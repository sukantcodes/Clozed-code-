package com.clozed.code.engine

import java.io.File

/** Finds files by glob pattern under the project root. */
object GlobTool : Tool {
    override val spec = ToolSpec(
        name = "glob",
        description = "Search file paths using a glob pattern (examples: \"**/*.ts\", \"src/**/*.tsx\").",
        params = listOf(
            ToolParam("pattern", "Glob pattern to match files against", true),
            ToolParam("path", "Directory to search. Defaults to the current working directory.", false),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val pattern = input["pattern"] ?: throw ToolError("pattern is required")
        val base = Workspace.resolve(input["path"] ?: Workspace.root?.path)
        if (!base.exists()) throw ToolError("Search path not found: ${base.path}")
        val rules = IgnoreWalker.loadRules(Workspace.root ?: base)
        val regex = globToRegex(pattern)
        val limit = 100
        val hits = ArrayList<String>()
        val walker = IgnoreWalker(base, rules)
        for (f in walker.walk(limit * 20)) {
            val rel = f.relativeTo(base).invariantSeparatorsPath
            if (regex.matches(rel)) {
                hits.add(rel)
                if (hits.size >= limit) break
            } else if (regex.matches(f.name)) {
                hits.add(rel)
                if (hits.size >= limit) break
            }
        }
        return if (hits.isEmpty()) "No files matched." else hits.joinToString("\n")
    }

    fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> if (i + 1 < glob.length && glob[i + 1] == '*') {
                    sb.append(".*"); i++
                } else sb.append("[^/]*")
                '?' -> sb.append("[^/]")
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '.', '+', '|', '^', '$', '@', '%' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        sb.append('$')
        return Regex(sb.toString())
    }
}

/** Searches file contents with a regular expression or literal. */
object GrepTool : Tool {
    override val spec = ToolSpec(
        name = "grep",
        description = "Search file contents using a regular expression or literal text. " +
            "Narrow with path or include. Returns matching paths, line numbers and previews.",
        params = listOf(
            ToolParam("pattern", "Regular expression or literal text to match", true),
            ToolParam("path", "File or directory to search. Defaults to the current working directory.", false),
            ToolParam("include", "Glob pattern to filter files (e.g. \"*.kt\" or \"*.{ts,tsx}\")", false),
            ToolParam("literal", "Treat pattern as exact text instead of a regular expression", false, "boolean"),
            ToolParam("caseSensitive", "Case-sensitive matching", false, "boolean"),
            ToolParam("limit", "Maximum number of matching lines to return", false, "integer"),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val pattern = input["pattern"] ?: throw ToolError("pattern is required")
        val literal = input["literal"]?.toBooleanStrictOrNull() ?: false
        val caseSensitive = input["caseSensitive"]?.toBooleanStrictOrNull() ?: true
        val limit = (input["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 1000)
        val regex = try {
            if (literal) Regex(Regex.escape(pattern), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            else Regex(pattern, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
        } catch (e: Exception) {
            throw ToolError("Invalid regular expression: ${e.message}")
        }
        val base = Workspace.resolve(input["path"] ?: Workspace.root?.path)
        if (!base.exists()) throw ToolError("Search path not found: ${base.path}")
        val includeGlob = input["include"]?.let { GlobTool.globToRegex(it) }
        val files = if (base.isFile) listOf(base)
        else IgnoreWalker(base, IgnoreWalker.loadRules(Workspace.root ?: base)).walk(4000)

        val out = ArrayList<String>()
        for (f in files) {
            if (out.size >= limit) break
            if (includeGlob != null && !includeGlob.matches(f.name) && !includeGlob.matches(f.path)) continue
            if (f.length() > 2_000_000) continue
            val rel = base.let { if (it.isDirectory) f.relativeTo(it).invariantSeparatorsPath else f.name }
            val lines = runCatching { f.readLines() }.getOrNull() ?: continue
            for ((idx, line) in lines.withIndex()) {
                if (regex.containsMatchIn(line)) {
                    out.add("$rel:${idx + 1}: ${line.trim().take(200)}")
                    if (out.size >= limit) break
                }
            }
        }
        return if (out.isEmpty()) "No matches found." else out.joinToString("\n")
    }
}

/** Deletes a file or empty directory (guarded). */
object DeleteTool : Tool {
    override val spec = ToolSpec(
        name = "delete",
        description = "Delete a file inside the project. Directories are removed recursively.",
        params = listOf(ToolParam("path", "Path to delete", true)),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val f = ctx.writeTarget(input["path"])
        if (!f.exists()) throw ToolError("File not found: ${f.path}")
        if (ctx.request("delete", "Delete", f.path) == PermissionDecision.Deny) {
            throw DeniedException("The user rejected deleting ${f.path}")
        }
        val before = if (f.isFile) f.readText() else null
        val ok = f.deleteRecursively()
        if (ok) record(ctx, f, before, null)
        return if (ok) "Deleted ${f.path}" else "Could not delete ${f.path}"
    }
}
