package com.clozed.code.engine

import android.content.Context
import kotlinx.serialization.Serializable

/** One allow/ask/deny rule. Patterns may contain `*` wildcards; the last match wins. */
@Serializable
data class PermRule(val pattern: String, val action: String = "ask") {
    fun matches(value: String): Boolean {
        if (pattern == "*") return true
        val regex = Regex("^" + Regex.escape(pattern).replace("\\*", ".*") + "$")
        return regex.matches(value) || regex.matches(value.substringAfterLast('/'))
    }
}

/** Persisted permission rules plus a per-session "allow next" set. */
class PermRules(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("clozed-perms", Context.MODE_PRIVATE)
    private val allowNext = HashSet<String>()

    fun rules(): List<PermRule> =
        prefs.getString("rules", null)
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.contains('=') }
            ?.map { line -> PermRule(line.substringBefore('=').trim(), line.substringAfter('=').trim()) }
            ?: emptyList()

    fun setRules(list: List<PermRule>) {
        prefs.edit().putString("rules", list.joinToString("\n") { "${it.pattern}=${it.action}" }).apply()
    }

    fun alwaysAutoApprove(): Boolean = prefs.getBoolean("autoApprove", false)
    fun setAlwaysAutoApprove(v: Boolean) = prefs.edit().putBoolean("autoApprove", v).apply()

    fun allowNext(request: PermReq) {
        allowNext.add(request.title)
    }

    fun consumeAllowNext(request: PermReq): Boolean = allowNext.remove(request.title)

    /** The decision for [request] given the configured rules (last match wins). */
    fun decide(request: PermReq): PermissionDecision {
        if (alwaysAutoApprove()) return PermissionDecision.AllowAlways
        if (consumeAllowNext(request)) return PermissionDecision.AllowNext
        val target = request.detail.ifBlank { request.title }
        var action = "ask"
        for (rule in rules()) if (rule.matches(target)) action = rule.action
        return when (action) {
            "allow" -> PermissionDecision.AllowAlways
            "deny" -> PermissionDecision.Deny
            else -> PermissionDecision.Ask
        }
    }
}
