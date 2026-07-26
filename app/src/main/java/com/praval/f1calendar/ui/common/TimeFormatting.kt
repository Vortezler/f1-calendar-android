package com.praval.f1calendar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * All API timestamps are UTC; everything user-facing is rendered in the device timezone unless the
 * user has explicitly asked for UTC in settings.
 */
fun displayZone(useUtc: Boolean): ZoneId = if (useUtc) ZoneOffset.UTC else ZoneId.systemDefault()

/*
 * Formatters are cached per (pattern, locale) rather than held in a `val`: a formatter built once
 * at class-init would keep using whatever locale was active at app start, and would then print
 * month and weekday names in the old language after an in-flight locale change.
 *
 * Times stay on a 24-hour clock regardless of locale — that's the convention every F1 timing sheet
 * and broadcast uses, and it avoids AM/PM ambiguity around midnight sessions in Asia-Pacific.
 *
 * Only ever touched from the UI thread, so the cache needs no synchronisation.
 */
private const val PATTERN_DAY_MONTH = "EEE d MMM"
private const val PATTERN_TIME = "HH:mm"
private const val PATTERN_DAY_MONTH_YEAR = "d MMM yyyy"

private val formatterCache = HashMap<String, DateTimeFormatter>()

private fun formatter(pattern: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    return formatterCache.getOrPut("$pattern@${locale.toLanguageTag()}") {
        DateTimeFormatter.ofPattern(pattern, locale)
    }
}

fun formatDate(instant: Instant, zone: ZoneId): String =
    formatter(PATTERN_DAY_MONTH).format(instant.atZone(zone))

fun formatTime(instant: Instant, zone: ZoneId): String =
    formatter(PATTERN_TIME).format(instant.atZone(zone))

fun formatDateTime(instant: Instant, zone: ZoneId): String =
    "${formatDate(instant, zone)}, ${formatTime(instant, zone)}"

fun formatFullDate(instant: Instant, zone: ZoneId): String =
    formatter(PATTERN_DAY_MONTH_YEAR).format(instant.atZone(zone))

fun formatDateRange(start: Instant, end: Instant, zone: ZoneId): String {
    val startDay = formatDate(start, zone)
    val endDay = formatDate(end, zone)
    return if (startDay == endDay) startDay else "$startDay – $endDay"
}

/**
 * Coarse at long range and precise up close: nobody needs seconds three weeks out, but they do in
 * the final minute before lights out.
 */
fun formatCountdown(now: Instant, target: Instant): String {
    val duration = Duration.between(now, target)
    if (duration.isNegative || duration.isZero) return "now"

    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    val seconds = duration.seconds % 60

    return when {
        days >= 1 -> if (hours > 0) "${days}d ${hours}h" else "${days}d"
        duration.toHours() >= 1 -> "${duration.toHours()}h ${minutes}m"
        duration.toMinutes() >= 1 -> "${duration.toMinutes()}m ${seconds}s"
        else -> "${duration.seconds}s"
    }
}

/** "in 3 days" / "2 days ago" style label for calendar rows. */
fun formatRelativeDays(now: Instant, target: Instant): String {
    val days = Duration.between(now, target).toDays()
    return when {
        days > 1 -> "in $days days"
        days == 1L -> "tomorrow"
        days == 0L -> "today"
        days == -1L -> "yesterday"
        else -> "${-days} days ago"
    }
}

/**
 * A clock that recomposes on a tick. The interval is a parameter so long-range countdowns can tick
 * once a minute instead of driving a recomposition every second.
 */
@Composable
fun rememberTickingNow(intervalMillis: Long = 1_000L): State<Instant> {
    val state = remember { mutableStateOf(Instant.now()) }
    var current by state
    LaunchedEffect(intervalMillis) {
        while (true) {
            current = Instant.now()
            delay(intervalMillis)
        }
    }
    return state
}
