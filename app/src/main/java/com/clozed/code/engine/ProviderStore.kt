package com.clozed.code.engine

import android.content.Context
import com.clozed.code.data.ProviderDef
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredProvider(
    val id: String,
    val name: String,
    val kind: String = "openai",
    val api: String,
    val env: String? = null,
    val enabled: Boolean = false,
    /** Keys are tried in order; the first active key is used first. */
    val keys: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val flaggedUntil: Long = 0,
)

@Serializable
data class ProviderBackup(val version: Int = 1, val providers: List<StoredProvider>, val exportedAt: Long = 0)

/**
 * Persists providers, their API keys and the currently selected model.
 * Backed by SharedPreferences; keys are stored in plain text so a backup can
 * round-trip (the UI warns about this).
 */
class ProviderStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("clozed-providers", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(): List<StoredProvider> =
        prefs.getString("providers", null)
            ?.let { runCatching { json.decodeFromString<List<StoredProvider>>(it) }.getOrNull() }
            ?: emptyList()

    fun save(list: List<StoredProvider>) {
        prefs.edit().putString("providers", json.encodeToString(list)).apply()
    }

    fun selectedProvider(): String = prefs.getString("provider", "anthropic") ?: "anthropic"
    fun selectedModel(): String = prefs.getString("model", "claude-sonnet-4-5") ?: "claude-sonnet-4-5"
    fun selectedAgent(): String = prefs.getString("agent", "build") ?: "build"

    fun setSelection(provider: String, model: String) {
        prefs.edit().putString("provider", provider).putString("model", model).apply()
    }

    fun setAgent(agent: String) = prefs.edit().putString("agent", agent).apply()

    fun projectFolder(): String? = prefs.getString("project", null)
    fun setProjectFolder(path: String?) = prefs.edit().putString("project", path).apply()

    fun sourceProject(): String? = prefs.getString("source", null)
    fun setSourceProject(path: String?) = prefs.edit().putString("source", path).apply()

    fun autoApprove(): Boolean = prefs.getBoolean("autoApprove", false)
    fun setAutoApprove(v: Boolean) = prefs.edit().putBoolean("autoApprove", v).apply()

    fun reasoningEffort(): String? = prefs.getString("effort", null)
    fun setReasoningEffort(v: String?) = prefs.edit().putString("effort", v).apply()

    /** Merges the bundled catalog with anything the user already stored. */
    fun merged(catalog: List<ProviderDef>): List<StoredProvider> {
        val saved = load().associateBy { it.id }
        return catalog.map { def ->
            val s = saved[def.id]
            StoredProvider(
                id = def.id, name = def.name, kind = def.kind, api = def.api, env = def.env,
                enabled = s?.enabled ?: false, keys = s?.keys ?: emptyList(),
                models = s?.models ?: def.models.mapNotNull { it.firstOrNull()?.toString()?.trim('"') },
                flaggedUntil = s?.flaggedUntil ?: 0,
            )
        }
    }

    fun export(): String = json.encodeToString(
        ProviderBackup(providers = load(), exportedAt = System.currentTimeMillis())
    )

    fun import(text: String): Int {
        val backup = runCatching { json.decodeFromString<ProviderBackup>(text) }.getOrNull()
            ?: throw IllegalArgumentException("This is not a valid backup")
        save(backup.providers)
        return backup.providers.size
    }

    /** Marks a provider's active key as exhausted until tomorrow. */
    fun flagProvider(id: String) {
        val list = load().map {
            if (it.id == id) it.copy(flaggedUntil = System.currentTimeMillis() + 24 * 60 * 60 * 1000) else it
        }
        save(list)
    }

    fun isFlagged(p: StoredProvider): Boolean = p.flaggedUntil > System.currentTimeMillis()
}
