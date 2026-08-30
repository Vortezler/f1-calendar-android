package com.praval.f1calendar.notifications

object NotificationIds {
    const val CHANNEL_SESSIONS = "session_reminders"
    const val CHANNEL_RESULTS = "session_results"

    /** Distinct per season/round/session so multiple reminders can be pending at once. */
    fun requestCode(season: Int, round: Int, sessionOrdinal: Int): Int =
        (season * 100_000) + (round * 100) + sessionOrdinal

    /**
     * Same shape as [requestCode] but offset into its own range, so a result notification never
     * collides with an alarm's PendingIntent even though both are keyed on the same season/round/
     * session ordinal.
     */
    fun resultRequestCode(season: Int, round: Int, sessionOrdinal: Int): Int =
        RESULT_BASE + requestCode(season, round, sessionOrdinal)

    private const val RESULT_BASE = 10_000_000
}
