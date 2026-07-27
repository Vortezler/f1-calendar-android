package com.praval.f1calendar.ui.nav

object Destinations {
    const val CALENDAR = "calendar"
    const val LIVE = "live"
    const val STANDINGS = "standings"
    const val SETTINGS = "settings"

    /**
     * Every destination is top level. A race no longer has a page of its own — the calendar *is*
     * the race view, with the wheel choosing which round it shows.
     */
    val topLevel = listOf(CALENDAR, LIVE, STANDINGS, SETTINGS)
}
