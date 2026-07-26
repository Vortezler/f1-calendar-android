package com.praval.f1calendar.domain.model

import java.time.Duration
import java.time.Instant

/** A session as OpenF1 sees it, which is the unit its live timing is keyed on. */
data class LiveSession(
    val sessionKey: Int,
    val name: String,
    val type: String,
    val location: String?,
    val countryName: String?,
    val start: Instant?,
    val end: Instant?,
) {
    val isRace: Boolean get() = type.equals("Race", ignoreCase = true)

    /**
     * Sessions routinely overrun — red flags, delayed starts — so the window is padded past the
     * scheduled end rather than cutting the tab away while cars are still running.
     */
    fun isLive(now: Instant): Boolean {
        val from = start ?: return false
        val until = (end ?: from.plus(Duration.ofHours(2))).plus(END_GRACE)
        return !now.isBefore(from) && !now.isAfter(until)
    }

    /** Shortly before lights out the tab is worth showing so people can park on it. */
    fun isImminent(now: Instant): Boolean {
        val from = start ?: return false
        return now.isBefore(from) && Duration.between(now, from) <= START_LEAD
    }

    private companion object {
        val END_GRACE: Duration = Duration.ofMinutes(20)
        val START_LEAD: Duration = Duration.ofMinutes(15)
    }
}

data class LiveStanding(
    val position: Int?,
    val driverNumber: Int,
    val acronym: String,
    val fullName: String,
    val teamName: String?,
    /** 0xAARRGGBB, from OpenF1's team colour or the local livery table as a fallback. */
    val teamColour: Long,
    /** Gap to the leader, pre-formatted ("+12.480", "+1 LAP"). Race sessions only. */
    val gapToLeader: String?,
    /** Gap to the car ahead. Race sessions only. */
    val interval: String?,
    /** Seconds. Practice and qualifying are ordered by this. */
    val bestLapSeconds: Double?,
    val lastLapSeconds: Double?,
)
