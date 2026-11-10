package com.clozed.code.engine

import android.content.res.AssetManager
import java.io.File

/**
 * Builds the system prompt for a turn: the base template from
 * `assets/prompts/system.txt` with tool guidance substituted, plus the
 * model-family addendum and live project context.
 */
object PromptBuilder {

    private val modelFiles = listOf(
        "claude" to "anthropic.txt",
        "gpt-5" to "gpt-astra.txt",
        "gpt-4" to "gpt.txt",
        "o1" to "gpt.txt",
        "o3" to "gpt.txt",
        "kimi" to "kimi.txt",
        "k2" to "kimi.txt",
        "trinity" to "trinity.txt",
        "meta" to "meta.txt",
        "llama" to "meta.txt",
    )

    fun system(
        assets: AssetManager,
        model: String,
        agent: String,
        projectDir: File?,
        projectSystem: String?,
    ): String {
        val base = read(assets, "prompts/system.txt")
            .replace("\${CLOZED_TOOL_GUIDANCE}", ToolGuidance.builtin)
        val addendum = modelFiles.firstOrNull { model.lowercase().contains(it.first) }
            ?.let { read(assets, "prompts/${it.second}").replace("{{MODEL_NAME}}", model) }
            .orEmpty()
        val mode = if (agent == "plan") TEXT_PLAN else TEXT_BUILD
        val context = context(projectDir, projectSystem)
        return buildString {
            appendLine(base)
            if (addendum.isNotBlank()) { appendLine(); appendLine(addendum) }
            appendLine(); appendLine(mode)
            if (context.isNotBlank()) { appendLine(); appendLine(context) }
        }.trim()
    }

    private fun context(projectDir: File?, projectSystem: String?): String = buildString {
        if (projectDir != null) {
            appendLine("# Project")
            appendLine("Working directory: ${projectDir.path}")
            val agents = File(projectDir, "AGENTS.md").takeIf { it.isFile }
                ?: File(projectDir, "CLAUDE.md").takeIf { it.isFile }
            if (agents != null) {
                appendLine()
                appendLine("Project instructions from ${agents.name}:")
                appendLine(agents.readText().take(8000))
            }
        }
        if (!projectSystem.isNullOrBlank()) {
            appendLine()
            appendLine("# Project configuration")
            appendLine(projectSystem.take(8000))
        }
    }

    private fun read(assets: AssetManager, path: String): String =
        runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")

    private const val TEXT_BUILD = """
# Working mode: build
You may read, create, modify and delete files and run commands inside the
project. Keep changes small and in scope. Prefer the dedicated tools over
shell commands. After a larger edit, run the diagnostics tool to confirm the
syntax is intact. Finish with evidence: say what you verified and what you
could not verify.
"""

    private const val TEXT_PLAN = """
# Working mode: plan (read-only)
Reason about the problem and inspect the project, but do NOT modify anything.
File writes, edits, patches and mutating shell commands are refused. Produce a
concrete, ordered plan with the exact files and changes you would make.
"""
}
