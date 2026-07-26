package com.praval.f1calendar.domain.model

data class Driver(
    val id: String,
    val code: String?,
    val permanentNumber: String?,
    val givenName: String,
    val familyName: String,
    val nationality: String?,
) {
    val fullName: String get() = "$givenName $familyName"

    /** Falls back to the surname when a driver has no three-letter code (common pre-2000). */
    val shortName: String get() = code ?: familyName.take(3).uppercase()
}

data class Team(
    val id: String,
    val name: String,
)

data class RaceResult(
    val position: Int,
    /** "1", "2"… for classified finishers; "R", "D", "W", "N" for retirements and exclusions. */
    val positionText: String,
    val driver: Driver,
    val team: Team,
    val grid: Int?,
    val laps: Int?,
    val status: String?,
    /** Winner shows total race time; everyone else shows a gap. Null when not classified. */
    val time: String?,
    val points: Double,
    val fastestLapTime: String?,
    val fastestLapRank: Int?,
) {
    val isClassified: Boolean get() = positionText.toIntOrNull() != null
    val setFastestLap: Boolean get() = fastestLapRank == 1
}

data class QualifyingResult(
    val position: Int,
    val driver: Driver,
    val team: Team,
    val q1: String?,
    val q2: String?,
    val q3: String?,
) {
    /** The lap that decided the driver's grid slot — their best session reached. */
    val bestTime: String? get() = q3 ?: q2 ?: q1
}

data class DriverStanding(
    val position: Int,
    val driver: Driver,
    val teams: List<Team>,
    val points: Double,
    val wins: Int,
)

data class ConstructorStanding(
    val position: Int,
    val team: Team,
    val nationality: String?,
    val points: Double,
    val wins: Int,
)
