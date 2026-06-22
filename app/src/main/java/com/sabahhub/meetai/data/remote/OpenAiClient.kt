package com.sabahhub.meetai.data.remote

import com.sabahhub.meetai.BuildConfig
import com.sabahhub.meetai.data.remote.dto.ChatMessage
import com.sabahhub.meetai.data.remote.dto.ChatRequest
import com.sabahhub.meetai.data.remote.dto.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Generates a meeting summary from a (speaker-labeled) transcript using OpenAI
 * chat completions. The model is asked to reply in the transcript's own
 * language so multilingual recordings get a same-language summary.
 */
class OpenAiClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val apiKey: String = BuildConfig.OPENAI_API_KEY,
    private val model: String = "gpt-4o",
) {
    suspend fun summarize(transcript: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY is missing — set it in local.properties" }
        if (transcript.isBlank()) return@withContext ""

        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", transcript),
            ),
        )
        val payload = json.encodeToString(ChatRequest.serializer(), request)

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Summary failed (${resp.code}): $raw")
            json.decodeFromString<ChatResponse>(raw)
                .choices.firstOrNull()?.message?.content?.trim().orEmpty()
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val SYSTEM_PROMPT = """
You are a meeting-notes assistant. Given a transcript (which may include speaker
labels like "Speaker A:"), produce concise, well-structured notes.

Respond in the SAME language as the transcript. Use this Markdown structure:

## Summary
A 2-4 sentence overview.

## Key Points
- bullet points of the main topics discussed

## Decisions
- decisions made (omit the section if none)

## Action Items
- [ ] owner — task (omit the section if none)
"""
    }
}
