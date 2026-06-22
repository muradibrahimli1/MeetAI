package com.sabahhub.meetai.data.remote

import com.sabahhub.meetai.BuildConfig
import com.sabahhub.meetai.data.model.Utterance
import com.sabahhub.meetai.data.remote.dto.TranscriptRequest
import com.sabahhub.meetai.data.remote.dto.TranscriptResponse
import com.sabahhub.meetai.data.remote.dto.UploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/**
 * AssemblyAI transcription: handles speaker diarization (`speaker_labels`) and
 * automatic language detection (`language_detection`) in a single transcript job.
 *
 * Flow: upload audio bytes -> create transcript job -> poll until completed.
 */
class AssemblyAiClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val apiKey: String = BuildConfig.ASSEMBLYAI_API_KEY,
) {
    data class Result(
        val text: String,
        val language: String?,
        val durationMs: Long,
        val utterances: List<Utterance>,
    )

    /** Uploads the audio file and returns AssemblyAI's temporary audio URL. */
    private suspend fun upload(file: File): String = withContext(Dispatchers.IO) {
        val body = file.asRequestBody("application/octet-stream".toMediaType())
        val req = Request.Builder()
            .url("$BASE/upload")
            .header("authorization", apiKey)
            .post(body)
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Upload failed (${resp.code}): $raw")
            json.decodeFromString<UploadResponse>(raw).uploadUrl
        }
    }

    private suspend fun createJob(audioUrl: String): String = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            TranscriptRequest.serializer(),
            TranscriptRequest(audioUrl = audioUrl),
        )
        val req = Request.Builder()
            .url("$BASE/transcript")
            .header("authorization", apiKey)
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Create transcript failed (${resp.code}): $raw")
            json.decodeFromString<TranscriptResponse>(raw).id
        }
    }

    private suspend fun fetch(id: String): TranscriptResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/transcript/$id")
            .header("authorization", apiKey)
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Poll failed (${resp.code}): $raw")
            json.decodeFromString<TranscriptResponse>(raw)
        }
    }

    /**
     * Transcribes [file]. Suspends (polling) until AssemblyAI finishes.
     * [onUploaded] fires once the audio is uploaded so the UI can move to the
     * "transcribing" state.
     */
    suspend fun transcribe(file: File, onUploaded: () -> Unit = {}): Result {
        require(apiKey.isNotBlank()) { "ASSEMBLYAI_API_KEY is missing — set it in local.properties" }

        val audioUrl = upload(file)
        onUploaded()
        val id = createJob(audioUrl)

        while (true) {
            val r = fetch(id)
            when (r.status) {
                "completed" -> {
                    val utterances = r.utterances.orEmpty().map {
                        Utterance(
                            speaker = "Speaker ${it.speaker}",
                            text = it.text,
                            startMs = it.start,
                            endMs = it.end,
                        )
                    }
                    val transcript = if (utterances.isNotEmpty()) {
                        utterances.joinToString("\n\n") { "${it.speaker}: ${it.text}" }
                    } else {
                        r.text.orEmpty()
                    }
                    return Result(
                        text = transcript,
                        language = r.languageCode,
                        durationMs = ((r.audioDuration ?: 0.0) * 1000).toLong(),
                        utterances = utterances,
                    )
                }
                "error" -> throw IOException("Transcription error: ${r.error}")
                else -> delay(POLL_INTERVAL_MS) // queued | processing
            }
        }
    }

    companion object {
        private const val BASE = "https://api.assemblyai.com/v2"
        private const val POLL_INTERVAL_MS = 3_000L
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
