package com.clozed.code.engine

/**
 * Best-effort repair of the JSON a model emits for tool arguments. Models
 * frequently produce trailing commas, single quotes, Python literals or a
 * truncated object; we fix the common cases before giving up.
 */
object ToolRepair {

    fun parseArgs(raw: String): Map<String, String> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyMap()
        val candidates = buildList {
            add(text)
            add(stripFences(text))
            add(balance(stripFences(text)))
            add(sanitize(balance(stripFences(text))))
        }
        for (c in candidates) {
            val elapsed = runCatching { Json5.parseObject(c) }.getOrNull()
            if (elapsed != null) return elapsed
        }
        return emptyMap()
    }

    fun stripFences(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```")
            t = t.substringAfter('\n', t)
            t = t.removeSuffix("```")
        }
        return t.trim()
    }

    /** Closes any unbalanced braces/brackets and drops an unterminated string tail. */
    fun balance(s: String): String {
        var inString = false
        var escape = false
        val stack = ArrayDeque<Char>()
        for (c in s) {
            if (escape) { escape = false; continue }
            when {
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                inString -> {}
                c == '{' || c == '[' -> stack.addLast(c)
                c == '}' || c == ']' -> stack.removeLastOrNull()
            }
        }
        val sb = StringBuilder(s.trimEnd().trimEnd(','))
        if (inString) sb.append('"')
        while (stack.isNotEmpty()) {
            sb.append(if (stack.removeLast() == '{') '}' else ']')
        }
        return sb.toString()
    }

    /** Removes trailing commas and converts Python literals/single quotes. */
    fun sanitize(s: String): String = s
        .replace(Regex(",\\s*([}\\]])"), "$1")
        .replace(Regex("\\bTrue\\b"), "true")
        .replace(Regex("\\bFalse\\b"), "false")
        .replace(Regex("\\bNone\\b"), "")
        .replace(Regex("\\bNaN\\b"), "")
}

/**
 * A very small, dependency-free JSON reader that tolerates single-quoted
 * strings and unquoted keys, returning a flat string map. Nested values are
 * re-serialized as compact JSON so array/object arguments survive.
 */
internal object Json5 {
    fun parseObject(src: String): Map<String, String>? {
        val p = Parser(src)
        p.ws()
        if (!p.eat('{')) return null
        val out = LinkedHashMap<String, String>()
        p.ws()
        if (p.eat('}')) return out
        while (true) {
            p.ws()
            val key = p.string() ?: return null
            p.ws()
            if (!p.eat(':')) return null
            p.ws()
            val value = p.value() ?: return null
            out[key] = value
            p.ws()
            if (p.eat(',')) continue
            if (p.eat('}')) return out
            return null
        }
    }

    private class Parser(private val s: String) {
        private var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun eat(c: Char): Boolean { if (i < s.length && s[i] == c) { i++; return true }; return false }

        fun string(): String? {
            if (i >= s.length) return null
            val quote = s[i]
            if (quote != '"' && quote != '\'') return null
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    val n = s[i + 1]
                    sb.append(when (n) {
                        'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; 'b' -> '\b'; 'f' -> '\u000C'
                        else -> n
                    })
                    i += 2
                    continue
                }
                if (c == quote) { i++; return sb.toString() }
                sb.append(c); i++
            }
            return sb.toString()
        }

        fun value(): String? {
            if (i >= s.length) return null
            return when (val c = s[i]) {
                '"', '\'' -> string()
                '{' -> nested('{', '}')
                '[' -> nested('[', ']')
                else -> {
                    if (!(c.isLetterOrDigit() || c == '-' || c == '+' || c == '.')) return null
                    val start = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "-+.eE")) i++
                    s.substring(start, i)
                }
            }
        }

        private fun nested(open: Char, close: Char): String? {
            val start = i
            var depth = 0
            var inStr = false
            var quote = ' '
            while (i < s.length) {
                val c = s[i]
                if (inStr) {
                    if (c == '\\') { i += 2; continue }
                    if (c == quote) inStr = false
                } else when (c) {
                    '"', '\'' -> { inStr = true; quote = c }
                    open -> depth++
                    close -> { depth--; if (depth == 0) { i++; return s.substring(start, i) } }
                }
                i++
            }
            return s.substring(start)
        }
    }
}

/** Detects a repeating tool call and returns the offending signature, if any. */
class RepeatGuard(private val threshold: Int = 3) {
    private val recent = ArrayDeque<String>()
    fun note(signature: String): String? {
        recent.addLast(signature)
        if (recent.size > threshold) recent.removeFirst()
        return if (recent.size == threshold && recent.all { it == signature }) signature else null
    }
    fun clear() = recent.clear()
}
