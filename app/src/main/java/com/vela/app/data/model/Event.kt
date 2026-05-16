package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String,
    val title: String,
    val startAt: String,
    val endAt: String? = null,
    val timezone: String? = null,
    val location: Location? = null,
    val description: String? = null,
    val reminders: List<Reminder> = emptyList(),
    val sourceSessionId: String? = null,
)
