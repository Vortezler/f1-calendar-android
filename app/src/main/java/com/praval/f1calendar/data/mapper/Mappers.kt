package com.praval.f1calendar.data.mapper

import com.praval.f1calendar.core.LapTime
import com.praval.f1calendar.data.local.entity.CircuitEntity
import com.praval.f1calendar.data.local.entity.ConstructorStandingEntity
import com.praval.f1calendar.data.local.entity.LapRecordEntity
import com.praval.f1calendar.data.local.entity.DriverStandingEntity
import com.praval.f1calendar.data.local.entity.QualifyingResultEntity
import com.praval.f1calendar.data.local.entity.RaceEntity
import com.praval.f1calendar.data.local.entity.RaceResultEntity
import com.praval.f1calendar.data.remote.dto.CircuitDto
import com.praval.f1calendar.data.remote.dto.ConstructorStandingDto
import com.praval.f1calendar.data.remote.dto.DriverStandingDto
import com.praval.f1calendar.data.remote.dto.QualifyingResultDto
import com.praval.f1calendar.data.remote.dto.RaceDto
import com.praval.f1calendar.data.remote.dto.ResultDto
import com.praval.f1calendar.data.remote.dto.SessionDto
import com.praval.f1calendar.domain.model.Circuit
import com.praval.f1calendar.domain.model.ConstructorStanding
import com.praval.f1calendar.domain.model.Driver
import com.praval.f1calendar.domain.model.DriverStanding
import com.praval.f1calendar.domain.model.LapRecord
import com.praval.f1calendar.domain.model.QualifyingResult
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.RaceResult
import com.praval.f1calendar.domain.model.RaceSession
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.domain.model.Team
import java.time.Instant
import java.time.LocalDate

private const val LIST_SEPARATOR = "|"

/** Stand-in for an unparseable date. `LocalDate.EPOCH` only exists from API 34. */
private val EPOCH_DATE: LocalDate = LocalDate.of(1970, 1, 1)

/**
 * The API splits a session timestamp across `date` ("2026-03-08") and `time` ("04:00:00Z").
 * Returns null when either half is missing or unparseable — seasons before 2005 carry dates only.
 */
private fun SessionDto.startMillis(): Long? = combineToMillis(date, time)

private fun combineToMillis(date: String?, time: String?): Long? {
    if (date.isNullOrBlank() || time.isNullOrBlank()) return null
    return runCatching { Instant.parse("${date}T$time").toEpochMilli() }.getOrNull()
}

// region remote -> local

fun RaceDto.toEntityOrNull(): RaceEntity? {
    val seasonInt = season.toIntOrNull() ?: return null
    val roundInt = round.toIntOrNull() ?: return null
    return RaceEntity(
        season = seasonInt,
        round = roundInt,
        raceName = raceName,
        circuitId = circuit.circuitId,
        circuitName = circuit.circuitName,
        locality = circuit.location?.locality,
        country = circuit.location?.country,
        lat = circuit.location?.lat?.toDoubleOrNull(),
        lng = circuit.location?.long?.toDoubleOrNull(),
        wikiUrl = url,
        raceDate = date,
        raceUtc = combineToMillis(date, time),
        fp1Utc = firstPractice?.startMillis(),
        fp2Utc = secondPractice?.startMillis(),
        fp3Utc = thirdPractice?.startMillis(),
        qualifyingUtc = qualifying?.startMillis(),
        sprintUtc = sprint?.startMillis(),
        // 2023 called it SprintShootout; later seasons call it SprintQualifying.
        sprintQualifyingUtc = (sprintQualifying ?: sprintShootout)?.startMillis(),
    )
}

fun ResultDto.toEntityOrNull(season: Int, round: Int): RaceResultEntity? {
    val pos = position.toIntOrNull() ?: return null
    return RaceResultEntity(
        season = season,
        round = round,
        position = pos,
        positionText = positionText ?: position,
        points = points?.toDoubleOrNull() ?: 0.0,
        driverId = driver.driverId,
        driverCode = driver.code,
        driverNumber = number ?: driver.permanentNumber,
        givenName = driver.givenName,
        familyName = driver.familyName,
        nationality = driver.nationality,
        constructorId = team.constructorId,
        constructorName = team.name,
        grid = grid?.toIntOrNull(),
        laps = laps?.toIntOrNull(),
        status = status,
        timeText = time?.time,
        fastestLapTime = fastestLap?.time?.time,
        fastestLapRank = fastestLap?.rank?.toIntOrNull(),
    )
}

fun QualifyingResultDto.toEntityOrNull(season: Int, round: Int): QualifyingResultEntity? {
    val pos = position.toIntOrNull() ?: return null
    return QualifyingResultEntity(
        season = season,
        round = round,
        position = pos,
        driverId = driver.driverId,
        driverCode = driver.code,
        driverNumber = number ?: driver.permanentNumber,
        givenName = driver.givenName,
        familyName = driver.familyName,
        nationality = driver.nationality,
        constructorId = team.constructorId,
        constructorName = team.name,
        q1 = q1?.takeIf { it.isNotBlank() },
        q2 = q2?.takeIf { it.isNotBlank() },
        q3 = q3?.takeIf { it.isNotBlank() },
    )
}

fun DriverStandingDto.toEntityOrNull(season: Int): DriverStandingEntity? {
    val pos = position?.toIntOrNull() ?: return null
    return DriverStandingEntity(
        season = season,
        driverId = driver.driverId,
        position = pos,
        points = points?.toDoubleOrNull() ?: 0.0,
        wins = wins?.toIntOrNull() ?: 0,
        driverCode = driver.code,
        driverNumber = driver.permanentNumber,
        givenName = driver.givenName,
        familyName = driver.familyName,
        nationality = driver.nationality,
        constructorIds = teams.joinToString(LIST_SEPARATOR) { it.constructorId },
        constructorNames = teams.joinToString(LIST_SEPARATOR) { it.name },
    )
}

fun ConstructorStandingDto.toEntityOrNull(season: Int): ConstructorStandingEntity? {
    val pos = position?.toIntOrNull() ?: return null
    return ConstructorStandingEntity(
        season = season,
        constructorId = team.constructorId,
        position = pos,
        points = points?.toDoubleOrNull() ?: 0.0,
        wins = wins?.toIntOrNull() ?: 0,
        constructorName = team.name,
        nationality = team.nationality,
    )
}

fun CircuitDto.toEntity(): CircuitEntity = CircuitEntity(
    circuitId = circuitId,
    circuitName = circuitName,
    locality = location?.locality,
    country = location?.country,
    lat = location?.lat?.toDoubleOrNull(),
    lng = location?.long?.toDoubleOrNull(),
    wikiUrl = url,
)

/**
 * Reduces a circuit's fastest-lap history to its outright record.
 *
 * Races before 2004 name a fastest-lap holder but carry no time, so they are discarded rather than
 * treated as an unbeatable zero.
 */
fun List<RaceDto>.toLapRecordOrNull(circuitId: String): LapRecordEntity? =
    mapNotNull { race ->
        val result = race.results?.firstOrNull() ?: return@mapNotNull null
        val timeText = result.fastestLap?.time?.time ?: return@mapNotNull null
        val millis = LapTime.parseMillis(timeText) ?: return@mapNotNull null
        LapRecordEntity(
            circuitId = circuitId,
            timeText = timeText,
            millis = millis,
            driverId = result.driver.driverId,
            givenName = result.driver.givenName,
            familyName = result.driver.familyName,
            driverCode = result.driver.code,
            constructorId = result.team.constructorId,
            constructorName = result.team.name,
            season = race.season.toIntOrNull() ?: 0,
            raceName = race.raceName,
        )
    }.minByOrNull { it.millis }

// endregion

// region local -> domain

fun RaceEntity.toDomain(): Race {
    val sessions = buildList {
        fp1Utc?.let { add(RaceSession(SessionType.FP1, Instant.ofEpochMilli(it))) }
        fp2Utc?.let { add(RaceSession(SessionType.FP2, Instant.ofEpochMilli(it))) }
        fp3Utc?.let { add(RaceSession(SessionType.FP3, Instant.ofEpochMilli(it))) }
        sprintQualifyingUtc?.let { add(RaceSession(SessionType.SPRINT_QUALIFYING, Instant.ofEpochMilli(it))) }
        sprintUtc?.let { add(RaceSession(SessionType.SPRINT, Instant.ofEpochMilli(it))) }
        qualifyingUtc?.let { add(RaceSession(SessionType.QUALIFYING, Instant.ofEpochMilli(it))) }
        // The grand prix itself is always listed, even when its start time is unknown.
        add(RaceSession(SessionType.RACE, raceUtc?.let(Instant::ofEpochMilli)))
    }
    return Race(
        season = season,
        round = round,
        name = raceName,
        circuitId = circuitId,
        circuitName = circuitName,
        locality = locality,
        country = country,
        wikiUrl = wikiUrl,
        // getOrElse, not getOrDefault: the fallback must stay lazy, and LocalDate.EPOCH is API 34+.
        raceDate = runCatching { LocalDate.parse(raceDate) }.getOrElse { EPOCH_DATE },
        raceStart = raceUtc?.let(Instant::ofEpochMilli),
        sessions = sessions,
    )
}

fun RaceResultEntity.toDomain(): RaceResult = RaceResult(
    position = position,
    positionText = positionText,
    driver = Driver(driverId, driverCode, driverNumber, givenName, familyName, nationality),
    team = Team(constructorId, constructorName),
    grid = grid,
    laps = laps,
    status = status,
    time = timeText,
    points = points,
    fastestLapTime = fastestLapTime,
    fastestLapRank = fastestLapRank,
)

fun QualifyingResultEntity.toDomain(): QualifyingResult = QualifyingResult(
    position = position,
    driver = Driver(driverId, driverCode, driverNumber, givenName, familyName, nationality),
    team = Team(constructorId, constructorName),
    q1 = q1,
    q2 = q2,
    q3 = q3,
)

fun DriverStandingEntity.toDomain(): DriverStanding {
    val ids = constructorIds.split(LIST_SEPARATOR).filter { it.isNotBlank() }
    val names = constructorNames.split(LIST_SEPARATOR).filter { it.isNotBlank() }
    return DriverStanding(
        position = position,
        driver = Driver(driverId, driverCode, driverNumber, givenName, familyName, nationality),
        teams = ids.mapIndexed { index, id -> Team(id, names.getOrElse(index) { id }) },
        points = points,
        wins = wins,
    )
}

fun CircuitEntity.toDomain(): Circuit = Circuit(
    id = circuitId,
    name = circuitName,
    locality = locality,
    country = country,
    wikiUrl = wikiUrl,
)

fun LapRecordEntity.toDomain(): LapRecord = LapRecord(
    circuitId = circuitId,
    time = timeText,
    millis = millis,
    driverId = driverId,
    driverName = "$givenName $familyName",
    driverCode = driverCode,
    teamId = constructorId,
    teamName = constructorName,
    season = season,
    raceName = raceName,
)

fun ConstructorStandingEntity.toDomain(): ConstructorStanding = ConstructorStanding(
    position = position,
    team = Team(constructorId, constructorName),
    nationality = nationality,
    points = points,
    wins = wins,
)

// endregion
