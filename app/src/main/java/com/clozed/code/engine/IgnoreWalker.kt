package com.clozed.code.engine

import java.io.File

/** A single ignore rule, matched against a path relative to the project root. */
data class IgnoreRule(val pattern: String, val negate: Boolean = false, val dirOnly: Boolean = false) {
    private val regex: Regex = compile(pattern)

    fun matches(relative: String, isDir: Boolean): Boolean {
        if (dirOnly && !isDir) return false
        return regex.matches(relative)
    }

    private fun compile(p: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < p.length) {
            val c = p[i]
            when (c) {
                '*' -> if (i + 1 < p.length && p[i + 1] == '*') { sb.append(".*"); i++ } else sb.append("[^/]*")
                '?' -> sb.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '@', '%' -> sb.append('\\').append(c)
                '/' -> sb.append('/')
                else -> sb.append(c)
            }
            i++
        }
        sb.append("(/.*)?$")
        return Regex(sb.toString())
    }
}

/**
 * Walks a project tree while honouring ignore rules loaded from `.gitignore`,
 * `.clozedignore` and the excluded folder list. Read-only.
 */
class IgnoreWalker(private val root: File, private val rules: List<IgnoreRule>) {

    /** Returns up to [limit] files below [root] that are not ignored. */
    fun walk(limit: Int = 2000): List<File> {
        val out = ArrayList<File>()
        visit(root, out, limit)
        return out
    }

    private fun visit(dir: File, out: MutableList<File>, limit: Int) {
        if (out.size >= limit) return
        val entries = dir.listFiles() ?: return
        for (entry in entries.sortedBy { it.name }) {
            if (out.size >= limit) return
            val rel = entry.relativeTo(root).invariantSeparatorsPath
            if (isIgnored(rel, entry.isDirectory)) continue
            if (entry.isDirectory) visit(entry, out, limit) else out.add(entry)
        }
    }

    fun isIgnored(relative: String, isDir: Boolean): Boolean {
        var ignored = false
        for (rule in rules) {
            if (rule.matches(relative, isDir)) ignored = !rule.negate
        }
        return ignored
    }

    companion object {
        val DEFAULT_EXCLUDES = listOf(".git", ".gradle", ".idea", "build", "node_modules", ".cxx", ".svn")

        fun loadRules(root: File): List<IgnoreRule> {
            val rules = ArrayList<IgnoreRule>()
            for (name in listOf(".gitignore", ".clozedignore")) {
                val f = File(root, name)
                if (!f.isFile) continue
                f.readLines().forEach { line ->
                    val raw = line.trim()
                    if (raw.isEmpty() || raw.startsWith("#")) return@forEach
                    val negate = raw.startsWith("!")
                    val body = if (negate) raw.substring(1) else raw
                    rules.add(IgnoreRule(body.trim('/'), negate, body.endsWith("/")))
                }
            }
            for (name in DEFAULT_EXCLUDES) rules.add(IgnoreRule(name))
            return rules
        }
    }
}
