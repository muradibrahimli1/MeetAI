package com.sabahhub.meetai.data.remote.supabase

import com.sabahhub.meetai.BuildConfig
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.data.model.RecordingStatus
import com.sabahhub.meetai.data.model.Utterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Cloud sync of recordings via Supabase PostgREST. Rows are scoped per user by
 * row-level security (the `user_id` column defaults to `auth.uid()`), so we
 * don't filter client-side. Audio files are not uploaded — only metadata.
 *
 * Handles a single transparent token refresh on 401.
 */
class SupabaseRepository(
    private val http: OkHttpClient,
    private val json: Json,
    private val auth: SupabaseAuth,
    private val url: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {
    val available: Boolean get() = auth.available

    suspend fun list(): List<Recording> = withContext(Dispatchers.IO) {
        val raw = executeAuthed {
            Request.Builder()
                .url("$url/rest/v1/recordings?select=*&order=created_at.desc")
                .get()
                .applyHeaders(it)
                .build()
        }
        json.decodeFromString(ListSerializer(RecordingRow.serializer()), raw).map { it.toDomain() }
    }

    suspend fun upsert(recording: Recording) = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(RecordingRow.serializer(), recording.toRow())
        executeAuthed {
            Request.Builder()
                .url("$url/rest/v1/recordings")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload.toRequestBody(JSON_MEDIA))
                .applyHeaders(it)
                .build()
        }
        Unit
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        executeAuthed {
            Request.Builder()
                .url("$url/rest/v1/recordings?id=eq.$id")
                .header("Prefer", "return=minimal")
                .delete()
                .applyHeaders(it)
                .build()
        }
        Unit
    }

    // --- internals ----------------------------------------------------------

    private fun Request.Builder.applyHeaders(token: String): Request.Builder =
        header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")

    /** Runs an authed request, retrying once after a token refresh on HTTP 401. */
    private suspend fun executeAuthed(build: (token: String) -> Request): String {
        val token = auth.accessToken ?: throw IOException("Not signed in")
        http.newCall(build(token)).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.code != 401) {
                if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: $raw")
                return raw
            }
        }
        // 401 — try a refresh and retry once.
        if (!auth.refresh()) throw IOException("Session expired — please sign in again.")
        val newToken = auth.accessToken ?: throw IOException("Session expired — please sign in again.")
        http.newCall(build(newToken)).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Supabase ${resp.code}: $raw")
            return raw
        }
    }

    private fun RecordingRow.toDomain(): Recording = Recording(
        id = id,
        createdAt = createdAt,
        title = title,
        durationMs = durationMs,
        status = runCatching { RecordingStatus.valueOf(status) }.getOrDefault(RecordingStatus.DONE),
        language = language,
        transcript = transcript,
        utterances = utterances.map { Utterance(it.speaker, it.text, it.startMs, it.endMs) },
        summary = summary,
        errorMessage = errorMessage,
    )

    private fun Recording.toRow(): RecordingRow = RecordingRow(
        id = id,
        createdAt = createdAt,
        title = title,
        durationMs = durationMs,
        status = status.name,
        language = language,
        transcript = transcript,
        summary = summary,
        errorMessage = errorMessage,
        utterances = utterances.map { UtteranceRow(it.speaker, it.text, it.startMs, it.endMs) },
    )

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
