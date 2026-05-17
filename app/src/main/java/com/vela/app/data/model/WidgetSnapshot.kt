package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WidgetSnapshot(
    val generatedAt: String,
    val title: String,
    val todayEvents: List<Event> = emptyList(),
    val upcomingEvents: List<Event> = emptyList(),
    val nextEvent: Event? = null,
    val remainingEventCount: Int = 0,
    val weatherHint: String = "天气待获取",
    val prepHint: String = "准备提醒待生成",
    val weatherStatusText: String = "真实天气未接入",
    val pendingCandidateCount: Int = 0,
)
