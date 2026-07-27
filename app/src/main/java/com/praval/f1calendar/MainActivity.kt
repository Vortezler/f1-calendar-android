package com.praval.f1calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.praval.f1calendar.core.PendingRaceSelection
import com.praval.f1calendar.core.RaceKey
import com.praval.f1calendar.ui.nav.F1NavHost
import com.praval.f1calendar.ui.theme.F1CalendarTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * The Activity can't reach the calendar's ViewModel, so which race to open travels through this
     * shared holder while navigation is handled separately by [showCalendarForAlarm].
     */
    @Inject lateinit var pendingRaceSelection: PendingRaceSelection

    private var showCalendarForAlarm by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAlarmIntent(intent)

        setContent {
            F1CalendarTheme {
                F1NavHost(
                    showCalendarForAlarm = showCalendarForAlarm,
                    onAlarmNavigationHandled = { showCalendarForAlarm = false },
                )
            }
        }
    }

    // launchMode is singleTop, so a second tap on a notification arrives here rather than in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(intent: Intent) {
        val season = intent.getIntExtra(EXTRA_OPEN_SEASON, 0)
        val round = intent.getIntExtra(EXTRA_OPEN_ROUND, 0)
        if (season <= 0 || round <= 0) return
        pendingRaceSelection.request(RaceKey(season, round))
        showCalendarForAlarm = true
    }

    companion object {
        const val EXTRA_OPEN_SEASON = "open_season"
        const val EXTRA_OPEN_ROUND = "open_round"
    }
}
