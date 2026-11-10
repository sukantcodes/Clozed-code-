package com.clozed.code.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * OAuth 2.0 (authorization-code + PKCE) for MCP servers, including the
 * `.well-known` discovery documents that advertise the endpoints.
 */
object McpOAuth {
    private val json = Json { ignoreUnknownKeys = true }

    data class Endpoints(
        val authorization: String,
        val token: String,
        val registration: String?,
        val scopes: String?,
    )

    /** Discovers endpoints from an MCP server URL. */
    fun discover(serverUrl: String): Endpoints? {
        val base = serverUrl.substringBefore("/mcp").trimEnd('/')
        for (path in listOf("/.well-known/oauth-authorization-server", "/.well-known/openid-configuration")) {
            fetch(base + path)?.let { return it }
        }
        // Protected-resource document points at the authorization server.
        fetch(base + "/.well-known/oauth-protected-resource")?.let { return it }
        return null
    }

    private fun fetch(url: String): Endpoints? = runCatching {
        val text = get(url)
        val obj = json.parseToJsonElement(text).jsonObject
        val auth = obj.str("authorization_endpoint") ?: return null
        val token = obj.str("token_endpoint") ?: return null
        Endpoints(auth, token, obj.str("registration_endpoint"), obj.str("scopes_supported"))
    }.getOrNull()

    /** RFC 7591 dynamic client registration (best effort). */
    fun register(endpoint: String, redirectUri: String): Pair<String, String?>? = runCatching {
        val body = """{"client_name":"Clozed Code","redirect_uris":["$redirectUri"],"grant_types":["authorization_code","refresh_token"],"response_types":["code"],"token_endpoint_auth_method":"none"}"""
        val (code, text) = postJson(endpoint, body, null)
        if (code !in 200..299) return null
        val obj = json.parseToJsonElement(text).jsonObject
        val id = obj.str("client_id") ?: return null
        id to obj.str("client_secret")
    }.getOrNull()

    fun verifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return base64Url(digest)
    }

    fun authorizeUrl(endpoints: Endpoints, clientId: String, redirectUri: String, challenge: String, state: String): String {
        val params = mapOf(
            "response_type" to "authorization_code",
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
        )
        return endpoints.authorization + (if ('?' in endpoints.authorization) "&" else "?") +
            params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
    }

    /** Exchanges an authorization code for an access token. */
    fun exchange(endpoints: Endpoints, clientId: String, clientSecret: String?, code: String, redirectUri: String, verifier: String): Pair<String, String?>? = runCatching {
        val form = buildString {
            append("grant_type=authorization_code")
            append("&code=").append(enc(code))
            append("&redirect_uri=").append(enc(redirectUri))
            append("&client_id=").append(enc(clientId))
            append("&code_verifier=").append(enc(verifier))
        }
        val body = """{"grant_type":"authorization_code","code":"$code","redirect_uri":"$redirectUri","client_id":"$clientId","code_verifier":"$verifier"}"""
        val (c, text) = postJson(endpoints.token, body, clientSecret)
        if (c !in 200..299) return null
        val obj = json.parseToJsonElement(text).jsonObject
        val token = obj.str("access_token") ?: return null
        token to obj.str("refresh_token")
    }.getOrNull()

    // ---- HTTP helpers ----------------------------------------------------

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("Accept", "application/json")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun postJson(url: String, body: String, secret: String?): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (secret != null) setRequestProperty("Authorization", "Basic " + base64Url("$secret".toByteArray()))
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()
}
