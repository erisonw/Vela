package com.vela.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@Composable
fun VelaNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                VelaTopLevelDestinations.forEach { destination ->
                    val selected = (
                        currentDestination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true
                        ) || (
                        currentDestination?.route == VelaRoutes.Event &&
                            destination.route == VelaRoutes.Calendar
                        )

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(VelaRoutes.Home) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(text = destination.compactLabel) },
                        label = { Text(text = destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = VelaRoutes.Home,
            ) {
                composable(
                    route = VelaRoutes.Home,
                    deepLinks = listOf(navDeepLink { uriPattern = "vela://home" }),
                ) {
                    HomeScreen(
                        onImportChatClick = { navController.navigate(VelaRoutes.ImportChat) },
                        onCalendarClick = { navController.navigate(VelaRoutes.Calendar) },
                        onEventClick = { eventId -> navController.navigate(VelaRoutes.event(eventId)) },
                    )
                }

                composable(
                    route = VelaRoutes.ImportChat,
                    deepLinks = listOf(navDeepLink { uriPattern = "vela://import/chat" }),
                ) {
                    ImportChatScreen(
                        onCalendarClick = { navController.navigate(VelaRoutes.Calendar) },
                    )
                }

                composable(route = VelaRoutes.Calendar) {
                    CalendarScreen(
                        eventId = null,
                        onBackClick = { navController.navigateUp() },
                    )
                }

                composable(
                    route = VelaRoutes.Event,
                    arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = "vela://event/{eventId}" }),
                ) { eventBackStackEntry ->
                    CalendarScreen(
                        eventId = eventBackStackEntry.arguments?.getString("eventId"),
                        onBackClick = { navController.navigateUp() },
                    )
                }
            }
        }
    }
}
