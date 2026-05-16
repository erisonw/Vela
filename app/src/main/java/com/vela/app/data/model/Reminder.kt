package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Reminder(
    val id: String,
    val minutesBefore: Int,
    val label: String? = null,
)
