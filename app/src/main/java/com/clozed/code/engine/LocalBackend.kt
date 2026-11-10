package com.clozed.code.engine

import kotlinx.coroutines.delay

/** Pending permission the user must answer before a tool runs. */
data class PendingPerm(
    val request: PermReq,
    val decide: (PermissionDecision) -> Unit,
)

/** Callbacks the agent loop uses to talk to the UI/service layer. */
interface BackendHost {
    val systemPrompt: String
    val request: LlmRequest
    fun onText(text: String)
    fun onReasoning(text: String)
    /** A tool call has started. */
    fun onToolStart(callId: String, name: String, input: String)
    /** A tool call finished. */
    fun onToolEnd(callId: String, output: String, ok: Boolean)
    fun onNotice(text: String, kind: String = "info")
    fun onState(state: String)
    fun sessionTurn(assistant: List<MPart>)
    fun sessionResult(callId: String, name: String, content: String)
    /** Blocks the loop until the user answers. Unused while the UI owns the loop. */
    suspend fun awaitPermission(request: PermReq): PermissionDecision
    suspend fun askQuestion(question: String, options: List<String>): String
    suspend fun spawnSubagent(agent: String, prompt: String): String
}

/** File-change undo record. */
class ChangeSet(val changes: List<FileChange>) {
    fun undo(): String {
        var n = 0
        for (c in changes.asReversed()) {
            val f = java.io.File(c.path)
            runCatching {
                if (c.before == null) f.delete() else f.writeText(c.before)
                n++
            }
        }
        return "Undid last turn ($n file${if (n == 1) "" else "s"} restored)."
    }
}

/**
 * The coding-agent loop: stream a model step, repair and dispatch its tool
 * calls, append the results, and repeat until the model answers without tools.
 */
class LocalBackend(
    private val llm: Llm,
    private val host: BackendHost,
    private val ctx: ToolCtx,
    private val maxSteps: Int = 40,
    /** Called when a request fails; return true to rotate the key and retry. */
    val onRequestError: (suspend (LlmException) -> Boolean)? = null,
) {
    val undoStack = ArrayDeque<ChangeSet>()
    var interrupted = false
        private set

    fun interrupt() { interrupted = true }

    suspend fun run(userText: String) {
        interrupted = false
        val turns = ArrayList(host.request.turns)
        if ((turns.lastOrNull() as? LlmTurn.User)?.text != userText) turns.add(LlmTurn.User(userText))
        ctx.changes.clear()
        val repeat = RepeatGuard()

        var step = 0
        while (step++ < maxSteps) {
            if (interrupted) { host.onNotice("Interrupted.", "warn"); break }
            host.onState("thinking")
            val acc = try {
                llm.stream(host.request.copy(turns = turns)) { host.onText(it) }
            } catch (e: LlmException) {
                host.onNotice(e.message ?: "Request failed", "error")
                if (e.retryable) { delay(1500); continue }
                if (onRequestError?.invoke(e) == true) continue
                return
            } catch (e: Exception) {
                host.onNotice("Request failed: ${e.message}", "error")
                return
            }

            val parts = acc.toParts()
            host.sessionTurn(parts)

            val calls = parts.filterIsInstance<MPart.ToolP>()
            if (calls.isEmpty()) {
                if (acc.text.isBlank()) host.onNotice("The model returned an empty response.", "warn")
                break
            }
            turns.add(LlmTurn.Assistant(parts))

            for (call in calls) {
                if (interrupted) break
                val output = dispatch(call, repeat)
                turns.add(LlmTurn.ToolResult(call.id, call.name, output))
                host.sessionResult(call.id, call.name, output)
            }
            if (ctx.changes.isNotEmpty()) {
                undoStack.addLast(ChangeSet(ctx.changes.toList()))
                ctx.changes.clear()
            }
            host.onState("working")
        }
        host.onState("")
        if (step > maxSteps) host.onNotice("Stopped after $maxSteps steps.", "warn")
    }

    private suspend fun dispatch(call: MPart.ToolP, repeat: RepeatGuard): String {
        host.onToolStart(call.id, call.name, call.input)
        val args = ToolRepair.parseArgs(call.input)
        val signature = call.name + ":" + args.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        val repeated = repeat.note(signature)
        if (repeated != null) {
            val msg = "CRITICAL - REPEATED TOOL CALLS DETECTED\n" +
                "You have issued the same ${call.name} call three times in a row with identical " +
                "arguments. Stop repeating it. Change approach, or finish and report your findings."
            host.onToolEnd(call.id, msg, false)
            return msg
        }

        val tool = Tools.byName(call.name) ?: McpRegistry.tool(call.name)
        val result = if (tool == null) {
            "Unknown tool '${call.name}'. Available tools: " + Tools.all.joinToString(", ") { it.spec.name }
        } else {
            try {
                tool.run(ctx, args)
            } catch (e: DeniedException) {
                "Denied: ${e.message}"
            } catch (e: ToolError) {
                "Error: ${e.message}"
            } catch (e: Exception) {
                "Error: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        val ok = !result.startsWith("Error:") && !result.startsWith("Denied:")
        host.onToolEnd(call.id, result, ok)
        return result
    }
}

/** Holds the process-wide backend so the Activity and Service share one loop. */
object EngineHolder {
    @Volatile var backend: LocalBackend? = null
    @Volatile var bus: EngineBus? = null
}

/** Tools currently exposed by connected MCP servers. Filled by the MCP layer. */
object McpRegistry {
    @Volatile var tools: List<Tool> = emptyList()
    fun tool(name: String): Tool? = tools.firstOrNull { it.spec.name == name }
}

/** A tiny event holder the service and the UI both observe. */
class EngineBus {
    @Volatile var state: String = ""     // "", "thinking", "working"
    @Volatile var sessionId: String = ""
    @Volatile var turnRunning: Boolean = false
}
