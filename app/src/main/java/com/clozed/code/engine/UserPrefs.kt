package com.clozed.code.engine

import android.content.Context

/**
 * Small, app-wide user preferences (working folder, agent, effort, toggles).
 * API keys and provider data live in [ProviderStore] instead.
 */
class UserPrefs(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("clozed-prefs", Context.MODE_PRIVATE)

    var projectFolder: String?
        get() = prefs.getString("project", null)
        set(v) = prefs.edit().putString("project", v).apply()

    var sourceProject: String?
        get() = prefs.getString("source", null)
        set(v) = prefs.edit().putString("source", v).apply()

    var agent: String
        get() = prefs.getString("agent", "build") ?: "build"
        set(v) = prefs.edit().putString("agent", v).apply()

    var reasoningEffort: String?
        get() = prefs.getString("effort", null)
        set(v) = prefs.edit().putString("effort", v).apply()

    var backgroundWork: Boolean
        get() = prefs.getBoolean("background", true)
        set(v) = prefs.edit().putBoolean("background", v).apply()

    var voiceAlerts: Boolean
        get() = prefs.getBoolean("voice", false)
        set(v) = prefs.edit().putBoolean("voice", v).apply()

    var agentAlerts: Boolean
        get() = prefs.getBoolean("alerts", true)
        set(v) = prefs.edit().putBoolean("alerts", v).apply()

    var autoApprove: Boolean
        get() = prefs.getBoolean("autoApprove", false)
        set(v) = prefs.edit().putBoolean("autoApprove", v).apply()

    /** Read-only "Plan" mode: the model may reason and read, but not modify files. */
    var planMode: Boolean
        get() = prefs.getString("agent", "build") == "plan"
        set(v) = prefs.edit().putString("agent", if (v) "plan" else "build").apply()

    fun lastSession(): String? = prefs.getString("lastSession", null)
    fun setLastSession(id: String?) = prefs.edit().putString("lastSession", id).apply()
}
