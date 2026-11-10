package com.clozed.code.engine

import java.io.File

/** Raised when a tool cannot complete; surfaced to the model as the tool result. */
class ToolError(message: String) : Exception(message)

/** Raised when the user denies a permission request. */
class DeniedException(message: String = "Permission denied") : Exception(message)

/** One whole-file before/after snapshot, used for the undo stack. */
data class FileChange(val path: String, val before: String?, val after: String?)

/** JSON-schema-ish description of a tool argument. */
data class ToolSpec(
    val name: String,
    val description: String,
    val params: List<ToolParam> = emptyList(),
)

data class ToolParam(
    val name: String,
    val description: String,
    val required: Boolean = false,
    val type: String = "string",
)

/** Context passed to every tool. Owns the permission gate and side channels. */
class ToolCtx(
    val root: File?,
    val tools: () -> List<Tool> = { Tools.all },
    val permission: suspend (PermReq) -> PermissionDecision = { PermissionDecision.AllowOnce },
    val askQuestion: suspend (String, List<String>) -> String = { _, _ -> "" },
    val runSubagent: suspend (String, String) -> String = { _, _ -> "" },
    val loadSkill: (String) -> String? = { null },
) {
    val changes = ArrayList<FileChange>()
    val todos = ArrayList<Todo>()

    suspend fun request(kind: String, title: String, detail: String = ""): PermissionDecision =
        permission(PermReq(kind, title, detail))

    /** Resolves and validates a read target inside the project. */
    fun readTarget(path: String?): File {
        val f = Workspace.resolve(path)
        if (!f.exists()) throw ToolError("File not found: ${path ?: f.path}")
        return f
    }

    /** Resolves and validates a write target inside the project. */
    fun writeTarget(path: String?): File {
        if (path.isNullOrBlank()) throw ToolError("A path is required")
        val f = Workspace.resolve(path)
        if (!Workspace.within(f)) throw ToolError("Refusing to write outside the project: ${f.path}")
        if (Workspace.root == null) throw ToolError("No project folder is open")
        f.parentFile?.mkdirs()
        return f
    }
}

data class PermReq(val kind: String, val title: String, val detail: String)

enum class PermissionDecision { AllowOnce, AllowNext, AllowAlways, Ask, Deny }

/** A single todo entry shown in the session plan. */
data class Todo(val content: String, val status: String)

/** Every callable tool. */
interface Tool {
    val spec: ToolSpec
    suspend fun run(ctx: ToolCtx, input: Map<String, String>): String
}
