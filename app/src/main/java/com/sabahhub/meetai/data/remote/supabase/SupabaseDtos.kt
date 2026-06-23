package com.sabahhub.meetai.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Persisted auth session (stored locally so login survives app restarts). */
@Serializable
data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String? = null,
)

/** Response shape from Supabase GoTrue token/signup endpoints. */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: SupabaseUserDto? = null,
)

@Serializable
data class SupabaseUserDto(
    val id: String,
    val email: String? = null,
)

@Serializable
data class Credentials(val email: String, val password: String)

@Serializable
data class RefreshBody(@SerialName("refresh_token") val refreshToken: String)

/** A row in the Supabase `recordings` table. */
@Serializable
data class RecordingRow(
    val id: String,
    @SerialName("created_at") val createdAt: Long,
    val title: String,
    @SerialName("duration_ms") val durationMs: Long = 0,
    val status: String = "DONE",
    val language: String? = null,
    val transcript: String = "",
    val summary: String = "",
    @SerialName("error_message") val errorMessage: String? = null,
    val utterances: List<UtteranceRow> = emptyList(),
)

@Serializable
data class UtteranceRow(
    val speaker: String,
    val text: String,
    @SerialName("start_ms") val startMs: Long = 0,
    @SerialName("end_ms") val endMs: Long = 0,
)
