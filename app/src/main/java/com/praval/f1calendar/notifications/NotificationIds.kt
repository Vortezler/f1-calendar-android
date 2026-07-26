package com.praval.f1calendar.notifications

object NotificationIds {
    const val CHANNEL_SESSIONS = "session_reminders"

    /** Distinct per season/round/session so multiple reminders can be pending at once. */
    fun requestCode(season: Int, round: Int, sessionOrdinal: Int): Int =
        (season * 100_000) + (round * 100) + sessionOrdinal
}
