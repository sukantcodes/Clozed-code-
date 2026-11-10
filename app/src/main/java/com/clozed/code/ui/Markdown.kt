package com.clozed.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clozed.code.ui.theme.Ink
import com.clozed.code.ui.theme.Mono
import com.clozed.code.ui.theme.Surface2
import com.clozed.code.ui.theme.TextHi
import com.clozed.code.ui.theme.TextLo

/**
 * A small, forgiving markdown renderer: fenced code blocks, headings, list
 * items, blockquotes and inline `code`. Not a full CommonMark parser.
 */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val nodes = MarkdownParser.parse(text)
    Column(modifier.fillMaxWidth()) {
        for (n in nodes) NodeView(n)
    }
}

@Composable
private fun NodeView(node: MarkdownParser.Node) {
    when (node) {
        is MarkdownParser.Node.Code -> CodeBlock(node.lang, node.body)
        is MarkdownParser.Node.Heading -> Text(
            node.text,
            style = if (node.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        is MarkdownParser.Node.Bullet -> Text("• " + node.text, style = MaterialTheme.typography.bodyLarge)
        is MarkdownParser.Node.Quote -> Text(
            "▏ " + node.text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLo,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        is MarkdownParser.Node.Paragraph -> Text(node.text, style = MaterialTheme.typography.bodyLarge)
        is MarkdownParser.Node.Blank -> Text("")
    }
}

/** A fenced code block with a subtle frame; long lines scroll horizontally. */
@Composable
fun CodeBlock(lang: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2),
    ) {
        if (lang.isNotBlank()) {
            Text(
                lang,
                style = MaterialTheme.typography.labelSmall,
                color = TextLo,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(
            body,
            fontFamily = Mono,
            style = MaterialTheme.typography.bodySmall,
            color = TextHi,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

object MarkdownParser {
    sealed interface Node {
        data class Paragraph(val text: String) : Node
        data class Heading(val level: Int, val text: String) : Node
        data class Bullet(val text: String) : Node
        data class Quote(val text: String) : Node
        data class Code(val lang: String, val body: String) : Node
        data object Blank : Node
    }

    fun parse(text: String): List<Node> {
        val out = ArrayList<Node>()
        val lines = text.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    body.appendLine(lines[i]); i++
                }
                if (i < lines.size) i++ // closing fence
                out.add(Node.Code(lang, body.toString().trimEnd()))
                continue
            }
            val heading = trimmed.takeWhile { it == '#' }
            if (heading.isNotEmpty() && trimmed.length > heading.length && trimmed[heading.length] == ' ') {
                out.add(Node.Heading(heading.length, inline(trimmed.substring(heading.length).trim())))
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                out.add(Node.Bullet(inline(trimmed.substring(2))))
            } else if (trimmed.startsWith("> ")) {
                out.add(Node.Quote(inline(trimmed.substring(2))))
            } else if (trimmed.isBlank()) {
                out.add(Node.Blank)
            } else {
                out.add(Node.Paragraph(inline(trimmed)))
            }
            i++
        }
        return out
    }

    /** Strips inline markers so the plain-text widget stays readable. */
    private fun inline(s: String): String = s
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\*(.+?)\\*"), "$1")
}
