package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WidgetSnapshot(
    val generatedAt: String,
    val title: String,
    val todayEvents: List<Event> = emptyList(),
    val nextEvent: Event? = null,
    val pendingCandidateCount: Int = 0,
)
