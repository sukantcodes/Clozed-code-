package com.clozed.code.engine

import com.clozed.code.data.ProviderDef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** One piece of an assistant turn. */
sealed interface MPart {
    data class Text(val text: String) : MPart
    data class Reasoning(val text: String) : MPart
    data class ToolP(val id: String, val name: String, val input: String) : MPart
}

/** One turn in the model transcript. */
sealed interface LlmTurn {
    data class User(val text: String) : LlmTurn
    data class Assistant(val parts: List<MPart>) : LlmTurn
    data class ToolResult(val callId: String, val name: String, val content: String) : LlmTurn

    /** A short line for logs and compact previews. */
    fun preview(): String = when (this) {
        is User -> text
        is Assistant -> parts.filterIsInstance<MPart.Text>().joinToString(" ") { it.text }
        is ToolResult -> "tool:$name"
    }
}

/** Everything needed for one streamed model request. */
data class LlmRequest(
    val provider: ProviderDef,
    val model: String,
    val apiKey: String,
    val system: String,
    val turns: List<LlmTurn>,
    val tools: List<McpToolDef> = emptyList(),
    val maxTokens: Int = 8192,
)

/** Accumulates streamed deltas for a single step. */
class StepAcc {
    val text = StringBuilder()
    val reasoning = StringBuilder()
    val calls = LinkedHashMap<String, CallAcc>()

    fun call(id: String, name: String): CallAcc = calls.getOrPut(id) { CallAcc(id, name) }

    val hasTools: Boolean get() = calls.isNotEmpty()

    fun toParts(): List<MPart> = buildList {
        if (reasoning.isNotBlank()) add(MPart.Reasoning(reasoning.toString()))
        if (text.isNotBlank()) add(MPart.Text(text.toString()))
        for (c in calls.values) {
            if (c.name.isBlank()) continue
            add(MPart.ToolP(c.id, c.name, c.input.toString()))
        }
    }

    fun stopReason(): String = if (hasTools) "tool_use" else "end_turn"
}

/** A tool call accumulated while streaming. */
class CallAcc(val id: String, var name: String, val input: StringBuilder = StringBuilder())

class LlmException(message: String, val code: Int = 0) : Exception(message) {
    val retryable: Boolean get() = code == 429 || code in 500..599 || code == 0
}

/** Streams chat completions from Anthropic or any OpenAI-compatible provider. */
class Llm {

    suspend fun stream(req: LlmRequest, onDelta: (String) -> Unit): StepAcc =
        if (req.provider.kind == "anthropic") anthropic(req, onDelta) else openai(req, onDelta)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---- Anthropic -------------------------------------------------------

    private suspend fun anthropic(req: LlmRequest, onDelta: (String) -> Unit): StepAcc {
        val body = buildJsonObject {
            put("model", req.model)
            put("max_tokens", req.maxTokens)
            put("stream", true)
            if (req.system.isNotBlank()) put("system", req.system)
            putJsonArray("messages") { anthropicMessages(req.turns, this) }
            if (req.tools.isNotEmpty()) putJsonArray("tools") { req.tools.forEach { add(anthropicTool(it)) } }
        }
        val acc = StepAcc()
        Sse.post(
            url = req.provider.api.trimEnd('/') + "/messages",
            bearer = null,
            headers = mapOf("x-api-key" to req.apiKey, "anthropic-version" to "2023-06-01"),
            body = body.toString(),
            onData = { data ->
                val obj = orNull { json.parseToJsonElement(data).jsonObject } ?: return@post
                when (obj.str("type")) {
                    "content_block_start" -> {
                        val block = obj["content_block"]?.let { runCatching { it.jsonObject }.getOrNull() }
                        if (block != null && block.str("type") == "tool_use") {
                            acc.call(block.str("id") ?: "", block.str("name") ?: "")
                        }
                    }
                    "content_block_delta" -> {
                        val delta = obj["delta"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return@post
                        when (delta.str("type")) {
                            "text_delta" -> delta.str("text")?.let { acc.text.append(it); onDelta(it) }
                            "thinking_delta" -> delta.str("thinking")?.let { acc.reasoning.append(it) }
                            "input_json_delta" ->
                                acc.calls.values.lastOrNull()?.input?.append(delta.str("partial_json") ?: "")
                        }
                    }
                }
            },
        )
        return acc
    }

    private fun anthropicMessages(turns: List<LlmTurn>, out: kotlinx.serialization.json.JsonArrayBuilder) {
        var i = 0
        while (i < turns.size) {
            when (val t = turns[i]) {
                is LlmTurn.User -> { out.add(buildJsonObject { put("role", "user"); put("content", t.text) }); i++ }
                is LlmTurn.Assistant -> {
                    out.add(buildJsonObject {
                        put("role", "assistant")
                        putJsonArray("content") {
                            for (p in t.parts) when (p) {
                                is MPart.Text -> add(buildJsonObject { put("type", "text"); put("text", p.text) })
                                is MPart.Reasoning -> {}
                                is MPart.ToolP -> add(buildJsonObject {
                                    put("type", "tool_use"); put("id", p.id); put("name", p.name)
                                    put("input", parseJson(p.input))
                                })
                            }
                        }
                    })
                    i++
                }
                is LlmTurn.ToolResult -> {
                    // Group consecutive tool results into one user turn (Anthropic requirement).
                    out.add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            while (i < turns.size && turns[i] is LlmTurn.ToolResult) {
                                val r = turns[i] as LlmTurn.ToolResult
                                add(buildJsonObject {
                                    put("type", "tool_result"); put("tool_use_id", r.callId)
                                    put("content", r.content)
                                })
                                i++
                            }
                        }
                    })
                }
            }
        }
    }

    // ---- OpenAI-compatible ----------------------------------------------

    private suspend fun openai(req: LlmRequest, onDelta: (String) -> Unit): StepAcc {
        val body = buildJsonObject {
            put("model", req.model)
            put("stream", true)
            put("max_tokens", req.maxTokens)
            putJsonArray("messages") {
                if (req.system.isNotBlank()) add(buildJsonObject { put("role", "system"); put("content", req.system) })
                for (t in req.turns) add(openaiMessage(t))
            }
            if (req.tools.isNotEmpty()) putJsonArray("tools") { req.tools.forEach { add(openaiTool(it)) } }
        }
        val acc = StepAcc()
        Sse.post(
            url = req.provider.api.trimEnd('/') + "/chat/completions",
            bearer = req.apiKey,
            headers = emptyMap(),
            body = body.toString(),
            onData = { data ->
                if (data == "[DONE]") return@post
                val obj = orNull { json.parseToJsonElement(data).jsonObject } ?: return@post
                val choice = obj["choices"]?.let { runCatching { it.jsonArray.firstOrNull() }.getOrNull() } ?: return@post
                val delta = choice.jsonObject["delta"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return@post
                delta.str("content")?.let { acc.text.append(it); onDelta(it) }
                delta.str("reasoning_content")?.let { acc.reasoning.append(it) }
                delta["tool_calls"]?.let { runCatching { it.jsonArray }.getOrNull() }?.forEach { tc ->
                    val t = runCatching { tc.jsonObject }.getOrNull() ?: return@forEach
                    val idx = t.str("index") ?: "0"
                    val fn = t["function"]?.let { runCatching { it.jsonObject }.getOrNull() }
                    val cur = acc.calls.getOrPut(idx) { CallAcc(t.str("id") ?: "call_$idx", "") }
                    fn?.str("name")?.let { if (it.isNotBlank()) cur.name = it }
                    fn?.str("arguments")?.let { cur.input.append(it) }
                }
            },
        )
        return acc
    }

    private fun openaiMessage(t: LlmTurn): JsonObject = when (t) {
        is LlmTurn.User -> buildJsonObject { put("role", "user"); put("content", t.text) }
        is LlmTurn.Assistant -> buildJsonObject {
            put("role", "assistant")
            val text = t.parts.filterIsInstance<MPart.Text>().joinToString("") { it.text }
            put("content", if (text.isBlank()) JsonNull else JsonPrimitive(text))
            val calls = t.parts.filterIsInstance<MPart.ToolP>()
            if (calls.isNotEmpty()) putJsonArray("tool_calls") {
                for (c in calls) add(buildJsonObject {
                    put("id", c.id); put("type", "function")
                    putJsonObject("function") { put("name", c.name); put("arguments", c.input.ifBlank { "{}" }) }
                })
            }
        }
        is LlmTurn.ToolResult -> buildJsonObject {
            put("role", "tool"); put("tool_call_id", t.callId); put("content", t.content)
        }
    }

    // ---- Schema helpers --------------------------------------------------

    private fun anthropicTool(t: McpToolDef): JsonObject = buildJsonObject {
        put("name", t.name)
        put("description", t.description)
        put("input_schema", parseJson(t.schemaJson ?: defaultSchema(t)))
    }

    private fun openaiTool(t: McpToolDef): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", t.name)
            put("description", t.description)
            put("parameters", parseJson(t.schemaJson ?: defaultSchema(t)))
        }
    }

    private fun defaultSchema(t: McpToolDef): String = buildString {
        append("""{"type":"object","properties":{""")
        append(t.parameters.entries.joinToString(",") { (k, v) -> """"$k":{"type":"$v"}""" })
        append("""}}""")
    }

    private fun parseJson(s: String): JsonElement =
        runCatching { json.parseToJsonElement(if (s.isBlank()) "{}" else s) }.getOrDefault(JsonObject(emptyMap()))

    private inline fun <T> orNull(block: () -> T): T? = runCatching(block).getOrNull()
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !== JsonNull }?.content

/** Minimal SSE client over HttpURLConnection (no external HTTP dependency). */
object Sse {
    fun post(
        url: String,
        bearer: String?,
        headers: Map<String, String>,
        body: String,
        onData: (String) -> Unit,
    ) {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 300000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("User-Agent", "ClozedCode-User/1.0; +https://clozedcode.app")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw LlmException(errorMessage(code, text), code)
        parse(text, onData)
    }

    private fun parse(text: String, onData: (String) -> Unit) {
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) continue
            onData(trimmed.removePrefix("data:").trim())
        }
    }

    private fun errorMessage(code: Int, body: String): String = when (code) {
        401 -> "HTTP 401: invalid or missing API key"
        402 -> "HTTP 402: payment required"
        403 -> "HTTP 403: the provider refused this request"
        404 -> "HTTP 404: model or endpoint not found"
        429 -> "HTTP 429: rate limited"
        else -> "HTTP $code: ${body.take(300)}"
    }
}
