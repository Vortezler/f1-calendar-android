package com.praval.f1calendar.core

import java.util.Locale

/**
 * Lap times arrive as display strings ("1:20.901"), which sort lexicographically in the wrong order
 * once a lap crosses a minute boundary. Converting to milliseconds is what makes "which of these is
 * the record" answerable.
 */
object LapTime {

    /** Accepts "m:ss.SSS" and the bare "ss.SSS" used for very short laps. */
    fun parseMillis(text: String?): Long? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":")
        return runCatching {
            when (parts.size) {
                1 -> secondsToMillis(parts[0])
                2 -> parts[0].trim().toLong() * 60_000L + secondsToMillis(parts[1])
                // Nothing in F1 runs to hours, but be defensive rather than wrong.
                3 -> parts[0].trim().toLong() * 3_600_000L +
                    parts[1].trim().toLong() * 60_000L +
                    secondsToMillis(parts[2])
                else -> null
            }
        }.getOrNull()?.takeIf { it > 0 }
    }

    private fun secondsToMillis(value: String): Long =
        Math.round(value.trim().toDouble() * 1000.0)

    fun format(millis: Long): String {
        val minutes = millis / 60_000
        val seconds = (millis % 60_000) / 1000.0
        return if (minutes > 0) {
            String.format(Locale.US, "%d:%06.3f", minutes, seconds)
        } else {
            String.format(Locale.US, "%.3f", seconds)
        }
    }

    /** Gap to the reference lap, e.g. "+0.842". */
    fun formatGap(millis: Long, referenceMillis: Long): String? {
        val delta = millis - referenceMillis
        if (delta <= 0) return null
        return "+" + String.format(Locale.US, "%.3f", delta / 1000.0)
    }
}
