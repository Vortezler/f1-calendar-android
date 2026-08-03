package com.praval.f1calendar.domain.model

import com.praval.f1calendar.core.CountryFlags

/** Every circuit that has ever held a championship grand prix, not only those on a calendar. */
data class Circuit(
    val id: String,
    val name: String,
    val locality: String?,
    val country: String?,
    val wikiUrl: String?,
) {
    val flag: String get() = CountryFlags.emojiFor(country)
}

/**
 * The fastest race lap ever recorded at a circuit, which is what Formula 1 recognises as the
 * outright lap record — qualifying laps are faster but don't count.
 */
data class LapRecord(
    val circuitId: String,
    val time: String,
    val millis: Long,
    val driverId: String,
    val driverName: String,
    val driverCode: String?,
    val teamId: String,
    val teamName: String,
    val season: Int,
    val raceName: String,
)

data class CircuitRecord(
    val circuit: Circuit,
    val record: LapRecord?,
    /** True once the record has been looked up, whether or not one exists. */
    val loaded: Boolean,
)
