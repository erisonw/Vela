package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EventAdvice(
    val eventId: String,
    val adviceText: String? = null,
    val generatedAt: String? = null,
    val sourceHash: String,
    val status: EventAdviceStatus = EventAdviceStatus.Pending,
)

@Serializable
enum class EventAdviceStatus {
    Pending,
    Ready,
    Failed,
}
