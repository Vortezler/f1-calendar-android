package com.praval.f1calendar.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.praval.f1calendar.ui.calendar.CalendarScreen
import com.praval.f1calendar.ui.racedetail.RaceDetailScreen
import com.praval.f1calendar.ui.settings.SettingsScreen
import com.praval.f1calendar.ui.standings.StandingsScreen

private data class TopLevelItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelItems = listOf(
    TopLevelItem(Destinations.CALENDAR, "Calendar", Icons.Filled.DateRange),
    TopLevelItem(Destinations.STANDINGS, "Standings", Icons.Filled.Star),
    TopLevelItem(Destinations.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun F1NavHost(
    pendingRace: RaceRef?,
    onPendingRaceHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Destinations.topLevel

    // A tapped reminder opens straight onto that race.
    LaunchedEffect(pendingRace) {
        val race = pendingRace ?: return@LaunchedEffect
        navController.navigate(Destinations.raceDetail(race.season, race.round)) {
            launchSingleTop = true
        }
        onPendingRaceHandled()
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute == item.route) return@NavigationBarItem
                                navController.navigate(item.route) {
                                    // Standard bottom-nav behaviour: one entry per tab, state kept.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.CALENDAR,
            modifier = Modifier.padding(
                // The detail screen draws its own bottom inset; top-level screens sit above the bar.
                bottom = innerPadding.calculateBottomPadding(),
            ),
        ) {
            composable(Destinations.CALENDAR) {
                CalendarScreen(
                    onRaceClick = { season, round ->
                        navController.navigate(Destinations.raceDetail(season, round))
                    },
                )
            }
            composable(Destinations.STANDINGS) {
                StandingsScreen()
            }
            composable(Destinations.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Destinations.RACE_DETAIL,
                arguments = listOf(
                    navArgument(Destinations.ARG_SEASON) { type = NavType.IntType },
                    navArgument(Destinations.ARG_ROUND) { type = NavType.IntType },
                ),
            ) {
                RaceDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
