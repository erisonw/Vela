package com.vela.app.navigation

object VelaRoutes {
    const val Calendar = "calendar"
    const val Schedule = "schedule"
    const val AiSchedule = "ai/schedule"
    const val ImportChat = AiSchedule
    const val SmartEdit = "smart/edit"
    const val Settings = "settings"
    const val Event = "event/{eventId}"

    fun event(eventId: String): String = "event/$eventId"
}

data class VelaTopLevelDestination(
    val route: String,
    val label: String,
    val compactLabel: String,
)

val VelaTopLevelDestinations = listOf(
    VelaTopLevelDestination(
        route = VelaRoutes.Calendar,
        label = "日历",
        compactLabel = "□",
    ),
    VelaTopLevelDestination(
        route = VelaRoutes.Schedule,
        label = "日程",
        compactLabel = "▤",
    ),
    VelaTopLevelDestination(
        route = VelaRoutes.AiSchedule,
        label = "AI 日程",
        compactLabel = "AI",
    ),
)
