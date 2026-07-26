package com.praval.f1calendar.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Ergast/Jolpica returns every scalar as a JSON string, including numbers, and wraps every payload
 * in an "MRData" envelope. These DTOs mirror that shape verbatim; conversion to typed domain
 * models happens in the mapper layer.
 */

@Serializable
data class SessionDto(
    val date: String,
    val time: String? = null,
)

@Serializable
data class LocationDto(
    val lat: String? = null,
    val long: String? = null,
    val locality: String? = null,
    val country: String? = null,
)

@Serializable
data class CircuitDto(
    val circuitId: String,
    val url: String? = null,
    val circuitName: String,
    @SerialName("Location") val location: LocationDto? = null,
)

@Serializable
data class DriverDto(
    val driverId: String,
    val permanentNumber: String? = null,
    val code: String? = null,
    val url: String? = null,
    val givenName: String,
    val familyName: String,
    val dateOfBirth: String? = null,
    val nationality: String? = null,
)

/** Named with a suffix because `constructor` is a reserved modifier in Kotlin. */
@Serializable
data class ConstructorDto(
    val constructorId: String,
    val url: String? = null,
    val name: String,
    val nationality: String? = null,
)

@Serializable
data class TimeDto(
    val millis: String? = null,
    val time: String? = null,
)

@Serializable
data class FastestLapDto(
    val rank: String? = null,
    val lap: String? = null,
    @SerialName("Time") val time: TimeDto? = null,
)

@Serializable
data class ResultDto(
    val number: String? = null,
    val position: String,
    val positionText: String? = null,
    val points: String? = null,
    @SerialName("Driver") val driver: DriverDto,
    @SerialName("Constructor") val team: ConstructorDto,
    val grid: String? = null,
    val laps: String? = null,
    val status: String? = null,
    @SerialName("Time") val time: TimeDto? = null,
    @SerialName("FastestLap") val fastestLap: FastestLapDto? = null,
)

@Serializable
data class QualifyingResultDto(
    val number: String? = null,
    val position: String,
    @SerialName("Driver") val driver: DriverDto,
    @SerialName("Constructor") val team: ConstructorDto,
    @SerialName("Q1") val q1: String? = null,
    @SerialName("Q2") val q2: String? = null,
    @SerialName("Q3") val q3: String? = null,
)

@Serializable
data class RaceDto(
    val season: String,
    val round: String,
    val url: String? = null,
    val raceName: String,
    @SerialName("Circuit") val circuit: CircuitDto,
    val date: String,
    val time: String? = null,
    @SerialName("FirstPractice") val firstPractice: SessionDto? = null,
    @SerialName("SecondPractice") val secondPractice: SessionDto? = null,
    @SerialName("ThirdPractice") val thirdPractice: SessionDto? = null,
    @SerialName("Qualifying") val qualifying: SessionDto? = null,
    @SerialName("Sprint") val sprint: SessionDto? = null,
    @SerialName("SprintQualifying") val sprintQualifying: SessionDto? = null,
    // 2023 only; renamed to SprintQualifying from 2024 onward.
    @SerialName("SprintShootout") val sprintShootout: SessionDto? = null,
    @SerialName("Results") val results: List<ResultDto>? = null,
    @SerialName("QualifyingResults") val qualifyingResults: List<QualifyingResultDto>? = null,
)

@Serializable
data class RaceTableDto(
    val season: String? = null,
    val round: String? = null,
    @SerialName("Races") val races: List<RaceDto> = emptyList(),
)

@Serializable
data class RaceMRData(
    val total: String? = null,
    val limit: String? = null,
    val offset: String? = null,
    @SerialName("RaceTable") val raceTable: RaceTableDto,
)

@Serializable
data class RaceResponse(
    @SerialName("MRData") val data: RaceMRData,
)

@Serializable
data class DriverStandingDto(
    val position: String? = null,
    val positionText: String? = null,
    val points: String? = null,
    val wins: String? = null,
    @SerialName("Driver") val driver: DriverDto,
    @SerialName("Constructors") val teams: List<ConstructorDto> = emptyList(),
)

@Serializable
data class ConstructorStandingDto(
    val position: String? = null,
    val positionText: String? = null,
    val points: String? = null,
    val wins: String? = null,
    @SerialName("Constructor") val team: ConstructorDto,
)

@Serializable
data class StandingsListDto(
    val season: String,
    val round: String? = null,
    @SerialName("DriverStandings") val driverStandings: List<DriverStandingDto> = emptyList(),
    @SerialName("ConstructorStandings") val constructorStandings: List<ConstructorStandingDto> = emptyList(),
)

@Serializable
data class StandingsTableDto(
    val season: String? = null,
    @SerialName("StandingsLists") val standingsLists: List<StandingsListDto> = emptyList(),
)

@Serializable
data class StandingsMRData(
    val total: String? = null,
    @SerialName("StandingsTable") val standingsTable: StandingsTableDto,
)

@Serializable
data class StandingsResponse(
    @SerialName("MRData") val data: StandingsMRData,
)
