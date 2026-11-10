package com.clozed.code.engine

import java.io.File
import java.util.concurrent.TimeUnit

/** Runs a command through /system/bin/sh inside the project folder. */
object ShellTool : Tool {
    override val spec = ToolSpec(
        name = "shell",
        description = "Execute a shell command and return its output. Commands run on Android using " +
            "/system/bin/sh (toybox). Quote paths containing spaces. Output is truncated when large.",
        params = listOf(
            ToolParam("command", "Shell command string to execute", true),
            ToolParam("workdir", "Working directory. Defaults to the project folder.", false),
            ToolParam("timeout", "Timeout in milliseconds (0 disables). Defaults to 120000.", false, "integer"),
            ToolParam("background", "Run in the background and return immediately", false, "boolean"),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val command = input["command"] ?: throw ToolError("command is required")
        val workdir = input["workdir"]?.let { Workspace.resolve(it) } ?: (Workspace.root ?: Workspace.defaultProjects())
        val timeout = (input["timeout"]?.toIntOrNull() ?: 120000).let { if (it <= 0) Int.MAX_VALUE else it }
        if (ctx.request("shell", "Run command", command) == PermissionDecision.Deny) {
            throw DeniedException("The user rejected running: $command")
        }
        return runBlockingShell(command, workdir, timeout)
    }

    fun runBlockingShell(command: String, workdir: File, timeoutMs: Int): String {
        val proc = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(workdir)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = proc.inputStream.bufferedReader()
        val t = Thread {
            runCatching { reader.forEachLine { synchronized(output) { output.appendLine(it) } } }
        }
        t.isDaemon = true
        t.start()
        val finished = proc.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return "Timed out after ${timeoutMs}ms.\n" + output.toString().take(8000)
        }
        t.join(500)
        val text = output.toString().ifBlank { "(no output)" }
        return text.take(20000)
    }
}

/** Applies a structured patch (add/update/delete hunks) with exact matching. */
object PatchTool : Tool {
    override val spec = ToolSpec(
        name = "patch",
        description = "Apply coordinated edits to one or more files in a single operation. Each hunk " +
            "locates an exact old block and replaces it with the new block.",
        params = listOf(
            ToolParam("patchText", "The full patch text that describes all changes to be made", true),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val text = input["patchText"] ?: throw ToolError("patchText is required")
        val ops = PatchOp.parse(text)
        if (ops.isEmpty()) throw ToolError("Patch contains no operations")
        if (ctx.request("patch", "Apply patch", "${ops.size} operation(s)") == PermissionDecision.Deny) {
            throw DeniedException("The user rejected the patch")
        }
        val results = ArrayList<String>()
        for (op in ops) {
            val f = ctx.writeTarget(op.path)
            when (op) {
                is PatchOp.Add -> {
                    val before = if (f.isFile) f.readText() else null
                    f.writeText(op.lines.joinToString("\n") + "\n")
                    record(ctx, f, before, op.lines.joinToString("\n"))
                    results.add("A ${op.path}")
                }
                is PatchOp.Delete -> {
                    val before = if (f.isFile) f.readText() else null
                    f.delete()
                    record(ctx, f, before, null)
                    results.add("D ${op.path}")
                }
                is PatchOp.Update -> {
                    val before = f.readText()
                    var after = before
                    for (chunk in op.chunks) {
                        if (!after.contains(chunk.old)) {
                            throw ToolError("Patch chunk did not match in ${op.path}. Re-read the file and retry.")
                        }
                        after = after.replaceFirst(chunk.old, chunk.new)
                    }
                    f.writeText(after)
                    record(ctx, f, before, after)
                    results.add("M ${op.path}")
                }
            }
        }
        return results.joinToString("\n")
    }
}

/** A parsed patch operation. */
sealed interface PatchOp {
    val path: String
    data class Add(override val path: String, val lines: List<String>) : PatchOp
    data class Delete(override val path: String) : PatchOp
    data class Update(override val path: String, val chunks: List<PatchChunk>) : PatchOp

    companion object {
        fun parse(text: String): List<PatchOp> {
            val ops = ArrayList<PatchOp>()
            var currentFile: String? = null
            var mode = ""
            val addLines = ArrayList<String>()
            val chunks = ArrayList<PatchChunk>()
            var oldBuf = ArrayList<String>()
            var newBuf = ArrayList<String>()

            fun flushChunk() {
                if (oldBuf.isNotEmpty() || newBuf.isNotEmpty()) {
                    chunks.add(PatchChunk(oldBuf.joinToString("\n"), newBuf.joinToString("\n")))
                }
                oldBuf = ArrayList(); newBuf = ArrayList()
            }
            fun flushFile() {
                val path = currentFile ?: return
                when (mode) {
                    "add" -> ops.add(Add(path, addLines.toList()))
                    "delete" -> ops.add(Delete(path))
                    "update" -> { flushChunk(); ops.add(Update(path, chunks.toList())) }
                }
                currentFile = null; mode = ""; addLines.clear(); chunks.clear(); flushChunk()
            }

            for (line in text.lines()) {
                when {
                    line.startsWith("*** Add File:") -> { flushFile(); currentFile = line.substringAfter(':').trim(); mode = "add" }
                    line.startsWith("*** Delete File:") -> { flushFile(); currentFile = line.substringAfter(':').trim(); mode = "delete" }
                    line.startsWith("*** Update File:") -> { flushFile(); currentFile = line.substringAfter(':').trim(); mode = "update" }
                    line.startsWith("@@") -> { flushChunk() }
                    mode == "add" && line.startsWith("+") -> addLines.add(line.substring(1))
                    mode == "update" && line.startsWith("+") -> newBuf.add(line.substring(1))
                    mode == "update" && line.startsWith("-") -> oldBuf.add(line.substring(1))
                    mode == "update" && line.startsWith(" ") -> { oldBuf.add(line.substring(1)); newBuf.add(line.substring(1)) }
                }
            }
            flushFile()
            return ops
        }
    }
}

data class PatchChunk(val old: String, val `new`: String)
