package com.clozed.code.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Top-level navigation destinations. */
enum class Screen {
    Chat,
    Sessions,
    Settings,
    Advanced,
    Skills,
    Mcp,
    Rules,
    Permissions,
    ProviderBackup,
    FolderPicker,
}

/** One renderable chunk of an assistant turn. */
sealed interface Block {
    data class Text(val text: String) : Block
    data class Reasoning(val text: String) : Block
    data class Tool(
        val id: String = "",
        val name: String,
        val input: String = "",
        val output: String = "",
        val running: Boolean = false,
        val ok: Boolean = true,
    ) : Block {
        fun finish(out: String, isOk: Boolean = true) =
            copy(output = out, running = false, ok = isOk)
    }
}

/** An item in the chat transcript. */
sealed interface ChatItem {
    val id: String

    data class User(override val id: String, val text: String) : ChatItem
    data class Assistant(override val id: String, val blocks: List<Block>) : ChatItem
    data class Shell(override val id: String, val command: String, val output: String = "") : ChatItem
    data class Notice(override val id: String, val text: String, val kind: Kind = Kind.Info) : ChatItem {
        enum class Kind { Info, Warn, Error }
    }
}

@Serializable
data class SessionInfo(
    val id: String,
    val title: String = "",
    @SerialName("updated") val updatedAt: Long = 0,
    val directory: String? = null,
    val agent: String? = null,
    val model: String? = null,
)

/** A pending permission decision shown to the user. */
data class PermissionReq(
    val id: String,
    val title: String,
    val detail: String = "",
    val kind: String = "",
    val options: List<String> = listOf("Allow once", "Allow next", "Always", "Deny"),
    val alwaysLabel: String = "Always",
)

@Serializable
data class AgentOpt(
    val id: String,
    val name: String,
    val description: String = "",
    val model: String? = null,
)

@Serializable
data class ModelOpt(
    val id: String,
    val name: String,
    val provider: String = "",
    val contextWindow: Long = 0,
)

@Serializable
data class IntegrationOpt(
    val id: String,
    val name: String,
    val description: String = "",
    val connected: Boolean = false,
)

/** Catalog provider entry decoded from assets/providers.json. */
@Serializable
data class ProviderDef(
    val id: String,
    val name: String,
    val kind: String = "openai",
    val api: String,
    val env: String? = null,
    val models: List<List<kotlinx.serialization.json.JsonElement>> = emptyList(),
)

/** Persisted application preferences. */
data class Prefs(
    val projectFolder: String? = null,
    val provider: String = "anthropic",
    val model: String = "claude-sonnet-4-5",
    val agent: String = "build",
    val reasoningEffort: String? = null,
    val autoApprove: Boolean = false,
    val backgroundWork: Boolean = true,
    val voiceAlerts: Boolean = false,
    val sourceProject: String? = null,
)

@Serializable
data class ApiException(val code: Int, val message: String) : Exception(message)
