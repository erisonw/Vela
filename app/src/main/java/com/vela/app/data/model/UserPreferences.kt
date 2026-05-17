package com.vela.app.data.model

data class UserPreferences(
    val defaultReminderMinutes: Int? = DefaultReminderMinutes,
    val aiServiceStatusText: String = "未接入真实 AI 服务",
    val aiEndpoint: String = "",
    val aiApiKey: String = "",
    val aiTextModel: String = "",
    val aiVisionModel: String = "",
    val aiDocumentModel: String = "",
    val aiVoiceModel: String = "",
    val weatherLatitude: Double? = null,
    val weatherLongitude: Double? = null,
    val weatherUpdatedAt: String? = null,
)
