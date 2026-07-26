package com.praval.f1calendar

import com.praval.f1calendar.core.CountryFlags
import com.praval.f1calendar.ui.common.formatCountdown
import com.praval.f1calendar.ui.common.formatRelativeDays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TimeFormattingTest {

    private val now: Instant = Instant.parse("2026-03-08T00:00:00Z")

    private fun countdownAfter(seconds: Long) = formatCountdown(now, now.plusSeconds(seconds))

    @Test
    fun `countdown gets more precise as the session approaches`() {
        assertEquals("2d 4h", countdownAfter(2 * 86_400 + 4 * 3600))
        assertEquals("3h 12m", countdownAfter(3 * 3600 + 12 * 60))
        assertEquals("12m 30s", countdownAfter(12 * 60 + 30))
        assertEquals("45s", countdownAfter(45))
    }

    @Test
    fun `whole days drop the trailing zero hours`() {
        assertEquals("2d", countdownAfter(2 * 86_400))
    }

    @Test
    fun `a target in the past reads as now rather than going negative`() {
        assertEquals("now", formatCountdown(now, now.minusSeconds(3600)))
        assertEquals("now", formatCountdown(now, now))
    }

    @Test
    fun `relative days uses words for the near term`() {
        assertEquals("today", formatRelativeDays(now, now.plusSeconds(3600)))
        assertEquals("tomorrow", formatRelativeDays(now, now.plusSeconds(86_400 + 60)))
        assertEquals("in 5 days", formatRelativeDays(now, now.plusSeconds(5 * 86_400)))
        assertEquals("yesterday", formatRelativeDays(now, now.minusSeconds(86_400 + 60)))
        assertEquals("3 days ago", formatRelativeDays(now, now.minusSeconds(3 * 86_400 + 60)))
    }

    @Test
    fun `flags cover the informal country names the API actually returns`() {
        assertEquals("🇬🇧", CountryFlags.emojiFor("UK"))
        assertEquals("🇺🇸", CountryFlags.emojiFor("USA"))
        assertEquals("🇦🇪", CountryFlags.emojiFor("UAE"))
        assertEquals("🇲🇨", CountryFlags.emojiFor("Monaco"))
        // Case and padding vary between eras of the dataset.
        assertEquals("🇮🇹", CountryFlags.emojiFor(" italy "))
    }

    @Test
    fun `an unmapped country falls back to a chequered flag instead of blank space`() {
        assertEquals("🏁", CountryFlags.emojiFor("Atlantis"))
        assertEquals("🏁", CountryFlags.emojiFor(null))
        assertEquals("🏁", CountryFlags.emojiFor(""))
    }
}
