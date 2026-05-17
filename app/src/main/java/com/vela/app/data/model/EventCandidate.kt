package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EventCandidate(
    val id: String,
    val title: String,
    val startAt: String,
    val endAt: String? = null,
    val timezone: String? = null,
    val location: Location? = null,
    val description: String? = null,
    val sourceMessageIds: List<String> = emptyList(),
    val sourceEvidence: String? = null,
    val reminders: List<Reminder> = emptyList(),
    val confidence: Float? = null,
    val isSelectedForImport: Boolean = false,
    val reviewStatus: EventCandidateReviewStatus = EventCandidateReviewStatus.Pending,
    val missingFields: List<String> = emptyList(),
)

@Serializable
enum class EventCandidateReviewStatus {
    Pending,
    Edited,
    Rejected,
    Imported,
}
