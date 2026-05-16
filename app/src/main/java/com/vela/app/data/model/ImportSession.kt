package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportSession(
    val id: String,
    val createdAt: String,
    val updatedAt: String,
    val status: ImportSessionStatus,
    val messages: List<ChatMessage> = emptyList(),
    val candidates: List<EventCandidate> = emptyList(),
)

@Serializable
enum class ImportSessionStatus {
    Draft,
    ReadyForReview,
    Imported,
}
