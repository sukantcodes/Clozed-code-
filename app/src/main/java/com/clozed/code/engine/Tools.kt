package com.clozed.code.engine

/** A JSON-schema tool declaration sent to the model. */
data class McpToolDef(
    val name: String,
    val description: String,
    val parameters: Map<String, String> = emptyMap(),
    /** Full JSON schema, when the tool comes from an MCP server. */
    val schemaJson: String? = null,
    /** Owning MCP server id, null for built-ins. */
    val server: String? = null,
)

/** The registry of built-in tools. */
object Tools {
    val all: List<Tool> = listOf(
        ReadTool, WriteTool, EditTool, PatchTool, DeleteTool,
        GlobTool, GrepTool, ShellTool,
        TodoTool, TaskTool, SkillTool, QuestionTool,
        DiagnosticsTool, WebFetchTool, WebSearchTool,
    )

    fun byName(name: String): Tool? = all.firstOrNull { it.spec.name == name }
}

/** Registry of the app's bundled skills loaded from assets/skills. */
object Skills {
    data class Skill(val id: String, val name: String, val description: String, val body: String)

    /** Parses YAML front-matter (name/description) plus the markdown body. */
    fun parse(id: String, content: String): Skill {
        val name: String
        val description: String
        var body = content
        if (content.startsWith("---")) {
            val end = content.indexOf("\n---", 3)
            if (end > 0) {
                val fm = content.substring(3, end)
                body = content.substring(end + 4).trim()
                name = fm.lineSequence().firstOrNull { it.trim().startsWith("name:") }
                    ?.substringAfter(':')?.trim() ?: id
                description = fm.lineSequence().firstOrNull { it.trim().startsWith("description:") }
                    ?.substringAfter(':')?.trim() ?: ""
                return Skill(id, name, description, body)
            }
        }
        return Skill(id, id, "", body)
    }
}

/** The tool-guidance text injected into the system prompt. */
object ToolGuidance {
    val builtin: String = buildString {
        appendLine("# Tools")
        appendLine("You call tools by name. Use the dedicated tools instead of shell when one exists.")
        for (t in Tools.all) {
            appendLine()
            appendLine("## ${t.spec.name}")
            appendLine(t.spec.description)
            if (t.spec.params.isNotEmpty()) {
                appendLine("Arguments:")
                for (p in t.spec.params) {
                    appendLine("- ${p.name} (${p.type}${if (p.required) ", required" else ""}): ${p.description}")
                }
            }
        }
        appendLine()
        appendLine("## mcp")
        appendLine("Tools exposed by connected MCP servers are called exactly like the built-ins above.")
    }.trim()
}
