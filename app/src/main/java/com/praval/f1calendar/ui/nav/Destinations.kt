package com.praval.f1calendar.ui.nav

/** A race the app was asked to open from outside — currently only from a reminder notification. */
data class RaceRef(val season: Int, val round: Int)

object Destinations {
    const val CALENDAR = "calendar"
    const val LIVE = "live"
    const val STANDINGS = "standings"
    const val SETTINGS = "settings"

    const val ARG_SEASON = "season"
    const val ARG_ROUND = "round"
    const val RACE_DETAIL = "race/{$ARG_SEASON}/{$ARG_ROUND}"

    fun raceDetail(season: Int, round: Int) = "race/$season/$round"

    /** Destinations that show the bottom bar; the detail screen is a pushed page instead. */
    val topLevel = listOf(CALENDAR, LIVE, STANDINGS, SETTINGS)
}
