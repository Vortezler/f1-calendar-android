package com.praval.f1calendar.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Session start times are stored as epoch millis (UTC) rather than as the API's date/time strings,
 * so ordering and "is it over yet" comparisons are plain integer maths. Rendering into the device
 * timezone happens in the UI layer.
 */
@Entity(tableName = "races", primaryKeys = ["season", "round"])
data class RaceEntity(
    val season: Int,
    val round: Int,
    val raceName: String,
    val circuitId: String,
    val circuitName: String,
    val locality: String?,
    val country: String?,
    val lat: Double?,
    val lng: Double?,
    val wikiUrl: String?,
    /** ISO-8601 date (UTC) of the grand prix; present even when no start time is known. */
    val raceDate: String,
    val raceUtc: Long?,
    val fp1Utc: Long?,
    val fp2Utc: Long?,
    val fp3Utc: Long?,
    val qualifyingUtc: Long?,
    val sprintUtc: Long?,
    val sprintQualifyingUtc: Long?,
)

@Entity(
    tableName = "race_results",
    primaryKeys = ["season", "round", "position"],
    indices = [Index("season", "round")],
)
data class RaceResultEntity(
    val season: Int,
    val round: Int,
    val position: Int,
    val positionText: String,
    val points: Double,
    val driverId: String,
    val driverCode: String?,
    val driverNumber: String?,
    val givenName: String,
    val familyName: String,
    val nationality: String?,
    val constructorId: String,
    val constructorName: String,
    val grid: Int?,
    val laps: Int?,
    val status: String?,
    val timeText: String?,
    val fastestLapTime: String?,
    val fastestLapRank: Int?,
)

@Entity(
    tableName = "qualifying_results",
    primaryKeys = ["season", "round", "position"],
    indices = [Index("season", "round")],
)
data class QualifyingResultEntity(
    val season: Int,
    val round: Int,
    val position: Int,
    val driverId: String,
    val driverCode: String?,
    val driverNumber: String?,
    val givenName: String,
    val familyName: String,
    val nationality: String?,
    val constructorId: String,
    val constructorName: String,
    val q1: String?,
    val q2: String?,
    val q3: String?,
)

/**
 * Keyed on driver rather than position: standings positions can tie mid-season and the API has
 * historically reported duplicate positions in that case.
 */
@Entity(tableName = "driver_standings", primaryKeys = ["season", "driverId"])
data class DriverStandingEntity(
    val season: Int,
    val driverId: String,
    val position: Int,
    val points: Double,
    val wins: Int,
    val driverCode: String?,
    val driverNumber: String?,
    val givenName: String,
    val familyName: String,
    val nationality: String?,
    /** Comma-separated ids/names — a driver can appear for more than one team in a season. */
    val constructorIds: String,
    val constructorNames: String,
)

@Entity(tableName = "constructor_standings", primaryKeys = ["season", "constructorId"])
data class ConstructorStandingEntity(
    val season: Int,
    val constructorId: String,
    val position: Int,
    val points: Double,
    val wins: Int,
    val constructorName: String,
    val nationality: String?,
)

/**
 * A per-session override of the standing alarm rule for that session type.
 *
 * A row exists only when the user has explicitly overridden one weekend's session — turning a
 * practice alarm on for a home race, or muting qualifying for a weekend they'll be watching live.
 * With no row, [SessionRuleEntity] decides.
 */
@Entity(tableName = "reminders", primaryKeys = ["season", "round", "session"])
data class ReminderEntity(
    val season: Int,
    val round: Int,
    /** [com.praval.f1calendar.domain.model.SessionType] name. */
    val session: String,
    val enabled: Boolean,
)

/**
 * The standing rule for a session type: whether it gets an alarm at all, and how far ahead.
 *
 * Absent rows fall back to [com.praval.f1calendar.domain.model.DefaultAlarmRules], so the table
 * only ever holds what the user actually changed.
 */
@Entity(tableName = "session_rules")
data class SessionRuleEntity(
    /** [com.praval.f1calendar.domain.model.SessionType] name. */
    @PrimaryKey val session: String,
    val enabled: Boolean,
    val leadMinutes: Int,
)

/** Every circuit that has ever held a championship grand prix, calendar or not. */
@Entity(tableName = "circuits")
data class CircuitEntity(
    @PrimaryKey val circuitId: String,
    val circuitName: String,
    val locality: String?,
    val country: String?,
    val lat: Double?,
    val lng: Double?,
    val wikiUrl: String?,
)

/**
 * The outright lap record for a circuit: the fastest lap ever set during a race there.
 *
 * A row exists only when a record is actually known. Circuits last raced before 2004 have no lap
 * times in the dataset at all, so "looked up and found nothing" is recorded in `cache_meta`
 * instead — the absence of a row here is not the same as never having checked.
 */
@Entity(tableName = "lap_records")
data class LapRecordEntity(
    @PrimaryKey val circuitId: String,
    val timeText: String,
    val millis: Long,
    val driverId: String,
    val givenName: String,
    val familyName: String,
    val driverCode: String?,
    val constructorId: String,
    val constructorName: String,
    val season: Int,
    val raceName: String,
)

/** Tracks when each remote resource was last fetched so refreshes can respect a TTL. */
@Entity(tableName = "cache_meta")
data class CacheMetaEntity(
    @PrimaryKey val key: String,
    val fetchedAt: Long,
)
