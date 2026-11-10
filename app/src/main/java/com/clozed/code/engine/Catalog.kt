package com.clozed.code.engine

import com.clozed.code.data.ProviderDef
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads the bundled provider catalog (assets/providers.json) and exposes the
 * subset the app supports. Entries are `ProviderDef` with a kind of
 * "anthropic" or "openai".
 */
object Catalog {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<ProviderDef> =
        runCatching { json.decodeFromString<List<ProviderDef>>(text) }.getOrDefault(emptyList())

    fun load(assetsText: String?): List<ProviderDef> =
        assetsText?.let(::parse) ?: emptyList()

    /** Providers offered on the connect screen. */
    val featured = listOf(
        "anthropic", "openai", "openrouter", "google", "groq", "deepseek", "xai",
        "mistral", "togetherai", "cerebras", "fireworks-ai", "nvidia",
        "huggingface", "lmstudio", "ollama", "opencode",
    )

    fun find(providers: List<ProviderDef>, id: String): ProviderDef? =
        providers.firstOrNull { it.id == id }

    fun readFrom(file: File): List<ProviderDef> =
        runCatching { parse(file.readText()) }.getOrDefault(emptyList())
}

/** A flattened model row from [ProviderDef.models]. */
data class CatModel(
    val id: String,
    val name: String,
    val context: Long,
    val reasoning: Boolean,
    val inputCost: Double,
    val outputCost: Double,
    val cacheRead: Double,
    val cacheWrite: Double,
    val maxOutput: Long,
)

fun ProviderDef.toModels(): List<CatModel> = models.mapNotNull { row ->
    if (row.size < 9) return@mapNotNull null
    val c = row.map { it.toString().trim('"') }
    CatModel(
        id = c[0],
        name = c[1],
        context = c[2].toLongOrNull() ?: 0,
        reasoning = c[3] == "1",
        inputCost = c[4].toDoubleOrNull() ?: 0.0,
        outputCost = c[5].toDoubleOrNull() ?: 0.0,
        cacheRead = c[6].toDoubleOrNull() ?: 0.0,
        cacheWrite = c[7].toDoubleOrNull() ?: 0.0,
        maxOutput = c[8].toLongOrNull() ?: 0,
    )
}

/** The catalog provider shape used by the UI (id/name/kind/api/env). */
@Suppress("unused")
val ProviderDef.displayName: String get() = name
