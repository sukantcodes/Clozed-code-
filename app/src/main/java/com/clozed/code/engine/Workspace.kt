package com.clozed.code.engine

import java.io.File

/** The active project + workspace directories the tools operate on. */
object Workspace {
    const val PROJECTS_DIR = "/storage/emulated/0/ClozedProjects"

    @Volatile
    var root: File? = null
        private set

    val privateDir: File = File("/data/data/com.clozed.code/files/workspace")

    fun setRoot(dir: File?) {
        root = dir
        dir?.mkdirs()
    }

    fun defaultProjects(): File {
        val f = File(PROJECTS_DIR)
        if (!f.exists()) f.mkdirs()
        return f
    }

    /** Resolves a possibly-relative path against the active project root. */
    fun resolve(path: String?): File {
        if (path.isNullOrBlank()) return root ?: defaultProjects()
        val f = File(path)
        return if (f.isAbsolute) f else File(root ?: defaultProjects(), path)
    }

    /** True when [file] sits inside the active project (guards tool writes). */
    fun within(file: File): Boolean {
        val base = root ?: return true
        return file.canonicalPath.startsWith(base.canonicalPath)
    }
}

/** Best-effort language tag from a file extension, used for markdown code fences. */
fun langOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "py" -> "python"
    "js", "mjs", "cjs" -> "javascript"
    "ts", "tsx" -> "typescript"
    "json" -> "json"
    "xml" -> "xml"
    "html", "htm" -> "html"
    "css" -> "css"
    "md" -> "markdown"
    "sh", "bash" -> "bash"
    "c", "h" -> "c"
    "cpp", "cc", "hpp" -> "cpp"
    "go" -> "go"
    "rs" -> "rust"
    "rb" -> "ruby"
    "swift" -> "swift"
    "yml", "yaml" -> "yaml"
    "toml" -> "toml"
    "sql" -> "sql"
    else -> ""
}
