package com.praval.f1calendar

import com.praval.f1calendar.data.live.dto.gapText
import com.praval.f1calendar.domain.model.DefaultAlarmRules
import com.praval.f1calendar.domain.model.LiveSession
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.ui.live.formatLapTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveTest {

    private fun session(
        start: String = "2026-07-26T13:00:00Z",
        end: String? = "2026-07-26T15:00:00Z",
        type: String = "Race",
    ) = LiveSession(
        sessionKey = 11342,
        name = "Race",
        type = type,
        location = "Budapest",
        countryName = "Hungary",
        start = Instant.parse(start),
        end = end?.let(Instant::parse),
    )

    @Test
    fun `a session is live between its start and end`() {
        val s = session()
        assertTrue(s.isLive(Instant.parse("2026-07-26T13:00:00Z")))
        assertTrue(s.isLive(Instant.parse("2026-07-26T14:30:00Z")))
        assertFalse(s.isLive(Instant.parse("2026-07-26T12:59:00Z")))
    }

    @Test
    fun `an overrunning session stays live through the grace period`() {
        val s = session()
        // Red flags and delayed starts routinely push a race past its scheduled end.
        assertTrue(s.isLive(Instant.parse("2026-07-26T15:15:00Z")))
        assertFalse(s.isLive(Instant.parse("2026-07-26T15:25:00Z")))
    }

    @Test
    fun `the tab appears shortly before lights out but not hours early`() {
        val s = session()
        assertTrue(s.isImminent(Instant.parse("2026-07-26T12:50:00Z")))
        assertFalse(s.isImminent(Instant.parse("2026-07-26T12:30:00Z")))
        // Once running it is live, not imminent.
        assertFalse(s.isImminent(Instant.parse("2026-07-26T13:30:00Z")))
    }

    @Test
    fun `a session with no end time still closes out`() {
        val s = session(end = null)
        assertTrue(s.isLive(Instant.parse("2026-07-26T14:00:00Z")))
        assertFalse(s.isLive(Instant.parse("2026-07-26T15:30:00Z")))
    }

    @Test
    fun `race detection is case insensitive`() {
        assertTrue(session(type = "Race").isRace)
        assertTrue(session(type = "race").isRace)
        assertFalse(session(type = "Qualifying").isRace)
    }

    @Test
    fun `numeric gaps are formatted, lapped markers pass through`() {
        assertEquals("+12.480", JsonPrimitive(12.48).gapText())
        assertEquals("+1.007", JsonPrimitive(1.007).gapText())
        // OpenF1 switches this field to a string once a driver is lapped.
        assertEquals("+1 LAP", JsonPrimitive("+1 LAP").gapText())
    }

    @Test
    fun `the leader's own zero gap is not rendered as a gap`() {
        assertNull(JsonPrimitive(0.0).gapText())
        assertNull(JsonNull.gapText())
        assertNull(null.gapText())
    }

    @Test
    fun `lap times use F1's minute-second convention`() {
        assertEquals("1:22.670", formatLapTime(82.670))
        assertEquals("1:43.391", formatLapTime(103.391))
        // Sector-length times stay in plain seconds.
        assertEquals("55.287", formatLapTime(55.287))
    }

    @Test
    fun `default alarm rules arm the sessions that decide something`() {
        assertTrue(DefaultAlarmRules.forType(SessionType.RACE).enabled)
        assertTrue(DefaultAlarmRules.forType(SessionType.QUALIFYING).enabled)
        assertTrue(DefaultAlarmRules.forType(SessionType.SPRINT).enabled)
        // Free practice is opt-in.
        assertFalse(DefaultAlarmRules.forType(SessionType.FP1).enabled)
        assertFalse(DefaultAlarmRules.forType(SessionType.FP3).enabled)
    }

    @Test
    fun `the grand prix gets more notice than a qualifying session`() {
        val race = DefaultAlarmRules.forType(SessionType.RACE).leadMinutes
        val quali = DefaultAlarmRules.forType(SessionType.QUALIFYING).leadMinutes
        assertTrue(race > quali)
        assertTrue(DefaultAlarmRules.LEAD_TIME_OPTIONS.containsAll(listOf(race, quali)))
    }

    @Test
    fun `every session type has a default rule`() {
        assertEquals(SessionType.entries.size, DefaultAlarmRules.all().size)
    }
}
