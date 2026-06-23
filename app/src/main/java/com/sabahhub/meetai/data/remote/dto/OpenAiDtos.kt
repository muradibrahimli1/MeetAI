package com.sabahhub.meetai.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ResponseFormat(val type: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

/** The JSON the model returns when asked for notes: a short title + Markdown body. */
@Serializable
data class SummaryJson(
    val title: String = "",
    val summary: String = "",
)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList()) {
    @Serializable
    data class Choice(val message: ChatMessage)
}
