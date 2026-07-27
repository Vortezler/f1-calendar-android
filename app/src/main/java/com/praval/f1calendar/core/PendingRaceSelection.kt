package com.praval.f1calendar.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/** Identifies one grand prix. */
data class RaceKey(val season: Int, val round: Int)

/**
 * Hand-off point for "open this race" requests that originate outside the UI — currently only a
 * tapped session alarm.
 *
 * The Activity can't reach the calendar's ViewModel directly, and the request has to survive the
 * gap between the intent arriving and the calendar's schedule finishing loading, so it is parked
 * here until the ViewModel can honour it.
 */
@Singleton
class PendingRaceSelection @Inject constructor() {

    private val _race = MutableStateFlow<RaceKey?>(null)
    val race: StateFlow<RaceKey?> = _race.asStateFlow()

    fun request(key: RaceKey) {
        _race.value = key
    }

    /** Returns the outstanding request, if any, and clears it so it is honoured only once. */
    fun consume(): RaceKey? = _race.getAndUpdate { null }
}
