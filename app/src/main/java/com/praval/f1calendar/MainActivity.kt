package com.praval.f1calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.praval.f1calendar.ui.nav.F1NavHost
import com.praval.f1calendar.ui.nav.RaceRef
import com.praval.f1calendar.ui.theme.F1CalendarTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Set when a session reminder is tapped. The nav host consumes it once and clears it, so
     * rotating the device doesn't re-navigate.
     */
    private var pendingRace by mutableStateOf<RaceRef?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRace = intent.toRaceRef()

        setContent {
            F1CalendarTheme {
                F1NavHost(
                    pendingRace = pendingRace,
                    onPendingRaceHandled = { pendingRace = null },
                )
            }
        }
    }

    // launchMode is singleTop, so a second tap on a notification arrives here rather than in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRace = intent.toRaceRef()
    }

    private fun Intent.toRaceRef(): RaceRef? {
        val season = getIntExtra(EXTRA_OPEN_SEASON, 0)
        val round = getIntExtra(EXTRA_OPEN_ROUND, 0)
        return if (season > 0 && round > 0) RaceRef(season, round) else null
    }

    companion object {
        const val EXTRA_OPEN_SEASON = "open_season"
        const val EXTRA_OPEN_ROUND = "open_round"
    }
}
