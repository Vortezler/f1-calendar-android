package com.praval.f1calendar.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.praval.f1calendar.ui.calendar.CalendarScreen
import com.praval.f1calendar.ui.live.LiveScreen
import com.praval.f1calendar.ui.live.LiveTabViewModel
import com.praval.f1calendar.ui.settings.SettingsScreen
import com.praval.f1calendar.ui.standings.StandingsScreen

private data class TopLevelItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val calendarItem = TopLevelItem(Destinations.CALENDAR, "Calendar", Icons.Filled.DateRange)
private val liveItem = TopLevelItem(Destinations.LIVE, "Live", Icons.Filled.PlayArrow)
private val standingsItem = TopLevelItem(Destinations.STANDINGS, "Standings", Icons.Filled.Star)
private val settingsItem = TopLevelItem(Destinations.SETTINGS, "Settings", Icons.Filled.Settings)

@Composable
fun F1NavHost(
    /** Set when a session alarm is tapped; the round itself is applied by the calendar's ViewModel. */
    showCalendarForAlarm: Boolean,
    onAlarmNavigationHandled: () -> Unit,
    liveTabViewModel: LiveTabViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Destinations.topLevel

    // The Live tab only exists while something is actually running on track.
    val liveVisible by liveTabViewModel.visible.collectAsStateWithLifecycle()
    val topLevelItems = remember(liveVisible) {
        buildList {
            add(calendarItem)
            if (liveVisible) add(liveItem)
            add(standingsItem)
            add(settingsItem)
        }
    }

    // Don't strand the user on a tab that just disappeared when the session ended.
    LaunchedEffect(liveVisible, currentRoute) {
        if (!liveVisible && currentRoute == Destinations.LIVE) {
            navController.navigate(Destinations.CALENDAR) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    // A tapped alarm brings the calendar forward; CalendarViewModel spins the wheel to the round.
    LaunchedEffect(showCalendarForAlarm) {
        if (!showCalendarForAlarm) return@LaunchedEffect
        if (currentRoute != Destinations.CALENDAR) {
            navController.navigate(Destinations.CALENDAR) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        onAlarmNavigationHandled()
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
                CalendarScreen()
            }
            composable(Destinations.LIVE) {
                LiveScreen()
            }
            composable(Destinations.STANDINGS) {
                StandingsScreen()
            }
            composable(Destinations.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
