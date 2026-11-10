package com.clozed.code.engine

import java.io.File

internal fun record(ctx: ToolCtx, file: File, before: String?, after: String?) {
    ctx.changes.add(FileChange(file.path, before, after))
}

/** Reads a file (numbered lines) or lists a directory. */
object ReadTool : Tool {
    override val spec = ToolSpec(
        name = "read",
        description = "Read the contents of a file or directory. Each text line is prefixed by its " +
            "1-based line number. Directory entries are returned one per line. Use offset and limit " +
            "to read large files in sections.",
        params = listOf(
            ToolParam("path", "File or directory to read", true),
            ToolParam("offset", "The line or directory entry to start reading from (1-based)", false, "integer"),
            ToolParam("limit", "Maximum number of lines or entries (defaults to and capped at 2000)", false, "integer"),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val f = ctx.readTarget(input["path"])
        val offset = (input["offset"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (input["limit"]?.toIntOrNull() ?: 2000).coerceIn(1, 2000)
        if (f.isDirectory) {
            val entries = (f.listFiles() ?: emptyArray()).sortedBy { it.name }
            if (entries.isEmpty()) return "(empty directory)"
            return entries.drop(offset - 1).take(limit)
                .joinToString("\n") { it.name + if (it.isDirectory) "/" else "" }
        }
        if (f.length() > 4_000_000) throw ToolError("File too large (${f.length()} bytes)")
        val lines = f.readLines()
        return lines.drop(offset - 1).take(limit)
            .mapIndexed { i, line -> "${offset + i}: $line" }
            .joinToString("\n")
    }
}

/** Creates or replaces a whole file. */
object WriteTool : Tool {
    override val spec = ToolSpec(
        name = "write",
        description = "Writes a file to the local filesystem, overwriting if one exists. " +
            "Missing parent directories are created automatically. Use edit for partial changes.",
        params = listOf(
            ToolParam("path", "File to write to", true),
            ToolParam("content", "Content to write to the file", true),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val f = ctx.writeTarget(input["path"])
        if (ctx.request("write", "Write file", f.path) == PermissionDecision.Deny) {
            throw DeniedException("The user rejected writing ${f.path}")
        }
        val before = if (f.isFile) f.readText() else null
        val content = input["content"] ?: ""
        f.writeText(content)
        record(ctx, f, before, content)
        return "Wrote ${content.length} bytes to ${f.path}"
    }
}

/** Exact-text find and replace inside a file. */
object EditTool : Tool {
    override val spec = ToolSpec(
        name = "edit",
        description = "Edit a file by finding and replacing exact text. oldString must match exactly " +
            "and identify a unique location unless replaceAll is true. The file's line endings are preserved.",
        params = listOf(
            ToolParam("path", "File to edit", true),
            ToolParam("oldString", "Exact text to find", true),
            ToolParam("newString", "Replacement text", true),
            ToolParam("replaceAll", "Replace every occurrence", false, "boolean"),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val f = ctx.writeTarget(input["path"])
        val old = input["oldString"] ?: throw ToolError("oldString must not be empty")
        if (old.isEmpty()) throw ToolError("oldString must not be empty. Use write to replace whole files.")
        val new = input["newString"] ?: ""
        val replaceAll = input["replaceAll"]?.toBooleanStrictOrNull() ?: false
        val before = f.readText()
        val count = Regex.fromLiteral(old).findAll(before).count()
        if (count == 0) throw ToolError("oldString was not found in ${f.path}")
        if (count > 1 && !replaceAll) {
            throw ToolError("$count matches for oldString. Add more context or set replaceAll to true.")
        }
        if (ctx.request("edit", "Edit file", f.path) == PermissionDecision.Deny) {
            throw DeniedException("The user rejected editing ${f.path}")
        }
        val after = if (replaceAll) before.replace(old, new) else before.replaceFirst(old, new)
        f.writeText(after)
        record(ctx, f, before, after)
        return "Updated ${f.path} ($count replacement${if (count == 1) "" else "s"})"
    }
}
