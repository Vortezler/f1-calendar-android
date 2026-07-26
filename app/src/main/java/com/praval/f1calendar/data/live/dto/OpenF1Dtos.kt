package com.praval.f1calendar.data.live.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/*
 * OpenF1 returns flat JSON arrays with snake_case keys. Every endpoint here is public and needs no
 * key. Unlike Jolpica, numbers really are numbers.
 */

@Serializable
data class OpenF1SessionDto(
    @SerialName("session_key") val sessionKey: Int,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("session_type") val sessionType: String? = null,
    @SerialName("date_start") val dateStart: String? = null,
    @SerialName("date_end") val dateEnd: String? = null,
    @SerialName("circuit_short_name") val circuitShortName: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val location: String? = null,
    val year: Int? = null,
)

@Serializable
data class OpenF1DriverDto(
    @SerialName("driver_number") val driverNumber: Int,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("name_acronym") val acronym: String? = null,
    @SerialName("team_name") val teamName: String? = null,
    /** Six hex digits with no leading '#', e.g. "F47600". Null for some historical sessions. */
    @SerialName("team_colour") val teamColour: String? = null,
)

@Serializable
data class OpenF1PositionDto(
    val date: String,
    @SerialName("driver_number") val driverNumber: Int,
    val position: Int,
)

/**
 * `interval` and `gap_to_leader` are usually numbers of seconds, but OpenF1 switches them to
 * strings such as "+1 LAP" once a driver is lapped, so they're kept as raw JSON and rendered by
 * [gapText].
 */
@Serializable
data class OpenF1IntervalDto(
    val date: String,
    @SerialName("driver_number") val driverNumber: Int,
    val interval: JsonElement? = null,
    @SerialName("gap_to_leader") val gapToLeader: JsonElement? = null,
)

@Serializable
data class OpenF1LapDto(
    @SerialName("driver_number") val driverNumber: Int,
    @SerialName("lap_number") val lapNumber: Int? = null,
    @SerialName("lap_duration") val lapDuration: Double? = null,
    @SerialName("date_start") val dateStart: String? = null,
    @SerialName("is_pit_out_lap") val isPitOutLap: Boolean? = null,
)

/** Renders a gap field that may be a number of seconds or an already-formatted lapped marker. */
fun JsonElement?.gapText(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.content.equals("null", ignoreCase = true)) return null
    primitive.content.toDoubleOrNull()?.let { seconds ->
        return if (seconds <= 0.0) null else "+" + String.format(java.util.Locale.US, "%.3f", seconds)
    }
    return primitive.content.takeIf { it.isNotBlank() }
}
