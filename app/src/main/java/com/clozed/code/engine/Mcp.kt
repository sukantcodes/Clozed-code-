package com.clozed.code.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

class McpException(message: String, val code: Int = 0) : Exception(message)
class McpUnauthorized(message: String) : Exception(message)
class McpSessionExpired(message: String) : Exception(message)

/** A configured MCP server. */
@Serializable
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val token: String? = null,
    val refreshToken: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val tokenEndpoint: String? = null,
    val scopes: String? = null,
)

/** Minimal MCP client over the Streamable HTTP transport (JSON-RPC 2.0). */
class McpClient(private val server: McpServer) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var nextId = 1

    suspend fun listTools(): List<McpToolDef> = withContext(Dispatchers.IO) {
        val result = call("tools/list", buildJsonObject { })
        val tools = result["tools"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return@withContext emptyList()
        tools.mapNotNull { t ->
            val obj = runCatching { t.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            McpToolDef(
                name = name,
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                schemaJson = obj["inputSchema"]?.toString(),
                server = server.id,
            )
        }
    }

    suspend fun callTool(name: String, args: Map<String, String>): String = withContext(Dispatchers.IO) {
        val params = buildJsonObject {
            put("name", name)
            putJsonObject("arguments") { args.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }
        }
        val result = call("tools/call", params)
        val content = result["content"]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?: return@withContext result.toString()
        content.joinToString("\n") { c ->
            runCatching { c.jsonObject["text"]?.jsonPrimitive?.content }.getOrNull() ?: c.toString()
        }
    }

    private suspend fun call(method: String, params: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val id = nextId++
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id); put("method", method); put("params", params)
        }
        val (code, text) = post(body.toString())
        when (code) {
            401 -> throw McpUnauthorized("HTTP 401: login required (tap Login under this server in Settings)")
            403 -> throw McpException("The server refused to register this app (403: approved clients only)", 403)
            404 -> throw McpException("HTTP 404: MCP endpoint not found at ${server.url}", 404)
        }
        if (code !in 200..299) throw McpException("HTTP $code: ${text.take(200)}", code)
        val payload = extractJson(text)
        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: throw McpException("Invalid JSON from MCP server")
        obj["error"]?.let { throw McpException("MCP tool error: ${it.toString()}") }
        (obj["result"] as? JsonObject) ?: JsonObject(emptyMap())
    }

    private fun post(body: String): Pair<Int, String> {
        val conn = (URL(server.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 120000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
            setRequestProperty("User-Agent", "ClozedCode-User/1.0; +https://clozedcode.app")
            server.token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
    }

    /** Handles both plain JSON and SSE (`data:` lines) responses. */
    private fun extractJson(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return trimmed
        val data = trimmed.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("data:") }
            ?.removePrefix("data:")?.trim()
        return data ?: trimmed
    }
}

/** A tool exposed by a connected MCP server. */
class McpTool(private val server: McpServer, private val def: McpToolDef) : Tool {
    override val spec = ToolSpec(def.name, def.description)
    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String =
        if (def.server != null) McpClient(server).callTool(def.name, input)
        else throw ToolError("MCP server for ${def.name} is not connected")
    val definition: McpToolDef get() = def
}

/** Persists MCP servers and keeps [McpRegistry] in sync. */
class McpManager(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("clozed-mcp", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun servers(): List<McpServer> =
        prefs.getString("servers", null)
            ?.let { runCatching { json.decodeFromString<List<McpServer>>(it) }.getOrNull() }
            ?: emptyList()

    fun save(list: List<McpServer>) {
        prefs.edit().putString("servers", json.encodeToString<List<McpServer>>(list)).apply()
    }

    fun add(server: McpServer) = save(servers().filterNot { it.id == server.id } + server)
    fun remove(id: String) = save(servers().filterNot { it.id == id })
    fun update(server: McpServer) = add(server)

    /** Connects to every enabled server and publishes their tools. */
    suspend fun refreshAll() {
        val connected = ArrayList<Tool>()
        for (s in servers().filter { it.enabled }) {
            try {
                val defs = McpClient(s).listTools()
                connected.addAll(defs.map { McpTool(s, it) })
            } catch (_: Exception) { /* server offline; skip */ }
        }
        McpRegistry.tools = connected
    }

    suspend fun tools(s: McpServer): List<McpToolDef> = McpClient(s).listTools()
}
