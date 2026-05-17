package com.vela.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.vela.app.feature.calendar.CalendarScreen
import com.vela.app.feature.home.HomeScreen
import com.vela.app.feature.importchat.ImportChatScreen
import com.vela.app.feature.settings.SettingsScreen

@Composable
fun VelaNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val topLevelRoute = when (currentRoute) {
        VelaRoutes.Calendar -> VelaRoutes.Calendar
        VelaRoutes.Schedule, VelaRoutes.Event, "home" -> VelaRoutes.Schedule
        VelaRoutes.AiSchedule, VelaRoutes.SmartEdit -> VelaRoutes.AiSchedule
        else -> null
    }

    Scaffold(
        bottomBar = {
            if (currentRoute != VelaRoutes.Settings) {
                NavigationBar(
                    containerColor = Color.White.copy(alpha = 0.96f),
                    tonalElevation = NavigationBarDefaults.Elevation,
                ) {
                    VelaTopLevelDestinations.forEach { destination ->
                        val selected = (
                            currentDestination
                                ?.hierarchy
                                ?.any { it.route == destination.route } == true
                            ) || topLevelRoute == destination.route

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (topLevelRoute == destination.route) {
                                    return@NavigationBarItem
                                }

                                if (destination.route == VelaRoutes.Calendar) {
                                    val poppedToCalendar = navController.popBackStack(
                                        route = VelaRoutes.Calendar,
                                        inclusive = false,
                                    )
                                    if (!poppedToCalendar) {
                                        navController.navigate(VelaRoutes.Calendar) {
                                            launchSingleTop = true
                                        }
                                    }
                                } else {
                                    navController.navigate(destination.route) {
                                        popUpTo(VelaRoutes.Calendar) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Text(text = destination.compactLabel) },
                            label = { Text(text = destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .background(Color(0xFFFAFBFF)),
        ) {
            NavHost(
                navController = navController,
                startDestination = VelaRoutes.Calendar,
            ) {
                composable(
                    route = VelaRoutes.Calendar,
                ) {
                    CalendarScreen(
                        onScheduleClick = { navController.navigate(VelaRoutes.Schedule) },
                    )
                }

                composable(
                    route = VelaRoutes.Schedule,
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "vela://home" },
                        navDeepLink { uriPattern = "vela://schedule" },
                    ),
                ) {
                    HomeScreen(
                        eventId = null,
                        onImportChatClick = { navController.navigate(VelaRoutes.AiSchedule) },
                        onCalendarClick = { navController.navigate(VelaRoutes.Calendar) },
                        onEventClick = { eventId -> navController.navigate(VelaRoutes.event(eventId)) },
                    )
                }

                composable(route = "home") {
                    HomeScreen(
                        eventId = null,
                        onImportChatClick = { navController.navigate(VelaRoutes.AiSchedule) },
                        onCalendarClick = { navController.navigate(VelaRoutes.Calendar) },
                        onEventClick = { eventId -> navController.navigate(VelaRoutes.event(eventId)) },
                    )
                }

                composable(
                    route = VelaRoutes.AiSchedule,
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "vela://import/chat" },
                    ),
                ) {
                    ImportChatScreen(
                        onCalendarClick = { navController.navigate(VelaRoutes.Calendar) },
                        onScheduleClick = { navController.navigate(VelaRoutes.Schedule) },
                        onSettingsClick = { navController.navigate(VelaRoutes.Settings) },
                    )
                }

                composable(
                    route = VelaRoutes.SmartEdit,
                    deepLinks = listOf(navDeepLink { uriPattern = "vela://smart/edit" }),
                ) {
                    ImportChatScreen(
                        onCalendarClick = { navController.navigate(VelaRoutes.Calendar) },
                        onScheduleClick = { navController.navigate(VelaRoutes.Schedule) },
                        onSettingsClick = { navController.navigate(VelaRoutes.Settings) },
                    )
                }

                composable(route = VelaRoutes.Settings) {
                    SettingsScreen(onBackClick = { navController.navigateUp() })
                }

                composable(
                    route = VelaRoutes.Event,
                    arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = "vela://event/{eventId}" }),
                ) { eventBackStackEntry ->
                    HomeScreen(
                        eventId = eventBackStackEntry.arguments?.getString("eventId"),
                        onImportChatClick = { navController.navigate(VelaRoutes.AiSchedule) },
                        onCalendarClick = { navController.navigate(VelaRoutes.Calendar) },
                        onEventClick = { eventId -> navController.navigate(VelaRoutes.event(eventId)) },
                    )
                }

            }
        }
    }
}
