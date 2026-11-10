package com.clozed.code.engine

import java.net.HttpURLConnection
import java.net.URL

/** Maintains the session todo list. */
object TodoTool : Tool {
    override val spec = ToolSpec(
        name = "todowrite",
        description = "Create and update a todo list for the current session. The user sees it as your working plan.",
        params = listOf(
            ToolParam("todos", "The updated todo list", true, "array"),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val raw = input["todos"] ?: ""
        ctx.todos.clear()
        for (entry in raw.split("\n", ";")) {
            val t = entry.trim()
            if (t.isEmpty()) continue
            val status = if (t.contains("[x]")) "completed" else if (t.contains("[>]")) "in_progress" else "pending"
            ctx.todos.add(Todo(t.replace(Regex("\\[( |x|>)]"), "").trim(), status))
        }
        return "Todo list updated: " + ctx.todos.joinToString("; ") { "${it.status}: ${it.content}" }
    }
}

/** Delegates a self-contained task to a subagent. */
object TaskTool : Tool {
    override val spec = ToolSpec(
        name = "task",
        description = "Launch a subagent to handle a self-contained task and return its final report. " +
            "Use agent \"explore\" for read-only searches or \"general\" for multi-step work.",
        params = listOf(
            ToolParam("description", "A short (3-5 words) description of the task", true),
            ToolParam("prompt", "The complete task for the subagent to perform", true),
            ToolParam("agent", "Subagent to use: explore or general", false),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val prompt = input["prompt"] ?: throw ToolError("prompt is required")
        val agent = input["agent"] ?: "general"
        return ctx.runSubagent(agent, prompt)
    }
}

/** Loads a skill's instructions into the conversation. */
object SkillTool : Tool {
    override val spec = ToolSpec(
        name = "skill",
        description = "Load a specialized skill's instructions and resources into the current conversation " +
            "when the task at hand matches its description.",
        params = listOf(ToolParam("id", "The ID of an available skill", true)),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val id = input["id"] ?: throw ToolError("id is required")
        return ctx.loadSkill(id) ?: throw ToolError("Unknown skill '$id'")
    }
}

/** Asks the user a multiple-choice question and returns their answer. */
object QuestionTool : Tool {
    override val spec = ToolSpec(
        name = "question",
        description = "Ask the user one or more questions when you need a decision or missing information. " +
            "Provide short options when possible; the user can also type their own answer.",
        params = listOf(
            ToolParam("question", "The question to ask", true),
            ToolParam("options", "Comma-separated options", false),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val q = input["question"] ?: throw ToolError("question is required")
        val options = input["options"]?.split("|", ";", ",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        return ctx.askQuestion(q, options)
    }
}

/** Lightweight syntax check (JSON/XML validity, bracket balance). */
object DiagnosticsTool : Tool {
    override val spec = ToolSpec(
        name = "diagnostics",
        description = "Run a quick syntax check on files (JSON/XML validity and unbalanced brackets or " +
            "quotes in common source languages). It is not a compiler.",
        params = listOf(ToolParam("paths", "Files to check", true, "array")),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val raw = input["paths"] ?: throw ToolError("paths is required")
        val paths = raw.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
        val issues = ArrayList<String>()
        for (p in paths) {
            val f = Workspace.resolve(p)
            if (!f.isFile) { issues.add("${f.path}: not a file"); continue }
            issues.addAll(Diagnostics.check(f.name, f.readText()))
        }
        return if (issues.isEmpty()) "No syntax problems found." else issues.joinToString("\n")
    }
}

/** Bracket/JSON/XML sanity checks shared by the diagnostics tool and ViewModel. */
object Diagnostics {
    data class Issue(val path: String, val message: String)

    fun check(name: String, text: String): List<String> {
        val out = ArrayList<String>()
        if (name.substringAfterLast('.', "").lowercase() == "json") {
            runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }
                .onFailure { out.add("$name: invalid JSON - ${it.message}") }
        }
        out.addAll(checkBrackets(name, text))
        return out
    }

    fun checkBrackets(name: String, text: String): List<String> {
        val stack = ArrayDeque<Pair<Char, Int>>()
        val open = mapOf('(' to ')', '[' to ']', '{' to '}')
        val close = open.values.toSet()
        var line = 1
        var inString = false
        var stringChar = ' '
        var inLineComment = false
        for (i in text.indices) {
            val c = text[i]
            if (c == '\n') { line++; inLineComment = false; continue }
            if (inLineComment) continue
            if (inString) {
                if (c == '\\') continue
                if (c == stringChar) inString = false
                continue
            }
            when (c) {
                '"', '\'' -> { inString = true; stringChar = c }
                '/' -> if (i + 1 < text.length && text[i + 1] == '/') inLineComment = true
                in open.keys -> stack.addLast(c to line)
                in close -> {
                    val top = stack.removeLastOrNull()
                        ?: return listOf("$name:$line: unmatched closing '$c'")
                    if (open[top.first] != c) return listOf("$name:$line: '$c' does not close '${top.first}' from line ${top.second}")
                }
            }
        }
        return stack.map { "$name:${it.second}: unbalanced '${it.first}'" }
    }
}

/** Fetches a URL and returns it as text or markdown. */
object WebFetchTool : Tool {
    override val spec = ToolSpec(
        name = "webfetch",
        description = "Fetch content from an HTTP or HTTPS URL and return it as text, markdown, or HTML. " +
            "Markdown is the default. This tool is read-only.",
        params = listOf(
            ToolParam("url", "The HTTP or HTTPS URL to fetch content from", true),
            ToolParam("format", "text, markdown, or html. Defaults to markdown.", false),
        ),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val url = input["url"] ?: throw ToolError("url is required")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw ToolError("URL must start with http:// or https://")
        }
        val format = input["format"] ?: "markdown"
        val body = httpGet(url)
        return when (format) {
            "html" -> body
            "text" -> body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            else -> toMarkdown(body)
        }.take(60000)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 30000
            setRequestProperty("User-Agent", "ClozedCode-User/1.0; +https://clozedcode.app")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun toMarkdown(html: String): String {
        var s = html
        s = s.replace(Regex("(?s)<(script|style)\\b.*?</\\1>"), "")
        s = s.replace(Regex("(?i)<h([1-6])[^>]*>(.*?)</h\\1>")) { "\n" + "#".repeat(it.groupValues[1].toInt()) + " " + it.groupValues[2].trim() + "\n" }
        s = s.replace(Regex("(?i)<li[^>]*>"), "\n- ")
        s = s.replace(Regex("(?i)<a\\s+[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>")) { "[${it.groupValues[2].trim()}](${it.groupValues[1]})" }
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</p>"), "\n\n")
        s = s.replace(Regex("<[^>]+>"), "")
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        return s.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

/** Web search (best-effort, uses DuckDuckGo's HTML endpoint). */
object WebSearchTool : Tool {
    override val spec = ToolSpec(
        name = "websearch",
        description = "Search the web. Use this for current information beyond your knowledge cutoff.",
        params = listOf(ToolParam("query", "Web search query", true)),
    )

    override suspend fun run(ctx: ToolCtx, input: Map<String, String>): String {
        val q = input["query"] ?: throw ToolError("query is required")
        return try {
            val body = WebFetchTool.run(
                ctx,
                mapOf("url" to "https://html.duckduckgo.com/html/?q=" + q.replace(" ", "+"), "format" to "markdown"),
            )
            if (body.isBlank()) "No results." else body.take(8000)
        } catch (e: Exception) {
            "Web search failed: ${e.message}"
        }
    }
}
