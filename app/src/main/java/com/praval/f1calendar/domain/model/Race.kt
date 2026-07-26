package com.praval.f1calendar.domain.model

import com.praval.f1calendar.core.CountryFlags
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

data class RaceSession(
    val type: SessionType,
    val startsAt: Instant?,
)

data class Race(
    val season: Int,
    val round: Int,
    val name: String,
    val circuitId: String,
    val circuitName: String,
    val locality: String?,
    val country: String?,
    val wikiUrl: String?,
    /** Calendar date of the grand prix in UTC, always present even for pre-2005 rounds with no times. */
    val raceDate: LocalDate,
    val raceStart: Instant?,
    val sessions: List<RaceSession>,
) {
    val flag: String get() = CountryFlags.emojiFor(country)

    val isSprintWeekend: Boolean
        get() = sessions.any { it.type == SessionType.SPRINT }

    /**
     * Grand prix distance is capped at two hours of racing plus red-flag allowance, so a race is
     * treated as over three hours after lights out. Seasons old enough to lack start times fall
     * back to the end of the race day.
     */
    val endsAt: Instant
        get() = raceStart?.plus(Duration.ofHours(3))
            ?: raceDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)

    fun isCompleted(now: Instant): Boolean = now.isAfter(endsAt)

    fun sessionsInOrder(): List<RaceSession> =
        sessions.sortedWith(compareBy({ it.startsAt ?: Instant.MAX }, { it.type.ordinal }))

    fun session(type: SessionType): RaceSession? = sessions.firstOrNull { it.type == type }
}
