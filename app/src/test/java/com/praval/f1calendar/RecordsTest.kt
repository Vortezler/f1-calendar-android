package com.praval.f1calendar

import com.praval.f1calendar.core.LapTime
import com.praval.f1calendar.data.mapper.toLapRecordOrNull
import com.praval.f1calendar.data.remote.dto.CircuitDto
import com.praval.f1calendar.data.remote.dto.ConstructorDto
import com.praval.f1calendar.data.remote.dto.DriverDto
import com.praval.f1calendar.data.remote.dto.FastestLapDto
import com.praval.f1calendar.data.remote.dto.RaceDto
import com.praval.f1calendar.data.remote.dto.ResultDto
import com.praval.f1calendar.data.remote.dto.TimeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordsTest {

    private fun race(season: String, lapTime: String?, driver: String = "hamilton"): RaceDto =
        RaceDto(
            season = season,
            round = "1",
            raceName = "Italian Grand Prix",
            circuit = CircuitDto(circuitId = "monza", circuitName = "Monza"),
            date = "$season-09-07",
            results = listOf(
                ResultDto(
                    position = "1",
                    driver = DriverDto(driverId = driver, code = "HAM", givenName = "Lewis", familyName = "Hamilton"),
                    team = ConstructorDto(constructorId = "mercedes", name = "Mercedes"),
                    fastestLap = lapTime?.let { FastestLapDto(rank = "1", time = TimeDto(time = it)) },
                ),
            ),
        )

    @Test
    fun `lap times parse to milliseconds so they can be ordered`() {
        assertEquals(80_901L, LapTime.parseMillis("1:20.901"))
        assertEquals(103_391L, LapTime.parseMillis("1:43.391"))
        // Bare seconds appear for very short laps.
        assertEquals(55_287L, LapTime.parseMillis("55.287"))
        assertEquals(60_000L, LapTime.parseMillis("1:00.000"))
    }

    @Test
    fun `unparseable or absent times are rejected rather than treated as zero`() {
        assertNull(LapTime.parseMillis(null))
        assertNull(LapTime.parseMillis(""))
        assertNull(LapTime.parseMillis("   "))
        assertNull(LapTime.parseMillis("no time"))
    }

    @Test
    fun `formatting round-trips a parsed lap time`() {
        assertEquals("1:20.901", LapTime.format(80_901L))
        assertEquals("55.287", LapTime.format(55_287L))
    }

    @Test
    fun `gaps are shown against the reference lap and omitted for the reference itself`() {
        assertEquals("+0.842", LapTime.formatGap(81_743L, 80_901L))
        assertNull(LapTime.formatGap(80_901L, 80_901L))
    }

    @Test
    fun `the record is the fastest lap across a circuit's whole history`() {
        val record = listOf(
            race("2004", "1:21.046"),
            race("2019", "1:21.779"),
            race("2025", "1:20.901"),
            race("2008", "1:28.047"),
        ).toLapRecordOrNull("monza")

        assertEquals("1:20.901", record?.timeText)
        assertEquals(80_901L, record?.millis)
        assertEquals(2025, record?.season)
    }

    @Test
    fun `pre-2004 races name a driver but carry no time, and must not win`() {
        // Ergast returns these with a FastestLap holder and no Time; treating them as zero would
        // make 1958 the permanent record at half the circuits on the calendar.
        val record = listOf(
            race("1958", null),
            race("1959", null),
            race("2004", "1:21.046"),
        ).toLapRecordOrNull("monza")

        assertEquals("1:21.046", record?.timeText)
        assertEquals(2004, record?.season)
    }

    @Test
    fun `a circuit with no timed laps at all has no record`() {
        val record = listOf(race("1958", null), race("1959", null)).toLapRecordOrNull("monza")
        assertNull(record)
    }

    @Test
    fun `an empty history has no record`() {
        assertNull(emptyList<RaceDto>().toLapRecordOrNull("monza"))
    }
}
