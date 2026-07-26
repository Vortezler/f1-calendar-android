package com.praval.f1calendar.domain.model

/**
 * How far ahead of a session type the app should raise an alarm, and whether it should at all.
 *
 * Rules are per *type*, not per session instance: setting "Qualifying, 30 minutes" once arms every
 * qualifying session on the calendar. Individual weekends can still be overridden from the race
 * page, which writes a [com.praval.f1calendar.data.local.entity.ReminderEntity] row.
 */
data class SessionAlarmRule(
    val type: SessionType,
    val enabled: Boolean,
    val leadMinutes: Int,
)

object DefaultAlarmRules {

    /** Lead times offered in the UI. */
    val LEAD_TIME_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 120)

    /**
     * Sensible out-of-the-box behaviour: alert for the sessions that decide something, stay quiet
     * for free practice. Longer notice for the grand prix, which people plan their day around.
     */
    fun forType(type: SessionType): SessionAlarmRule = when (type) {
        SessionType.RACE -> SessionAlarmRule(type, enabled = true, leadMinutes = 60)
        SessionType.QUALIFYING -> SessionAlarmRule(type, enabled = true, leadMinutes = 30)
        SessionType.SPRINT -> SessionAlarmRule(type, enabled = true, leadMinutes = 30)
        SessionType.SPRINT_QUALIFYING -> SessionAlarmRule(type, enabled = true, leadMinutes = 30)
        SessionType.FP1 -> SessionAlarmRule(type, enabled = false, leadMinutes = 15)
        SessionType.FP2 -> SessionAlarmRule(type, enabled = false, leadMinutes = 15)
        SessionType.FP3 -> SessionAlarmRule(type, enabled = false, leadMinutes = 15)
    }

    fun all(): List<SessionAlarmRule> = SessionType.entries.map(::forType)
}
