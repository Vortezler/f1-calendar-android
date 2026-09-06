package com.praval.f1calendar.data.live

import com.praval.f1calendar.core.Res
import com.praval.f1calendar.core.TeamColors
import com.praval.f1calendar.data.live.dto.OpenF1DriverDto
import com.praval.f1calendar.data.live.dto.OpenF1SessionDto
import com.praval.f1calendar.data.live.dto.OpenF1SessionResultDto
import com.praval.f1calendar.data.live.dto.bestLapSeconds
import com.praval.f1calendar.data.live.dto.gapText
import com.praval.f1calendar.data.remote.apiCall
import com.praval.f1calendar.di.AppScope
import com.praval.f1calendar.domain.model.Driver
import com.praval.f1calendar.domain.model.GridSlot
import com.praval.f1calendar.domain.model.LiveSession
import com.praval.f1calendar.domain.model.LiveStanding
import com.praval.f1calendar.domain.model.RaceResult
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.domain.model.Team
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live timing, polled from OpenF1.
 *
 * Nothing here is cached to Room: it is only meaningful while a session is running, and it is
 * superseded within seconds.
 *
 * Every OpenF1 endpoint returns *all* matching history rather than a current snapshot, so the
 * repository keeps a running picture in memory and asks only for what has changed since the last
 * poll. Fetching a whole grand prix's intervals on every tick would be megabytes per request.
 */
@Singleton
class LiveRepository @Inject constructor(
    private val api: OpenF1Api,
    @param:AppScope private val scope: CoroutineScope,
) {

    /**
     * Every session of one name in one season, so repeatedly asking "which OpenF1 session is this
     * round?" costs a single request per season. A season's session list never changes once run.
     */
    private val sessionCache = ConcurrentHashMap<Pair<Int, String>, List<OpenF1SessionDto>>()

    /**
     * The session OpenF1 currently considers latest. Shared, because both the navigation bar (to
     * decide whether to show the Live tab) and the Live screen itself need it.
     */
    val session: StateFlow<Res<LiveSession?>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(apiCall { api.sessions().firstOrNull()?.toDomain() })
            delay(SESSION_POLL.toMillis())
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(15_000), Res.Loading)

    fun standings(session: LiveSession): Flow<Res<List<LiveStanding>>> = flow {
        val drivers = HashMap<Int, OpenF1DriverDto>()
        val position = HashMap<Int, Int>()
        val positionAt = HashMap<Int, Instant>()
        val gapToLeader = HashMap<Int, String?>()
        val interval = HashMap<Int, String?>()
        val gapAt = HashMap<Int, Instant>()
        val bestLap = HashMap<Int, Double>()
        val lastLap = HashMap<Int, Double>()
        val lastLapAt = HashMap<Int, Instant>()

        // Null means "no lower bound" — used once, to establish a baseline for the whole session.
        var positionsSince: Instant? = null
        var lapsSince: Instant? = null

        while (currentCoroutineContext().isActive) {
            val outcome = apiCall {
                if (drivers.isEmpty()) {
                    api.drivers(session.sessionKey).forEach { drivers[it.driverNumber] = it }
                }

                api.positions(session.sessionKey, positionsSince?.let(::openF1Timestamp))
                    .forEach { dto ->
                        val at = parseInstant(dto.date) ?: return@forEach
                        if (positionAt[dto.driverNumber]?.isAfter(at) != true) {
                            positionAt[dto.driverNumber] = at
                            position[dto.driverNumber] = dto.position
                        }
                    }
                positionsSince = Instant.now().minus(DELTA_OVERLAP)

                if (session.isRace) {
                    // Intervals update for every car every few seconds, so a short window still
                    // covers the whole field while keeping the response small.
                    val since = openF1Timestamp(Instant.now().minus(INTERVAL_WINDOW))
                    api.intervals(session.sessionKey, since).forEach { dto ->
                        val at = parseInstant(dto.date) ?: return@forEach
                        if (gapAt[dto.driverNumber]?.isAfter(at) != true) {
                            gapAt[dto.driverNumber] = at
                            gapToLeader[dto.driverNumber] = dto.gapToLeader.gapText()
                            interval[dto.driverNumber] = dto.interval.gapText()
                        }
                    }
                } else {
                    api.laps(session.sessionKey, lapsSince?.let(::openF1Timestamp)).forEach { dto ->
                        val duration = dto.lapDuration ?: return@forEach
                        // An out-lap is not a representative time and never counts for a best.
                        if (dto.isPitOutLap == true) return@forEach
                        val currentBest = bestLap[dto.driverNumber]
                        if (currentBest == null || duration < currentBest) {
                            bestLap[dto.driverNumber] = duration
                        }
                        val at = parseInstant(dto.dateStart) ?: return@forEach
                        if (lastLapAt[dto.driverNumber]?.isAfter(at) != true) {
                            lastLapAt[dto.driverNumber] = at
                            lastLap[dto.driverNumber] = duration
                        }
                    }
                    lapsSince = Instant.now().minus(LAP_OVERLAP)
                }

                val rows = drivers.values.map { driver ->
                    LiveStanding(
                        position = position[driver.driverNumber],
                        driverNumber = driver.driverNumber,
                        acronym = driver.acronym ?: driver.driverNumber.toString(),
                        fullName = driver.fullName ?: "#${driver.driverNumber}",
                        teamName = driver.teamName,
                        teamColour = parseTeamColour(driver.teamColour),
                        gapToLeader = gapToLeader[driver.driverNumber],
                        interval = interval[driver.driverNumber],
                        bestLapSeconds = bestLap[driver.driverNumber],
                        lastLapSeconds = lastLap[driver.driverNumber],
                    )
                }
                sortForSession(rows, session.isRace)
            }

            emit(outcome)
            // Back off when the API is unhappy rather than hammering it at the live cadence.
            delay(if (outcome is Res.Error) ERROR_POLL.toMillis() else LIVE_POLL.toMillis())
        }
    }

    /**
     * A race is ordered by track position. Practice and qualifying are classified by best lap, so
     * they're ranked on the lap time actually shown rather than on OpenF1's position field, which
     * can disagree with it mid-session.
     */
    private fun sortForSession(rows: List<LiveStanding>, isRace: Boolean): List<LiveStanding> =
        if (isRace) {
            rows.sortedBy { it.position ?: Int.MAX_VALUE }
        } else {
            rows.sortedWith(
                compareBy(
                    { it.bestLapSeconds ?: Double.MAX_VALUE },
                    { it.position ?: Int.MAX_VALUE },
                ),
            ).mapIndexed { index, row ->
                if (row.bestLapSeconds != null) row.copy(position = index + 1) else row.copy(position = null)
            }
        }

    // region post-session results
    /*
     * Jolpica publishes a classification hours after the flag — sometimes the morning after — which
     * is far too late to notify anyone that a session is over. OpenF1 has the same order within
     * seconds, so it is read first and Jolpica is left to be the authoritative copy that lands later.
     */

    /**
     * The classification for one calendar session, mapped onto the app's own result type so it
     * renders through the same table as a Jolpica result.
     *
     * Returns an empty list when OpenF1 has nothing yet, which callers should treat as "not
     * published" rather than as a failure.
     */
    suspend fun classification(
        season: Int,
        type: SessionType,
        startsAt: Instant,
    ): Res<List<RaceResult>> = apiCall {
        val sessionKey = sessionKeyFor(season, type, startsAt) ?: return@apiCall emptyList()
        val rows = api.sessionResult(sessionKey).sortedBy { it.position ?: Int.MAX_VALUE }
        if (rows.isEmpty()) return@apiCall emptyList()

        val drivers = driversFor(sessionKey)
        val isRace = type == SessionType.RACE || type == SessionType.SPRINT
        rows.map { it.toRaceResult(drivers[it.driverNumber], isRace) }
    }

    /**
     * The grid for a race, which is published against the qualifying session that set it and
     * already has every post-qualifying penalty applied.
     *
     * A grid is only worth reading next to where each driver qualified. Pass [qualifyingByCode]
     * (keyed on the three-letter code) when the caller already holds a qualifying classification —
     * OpenF1 rate-limits bursts, and asking it for an order the app already has is the request most
     * likely to be the one refused.
     */
    suspend fun startingGrid(
        season: Int,
        qualifyingStart: Instant,
        qualifyingByCode: Map<String, Int> = emptyMap(),
    ): Res<List<GridSlot>> = apiCall {
        val sessionKey = sessionKeyFor(season, SessionType.QUALIFYING, qualifyingStart)
            ?: return@apiCall emptyList()
        val grid = api.startingGrid(sessionKey)
        if (grid.isEmpty()) return@apiCall emptyList()

        val drivers = driversFor(sessionKey)
        // Same session key, so the order the grid deviates from comes from the same place.
        val qualifyingByNumber = if (qualifyingByCode.isNotEmpty()) {
            emptyMap()
        } else {
            runCatching { api.sessionResult(sessionKey) }
                .getOrDefault(emptyList())
                .mapNotNull { row -> row.position?.let { row.driverNumber to it } }
                .toMap()
        }

        grid.sortedBy { it.position }.map { slot ->
            val driver = drivers[slot.driverNumber]
            val code = driver?.acronym
            GridSlot(
                position = slot.position,
                driverName = driver.displayName(slot.driverNumber),
                driverShort = code ?: slot.driverNumber.toString(),
                teamName = driver?.teamName,
                teamId = TeamColors.constructorIdForTeamName(driver?.teamName),
                qualifyingPosition = qualifyingByCode[code?.uppercase()]
                    ?: qualifyingByNumber[slot.driverNumber],
            )
        }
    }

    /**
     * OpenF1 keys everything on its own session ids, so a calendar round is matched by when its
     * session starts. Sessions of the same name are a week apart, so a wide tolerance still can't
     * match the wrong weekend, and it survives a session being moved by a few hours.
     */
    private suspend fun sessionKeyFor(season: Int, type: SessionType, startsAt: Instant): Int? {
        for (name in type.openF1Names()) {
            val sessions = sessionCache.getOrPut(season to name) {
                runCatching { api.sessionsInYear(season, name) }.getOrDefault(emptyList())
            }
            val match = sessions
                .mapNotNull { session -> parseInstant(session.dateStart)?.let { session to it } }
                .minByOrNull { (_, start) -> Duration.between(start, startsAt).abs() }
                ?.takeIf { (_, start) -> Duration.between(start, startsAt).abs() <= MATCH_TOLERANCE }
            if (match != null) return match.first.sessionKey
        }
        return null
    }

    private suspend fun driversFor(sessionKey: Int): Map<Int, OpenF1DriverDto> =
        runCatching { api.drivers(sessionKey) }.getOrDefault(emptyList())
            .associateBy { it.driverNumber }

    // endregion

    private companion object {
        val SESSION_POLL: Duration = Duration.ofSeconds(60)
        val LIVE_POLL: Duration = Duration.ofSeconds(8)
        val ERROR_POLL: Duration = Duration.ofSeconds(30)

        /** Re-ask for a little before the last poll so nothing is missed at the boundary. */
        val DELTA_OVERLAP: Duration = Duration.ofSeconds(45)
        val INTERVAL_WINDOW: Duration = Duration.ofMinutes(2)
        val LAP_OVERLAP: Duration = Duration.ofMinutes(5)

        /** How far a calendar start time may sit from OpenF1's before they're different sessions. */
        val MATCH_TOLERANCE: Duration = Duration.ofHours(12)
    }
}

/**
 * What OpenF1 calls each session. Sprint qualifying was named "Sprint Shootout" for 2023 only, so
 * both are tried.
 */
private fun SessionType.openF1Names(): List<String> = when (this) {
    SessionType.FP1 -> listOf("Practice 1")
    SessionType.FP2 -> listOf("Practice 2")
    SessionType.FP3 -> listOf("Practice 3")
    SessionType.SPRINT_QUALIFYING -> listOf("Sprint Qualifying", "Sprint Shootout")
    SessionType.SPRINT -> listOf("Sprint")
    SessionType.QUALIFYING -> listOf("Qualifying")
    SessionType.RACE -> listOf("Race")
}

private fun OpenF1DriverDto?.displayName(driverNumber: Int): String = when {
    this == null -> "#$driverNumber"
    firstName != null && lastName != null -> "$firstName $lastName"
    else -> fullName ?: "#$driverNumber"
}

/**
 * Maps a live classification onto the Jolpica-shaped result the rest of the app already renders.
 *
 * Driver ids are synthesised from the car number, which is enough to key a list — these rows are
 * never written to Room, where they would collide with the official ones.
 */
private fun OpenF1SessionResultDto.toRaceResult(
    driver: OpenF1DriverDto?,
    isRace: Boolean,
): RaceResult {
    val name = driver.displayName(driverNumber)
    return RaceResult(
        position = position ?: 0,
        // Mirrors Ergast's markers, so anything unclassified reads as such in the table too.
        positionText = when {
            dsq -> "D"
            dns -> "W"
            dnf -> "R"
            else -> position?.toString() ?: "-"
        },
        driver = Driver(
            id = "openf1-$driverNumber",
            code = driver?.acronym,
            permanentNumber = driverNumber.toString(),
            givenName = driver?.firstName ?: name.substringBeforeLast(' ', ""),
            familyName = driver?.lastName ?: name.substringAfterLast(' '),
            nationality = null,
        ),
        team = Team(
            id = TeamColors.constructorIdForTeamName(driver?.teamName).orEmpty(),
            name = driver?.teamName.orEmpty(),
        ),
        grid = null,
        laps = numberOfLaps,
        status = when {
            dsq -> "Disqualified"
            dns -> "Did not start"
            dnf -> "Retired"
            else -> null
        },
        time = when {
            !isRace -> duration.bestLapSeconds()?.let(::formatLapTime)
            position == 1 -> duration.bestLapSeconds()?.let(::formatRaceDuration)
            else -> gapToLeader.gapText()
        },
        points = points ?: 0.0,
        fastestLapTime = null,
        fastestLapRank = null,
    )
}

/** Seconds to the lap-time form the timing screens use, e.g. 81.786 -> "1:21.786". */
private fun formatLapTime(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    val rest = seconds - minutes * 60
    return String.format(Locale.US, "%d:%06.3f", minutes, rest)
}

/** Seconds to a total race time, e.g. 6675.281 -> "1:51:15.281". */
private fun formatRaceDuration(seconds: Double): String {
    val hours = (seconds / 3600).toInt()
    val minutes = ((seconds - hours * 3600) / 60).toInt()
    val rest = seconds - hours * 3600 - minutes * 60
    return String.format(Locale.US, "%d:%02d:%06.3f", hours, minutes, rest)
}

private fun OpenF1SessionDto.toDomain(): LiveSession = LiveSession(
    sessionKey = sessionKey,
    name = sessionName ?: sessionType ?: "Session",
    type = sessionType ?: "",
    location = location ?: circuitShortName,
    countryName = countryName,
    start = parseInstant(dateStart),
    end = parseInstant(dateEnd),
)

/** OpenF1 stamps everything as an offset date-time, e.g. `2026-07-26T13:30:04.116000+00:00`. */
private fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(value) }.getOrNull()
}

/** OpenF1's range filters expect a naive UTC timestamp with no offset suffix. */
private fun openF1Timestamp(instant: Instant): String =
    instant.atOffset(ZoneOffset.UTC).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

/** OpenF1 gives six hex digits with no alpha and no leading '#'. */
private fun parseTeamColour(hex: String?): Long {
    val cleaned = hex?.removePrefix("#")?.trim().orEmpty()
    if (cleaned.length != 6) return 0xFF9E9E9E
    return cleaned.toLongOrNull(16)?.let { 0xFF000000L or it } ?: 0xFF9E9E9E
}
