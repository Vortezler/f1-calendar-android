package com.praval.f1calendar.data.live

import com.praval.f1calendar.core.Res
import com.praval.f1calendar.data.live.dto.OpenF1DriverDto
import com.praval.f1calendar.data.live.dto.OpenF1SessionDto
import com.praval.f1calendar.data.live.dto.gapText
import com.praval.f1calendar.data.remote.apiCall
import com.praval.f1calendar.di.AppScope
import com.praval.f1calendar.domain.model.LiveSession
import com.praval.f1calendar.domain.model.LiveStanding
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

    private companion object {
        val SESSION_POLL: Duration = Duration.ofSeconds(60)
        val LIVE_POLL: Duration = Duration.ofSeconds(8)
        val ERROR_POLL: Duration = Duration.ofSeconds(30)

        /** Re-ask for a little before the last poll so nothing is missed at the boundary. */
        val DELTA_OVERLAP: Duration = Duration.ofSeconds(45)
        val INTERVAL_WINDOW: Duration = Duration.ofMinutes(2)
        val LAP_OVERLAP: Duration = Duration.ofMinutes(5)
    }
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
