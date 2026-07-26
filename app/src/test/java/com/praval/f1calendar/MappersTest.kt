package com.praval.f1calendar

import com.praval.f1calendar.data.mapper.toDomain
import com.praval.f1calendar.data.mapper.toEntityOrNull
import com.praval.f1calendar.data.remote.dto.CircuitDto
import com.praval.f1calendar.data.remote.dto.ConstructorDto
import com.praval.f1calendar.data.remote.dto.DriverDto
import com.praval.f1calendar.data.remote.dto.DriverStandingDto
import com.praval.f1calendar.data.remote.dto.LocationDto
import com.praval.f1calendar.data.remote.dto.RaceDto
import com.praval.f1calendar.data.remote.dto.ResultDto
import com.praval.f1calendar.data.remote.dto.SessionDto
import com.praval.f1calendar.data.remote.dto.TimeDto
import com.praval.f1calendar.domain.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MappersTest {

    private fun raceDto(
        season: String = "2026",
        round: String = "1",
        date: String = "2026-03-08",
        time: String? = "04:00:00Z",
        firstPractice: SessionDto? = SessionDto("2026-03-06", "01:30:00Z"),
        qualifying: SessionDto? = SessionDto("2026-03-07", "05:00:00Z"),
        sprint: SessionDto? = null,
        sprintQualifying: SessionDto? = null,
        sprintShootout: SessionDto? = null,
    ) = RaceDto(
        season = season,
        round = round,
        raceName = "Australian Grand Prix",
        circuit = CircuitDto(
            circuitId = "albert_park",
            circuitName = "Albert Park Grand Prix Circuit",
            location = LocationDto("-37.8497", "144.968", "Melbourne", "Australia"),
        ),
        date = date,
        time = time,
        firstPractice = firstPractice,
        qualifying = qualifying,
        sprint = sprint,
        sprintQualifying = sprintQualifying,
        sprintShootout = sprintShootout,
    )

    @Test
    fun `splits the API's date and time fields into a single UTC instant`() {
        val entity = raceDto().toEntityOrNull()!!

        assertEquals(Instant.parse("2026-03-08T04:00:00Z").toEpochMilli(), entity.raceUtc)
        assertEquals(Instant.parse("2026-03-06T01:30:00Z").toEpochMilli(), entity.fp1Utc)
        assertEquals(Instant.parse("2026-03-07T05:00:00Z").toEpochMilli(), entity.qualifyingUtc)
    }

    @Test
    fun `a race with no published start time still keeps its date`() {
        val entity = raceDto(time = null).toEntityOrNull()!!

        assertNull(entity.raceUtc)
        assertEquals("2026-03-08", entity.raceDate)
    }

    @Test
    fun `the race session is always present even when its time is unknown`() {
        val race = raceDto(time = null).toEntityOrNull()!!.toDomain()

        val session = race.session(SessionType.RACE)
        assertNotNull(session)
        assertNull(session!!.startsAt)
    }

    @Test
    fun `2023's SprintShootout is read as sprint qualifying`() {
        val entity = raceDto(sprintShootout = SessionDto("2026-03-13", "07:30:00Z")).toEntityOrNull()!!

        assertEquals(
            Instant.parse("2026-03-13T07:30:00Z").toEpochMilli(),
            entity.sprintQualifyingUtc,
        )
    }

    @Test
    fun `modern SprintQualifying takes priority over the legacy field`() {
        val entity = raceDto(
            sprintQualifying = SessionDto("2026-03-13", "07:30:00Z"),
            sprintShootout = SessionDto("2026-03-13", "09:00:00Z"),
        ).toEntityOrNull()!!

        assertEquals(
            Instant.parse("2026-03-13T07:30:00Z").toEpochMilli(),
            entity.sprintQualifyingUtc,
        )
    }

    @Test
    fun `sessions are ordered by start time, not by declaration order`() {
        // A real sprint weekend: one practice, then sprint qualifying, the sprint, normal
        // qualifying and finally the grand prix.
        val race = raceDto(
            date = "2026-03-15",
            time = "07:00:00Z",
            firstPractice = SessionDto("2026-03-13", "03:30:00Z"),
            sprintQualifying = SessionDto("2026-03-13", "07:30:00Z"),
            sprint = SessionDto("2026-03-14", "03:00:00Z"),
            qualifying = SessionDto("2026-03-14", "07:00:00Z"),
        ).toEntityOrNull()!!.toDomain()

        assertEquals(
            listOf(
                SessionType.FP1,
                SessionType.SPRINT_QUALIFYING,
                SessionType.SPRINT,
                SessionType.QUALIFYING,
                SessionType.RACE,
            ),
            race.sessionsInOrder().map { it.type },
        )
        assertTrue(race.isSprintWeekend)
    }

    @Test
    fun `a race is completed three hours after lights out, not at lights out`() {
        val race = raceDto().toEntityOrNull()!!.toDomain()
        val start = Instant.parse("2026-03-08T04:00:00Z")

        assertFalse(race.isCompleted(start.plusSeconds(60)))
        assertFalse(race.isCompleted(start.plusSeconds(2 * 3600)))
        assertTrue(race.isCompleted(start.plusSeconds(3 * 3600 + 1)))
    }

    @Test
    fun `a malformed season or round is dropped rather than crashing the sync`() {
        assertNull(raceDto(season = "not-a-year").toEntityOrNull())
        assertNull(raceDto(round = "").toEntityOrNull())
    }

    @Test
    fun `retirements keep their letter code but still map to a sortable position`() {
        val dto = ResultDto(
            position = "18",
            positionText = "R",
            points = "0",
            driver = DriverDto(driverId = "alonso", code = "ALO", givenName = "Fernando", familyName = "Alonso"),
            team = ConstructorDto(constructorId = "aston_martin", name = "Aston Martin"),
            grid = "9",
            laps = "12",
            status = "Engine",
        )

        val result = dto.toEntityOrNull(2026, 1)!!.toDomain()

        assertEquals(18, result.position)
        assertEquals("R", result.positionText)
        assertFalse(result.isClassified)
        assertNull(result.time)
    }

    @Test
    fun `the winner's total time is kept while gaps stay as reported`() {
        val dto = ResultDto(
            position = "1",
            positionText = "1",
            points = "25",
            driver = DriverDto(driverId = "russell", code = "RUS", givenName = "George", familyName = "Russell"),
            team = ConstructorDto(constructorId = "mercedes", name = "Mercedes"),
            time = TimeDto(millis = "4986801", time = "1:23:06.801"),
        )

        val result = dto.toEntityOrNull(2026, 1)!!.toDomain()

        assertEquals("1:23:06.801", result.time)
        assertEquals(25.0, result.points, 0.0)
        assertTrue(result.isClassified)
    }

    @Test
    fun `a driver who switched teams mid-season keeps both constructors`() {
        val dto = DriverStandingDto(
            position = "7",
            points = "42",
            wins = "0",
            driver = DriverDto(driverId = "sainz", code = "SAI", givenName = "Carlos", familyName = "Sainz"),
            teams = listOf(
                ConstructorDto(constructorId = "ferrari", name = "Ferrari"),
                ConstructorDto(constructorId = "williams", name = "Williams"),
            ),
        )

        val standing = dto.toEntityOrNull(2026)!!.toDomain()

        assertEquals(listOf("Ferrari", "Williams"), standing.teams.map { it.name })
        assertEquals(listOf("ferrari", "williams"), standing.teams.map { it.id })
    }
}
