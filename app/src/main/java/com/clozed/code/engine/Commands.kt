package com.clozed.code.engine

/** A slash command parsed from the composer, or a custom project command. */
data class SlashCommand(val name: String, val arg: String)

/** Parsed representation of the built-in `/help` listing. */
object Commands {

    val builtins: List<Pair<String, String>> = listOf(
        "/help" to "Show this help",
        "/init" to "Create an AGENTS.md describing this project",
        "/model" to "Choose the model (e.g. /model gpt-5.1)",
        "/models" to "List models for the current provider",
        "/effort" to "Set reasoning effort: /effort low|medium|high",
        "/compact" to "Summarise the conversation and free context",
        "/undo" to "Undid last turn (restore files written by the agent)",
        "/new" to "Start a new session",
        "/sessions" to "List saved sessions",
        "/connect" to "Add a provider and API key",
        "/port" to "Import a session or project from another tool",
        "/folder" to "Choose the working folder",
        "/clear" to "Clear the transcript",
    )

    /** Returns the command when [input] is a bare `/name arg`, otherwise null. */
    fun parse(input: String): SlashCommand? {
        val t = input.trim()
        if (!t.startsWith("/")) return null
        val name = t.substringBefore(' ').lowercase()
        val arg = t.substringAfter(' ', "").trim()
        return SlashCommand(name, arg)
    }

    fun help(extra: List<CustomCommand> = emptyList()): String = buildString {
        appendLine("Commands")
        for ((n, d) in builtins) appendLine("  $n - $d")
        for (c in extra) appendLine("  /${c.name} - ${c.description.ifBlank { "custom command" }}")
    }.trim()
}

/** A project-defined command from `.clozed/command` or `.opencode/command`. */
data class CustomCommand(
    val name: String,
    val description: String = "",
    val prompt: String = "",
    val agent: String? = null,
) {
    /** Expands `$ARGUMENTS` (or appends the raw argument) into a model prompt. */
    fun expand(arg: String): String =
        if (prompt.contains("\$ARGUMENTS")) prompt.replace("\$ARGUMENTS", arg)
        else if (arg.isBlank()) prompt else "$prompt\n\n$arg"
}
