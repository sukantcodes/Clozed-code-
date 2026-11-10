package com.clozed.code.engine

import android.content.Context
import com.clozed.code.data.SessionInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One persisted message in a session transcript. */
@Serializable
data class SessMsg(
    val role: String,                       // user | assistant | tool
    val text: String = "",
    val toolName: String? = null,
    val toolId: String? = null,
    val ts: Long = System.currentTimeMillis(),
)

@Serializable
data class Sess(
    val id: String,
    val title: String = "",
    val directory: String? = null,
    val agent: String = "build",
    val model: String = "",
    val provider: String = "",
    val updated: Long = System.currentTimeMillis(),
    val messages: List<SessMsg> = emptyList(),
) {
    fun toTurns(): List<LlmTurn> = messages.mapNotNull { m ->
        when (m.role) {
            "user" -> LlmTurn.User(m.text)
            "tool" -> if (m.toolId != null) LlmTurn.ToolResult(m.toolId, m.toolName ?: "tool", m.text) else null
            else -> LlmTurn.Assistant(listOf(MPart.Text(m.text)))
        }
    }

    fun withTitleIfEmpty(first: String): Sess =
        if (title.isNotBlank()) this
        else copy(title = first.lineSequence().firstOrNull()?.take(48)?.ifBlank { "Session" } ?: "Session")
}

/** Stores session transcripts as JSON files under files/sessions. */
class SessionStore(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "sessions").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun list(): List<SessionInfo> = dir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { f -> runCatching { json.decodeFromString<Sess>(f.readText()) }.getOrNull() }
        ?.sortedByDescending { it.updated }
        ?.map { SessionInfo(it.id, it.title, it.updated, it.directory, it.agent, it.model) }
        ?: emptyList()

    fun load(id: String): Sess? =
        File(dir, "$id.json").takeIf { it.isFile }
            ?.let { runCatching { json.decodeFromString<Sess>(it.readText()) }.getOrNull() }

    fun save(s: Sess) {
        runCatching { File(dir, "${s.id}.json").writeText(json.encodeToString<Sess>(s.copy(updated = System.currentTimeMillis()))) }
    }

    fun delete(id: String) { File(dir, "$id.json").delete() }

    fun newId(): String = "s" + System.currentTimeMillis().toString(36)
}
