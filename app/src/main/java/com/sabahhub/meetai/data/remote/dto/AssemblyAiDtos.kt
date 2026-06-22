package com.sabahhub.meetai.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(
    @SerialName("upload_url") val uploadUrl: String,
)

@Serializable
data class TranscriptRequest(
    @SerialName("audio_url") val audioUrl: String,
    @SerialName("speaker_labels") val speakerLabels: Boolean = true,
    @SerialName("language_detection") val languageDetection: Boolean = true,
)

@Serializable
data class TranscriptResponse(
    val id: String,
    val status: String, // queued | processing | completed | error
    val text: String? = null,
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("audio_duration") val audioDuration: Double? = null,
    val utterances: List<UtteranceDto>? = null,
    val error: String? = null,
)

@Serializable
data class UtteranceDto(
    val speaker: String,
    val text: String,
    val start: Long = 0,
    val end: Long = 0,
)
