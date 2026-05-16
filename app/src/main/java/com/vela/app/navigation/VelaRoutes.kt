package com.vela.app.navigation

object VelaRoutes {
    const val Home = "home"
    const val ImportChat = "import/chat"
    const val Calendar = "calendar"
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
        route = VelaRoutes.Home,
        label = "Home",
        compactLabel = "H",
    ),
    VelaTopLevelDestination(
        route = VelaRoutes.ImportChat,
        label = "Import",
        compactLabel = "I",
    ),
    VelaTopLevelDestination(
        route = VelaRoutes.Calendar,
        label = "Calendar",
        compactLabel = "C",
    ),
)
