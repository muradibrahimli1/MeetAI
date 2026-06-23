package com.sabahhub.meetai.data.remote.supabase

import com.sabahhub.meetai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Email/password authentication against Supabase GoTrue, over plain REST (no
 * SDK). Holds the current [session] and persists it via [SessionStore].
 */
class SupabaseAuth(
    private val http: OkHttpClient,
    private val json: Json,
    private val store: SessionStore,
    private val url: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {
    val available: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()

    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<SupabaseSession?> = _session.asStateFlow()

    val accessToken: String? get() = _session.value?.accessToken

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        requireConfigured()
        val raw = postJson(
            "$url/auth/v1/token?grant_type=password",
            json.encodeToString(Credentials.serializer(), Credentials(email.trim(), password)),
        )
        applyTokenResponse(raw, requireToken = true)
    }

    suspend fun signUp(email: String, password: String) = withContext(Dispatchers.IO) {
        requireConfigured()
        val raw = postJson(
            "$url/auth/v1/signup",
            json.encodeToString(Credentials.serializer(), Credentials(email.trim(), password)),
        )
        // With email confirmation disabled, signup returns a full session. If it
        // comes back without tokens, confirmation is still on.
        applyTokenResponse(
            raw,
            requireToken = true,
            missingTokenMessage = "Account created. If email confirmation is on, confirm via email, then sign in.",
        )
    }

    fun signOut() {
        store.clear()
        _session.value = null
    }

    /** Attempts to refresh the access token. Returns true on success. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val current = _session.value ?: return@withContext false
        val raw = runCatching {
            postJson(
                "$url/auth/v1/token?grant_type=refresh_token",
                json.encodeToString(RefreshBody.serializer(), RefreshBody(current.refreshToken)),
            )
        }.getOrNull() ?: return@withContext false
        runCatching { applyTokenResponse(raw, requireToken = true) }.isSuccess
    }

    // --- internals ----------------------------------------------------------

    private fun applyTokenResponse(
        raw: String,
        requireToken: Boolean,
        missingTokenMessage: String = "Authentication failed.",
    ) {
        val resp = json.decodeFromString(TokenResponse.serializer(), raw)
        val access = resp.accessToken
        val refresh = resp.refreshToken
        val user = resp.user
        if (access == null || refresh == null || user == null) {
            if (requireToken) throw IOException(missingTokenMessage)
            return
        }
        val session = SupabaseSession(access, refresh, user.id, user.email)
        store.save(session)
        _session.value = session
    }

    private fun postJson(endpoint: String, body: String): String {
        val req = Request.Builder()
            .url(endpoint)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(extractError(raw, resp.code))
            return raw
        }
    }

    /** Pulls a human-readable message out of a GoTrue error body. */
    private fun extractError(raw: String, code: Int): String {
        val msg = runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            (obj["error_description"] ?: obj["msg"] ?: obj["message"] ?: obj["error"])
                ?.jsonPrimitive?.content
        }.getOrNull()
        return msg ?: "Request failed ($code)."
    }

    private fun requireConfigured() {
        require(available) { "Supabase isn't configured — set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties." }
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
