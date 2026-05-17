package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: ChatMessageRole,
    val content: String,
    val createdAt: String,
)

@Serializable
enum class ChatMessageRole {
    User,
    Assistant,
    System,
}
