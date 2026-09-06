package com.praval.f1calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.praval.f1calendar.domain.model.DriverStanding
import com.praval.f1calendar.domain.model.GridSlot
import com.praval.f1calendar.domain.model.QualifyingResult
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.RaceResult
import com.praval.f1calendar.domain.model.RaceSession
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.ui.common.PositionBadge
import com.praval.f1calendar.ui.common.SectionCard
import com.praval.f1calendar.ui.common.SectionHeader
import com.praval.f1calendar.ui.common.TeamAccent
import com.praval.f1calendar.ui.common.formatCountdown
import com.praval.f1calendar.ui.common.formatDate
import com.praval.f1calendar.ui.common.formatDateTime
import com.praval.f1calendar.ui.common.formatLeadTime
import com.praval.f1calendar.ui.common.formatPoints
import java.time.Instant
import java.time.ZoneId

/*
 * The selected round's full picture, as one scrolling page: where and when, every session with its
 * alarm, the classifications, and the championship. Written as LazyListScope extensions so each
 * block is only composed once it scrolls into view.
 */

fun LazyListScope.raceHeaderItem(
    race: Race,
    now: Instant,
    zone: ZoneId,
    allAlarmsOn: Boolean,
    onToggleAll: (Boolean) -> Unit,
) {
    item(key = "header-${race.round}") {
        val nextSession = race.sessionsInOrder()
            .firstOrNull { it.startsAt != null && it.startsAt.isAfter(now) }
        val completed = race.isCompleted(now)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(race.flag, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = race.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Round ${race.round} · ${race.season}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (race.sessions.any { it.startsAt != null }) {
                        IconButton(onClick = { onToggleAll(!allAlarmsOn) }) {
                            Icon(
                                imageVector = if (allAlarmsOn) {
                                    Icons.Filled.Notifications
                                } else {
                                    Icons.Outlined.Notifications
                                },
                                contentDescription = if (allAlarmsOn) {
                                    "Turn off every alarm for this weekend"
                                } else {
                                    "Alarm me for every session this weekend"
                                },
                                tint = if (allAlarmsOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = race.circuitName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = listOfNotNull(race.locality, race.country).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (race.isSprintWeekend) {
                        Badge("SPRINT")
                        Spacer(Modifier.width(6.dp))
                    }
                    Badge(if (completed) "COMPLETED" else "UPCOMING", muted = completed)
                }

                Spacer(Modifier.height(12.dp))
                if (nextSession?.startsAt != null) {
                    Text(
                        text = "${nextSession.type.label} in ${formatCountdown(now, nextSession.startsAt)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatDateTime(nextSession.startsAt, zone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = race.raceStart?.let { formatDateTime(it, zone) }
                            ?: formatDate(race.raceDate.atStartOfDay(zone).toInstant(), zone),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, muted: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (muted) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

fun LazyListScope.sessionsSection(
    race: Race,
    now: Instant,
    zone: ZoneId,
    exactAlarmsAvailable: Boolean,
    alarmOn: (SessionType) -> Boolean,
    leadMinutes: (SessionType) -> Int,
    isOverridden: (SessionType) -> Boolean,
    onToggle: (SessionType, Boolean) -> Unit,
) {
    val sessions = race.sessionsInOrder()

    item(key = "sessions-header") { SectionHeader("Session times") }

    if (!exactAlarmsAvailable && sessions.any { alarmOn(it.type) }) {
        item(key = "exact-alarm-note") {
            Text(
                text = "Exact alarms are off for this app, so alarms may arrive a few minutes " +
                    "late. You can allow them in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    item(key = "sessions-card") {
        SectionCard {
            sessions.forEachIndexed { index, session ->
                SessionRow(
                    session = session,
                    now = now,
                    zone = zone,
                    alarmOn = alarmOn(session.type),
                    leadMinutes = leadMinutes(session.type),
                    overridden = isOverridden(session.type),
                    onToggle = { onToggle(session.type, it) },
                )
                if (index != sessions.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: RaceSession,
    now: Instant,
    zone: ZoneId,
    alarmOn: Boolean,
    leadMinutes: Int,
    overridden: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val start = session.startsAt
    val upcoming = start != null && start.isAfter(now)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = session.type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = start?.let { formatDateTime(it, zone) } ?: "Start time to be confirmed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (upcoming && start != null) {
                Text(
                    text = "in ${formatCountdown(now, start)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (upcoming && alarmOn) {
                Text(
                    text = buildString {
                        append("Alarm ${formatLeadTime(leadMinutes)} before")
                        // Make it obvious this weekend deviates from the standing rule.
                        if (overridden) append(" · just this weekend")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // An alarm can only be set for something that hasn't happened yet.
        if (upcoming) {
            Switch(checked = alarmOn, onCheckedChange = onToggle)
        }
    }
}

fun LazyListScope.resultsSection(
    results: List<RaceResult>,
    race: Race,
    now: Instant,
    provisional: Boolean = false,
) {
    item(key = "results-header") {
        SectionHeader("Race result", trailing = if (provisional) "PROVISIONAL" else null)
    }

    if (provisional) {
        item(key = "results-provisional") {
            SectionPlaceholder(
                "Unofficial, from live timing. The official classification replaces it once it's " +
                    "published.",
            )
        }
    }

    if (results.isEmpty()) {
        item(key = "results-empty") {
            SectionPlaceholder(
                if (race.isCompleted(now)) {
                    "No classification published yet. Pull down to check again."
                } else {
                    "Finishing positions appear here once the chequered flag drops."
                },
            )
        }
        return
    }

    item(key = "results-card") {
        SectionCard {
            TableHeader(listOf("POS" to 28, "DRIVER" to 0, "TIME / STATUS" to 0, "PTS" to 30))
            results.forEachIndexed { index, result ->
                ResultRow(result)
                if (index != results.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: RaceResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PositionBadge(
            // Retirements report "R"/"D"/"W" rather than a finishing position.
            text = if (result.isClassified) result.position.toString() else result.positionText,
            highlighted = result.isClassified && result.position <= 3,
        )
        Spacer(Modifier.width(8.dp))
        TeamAccent(result.team.id)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.driver.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.setFastestLap) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "FL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                text = result.team.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = result.time ?: result.status.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            result.grid?.let {
                Text(
                    text = "from P$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = result.points.formatPoints(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}

fun LazyListScope.qualifyingSection(
    results: List<QualifyingResult>,
    grid: List<GridSlot>,
    showGrid: Boolean,
    gridLoading: Boolean,
    race: Race,
    now: Instant,
    onShowGrid: (Boolean) -> Unit,
) {
    item(key = "qualifying-header") {
        SectionHeader(if (showGrid) "Starting grid" else "Qualifying")
    }

    if (results.isEmpty() && grid.isEmpty()) {
        val qualifyingStart = race.session(SessionType.QUALIFYING)?.startsAt
        item(key = "qualifying-empty") {
            SectionPlaceholder(
                if (qualifyingStart != null && qualifyingStart.isAfter(now)) {
                    "Grid positions appear here once the session is over."
                } else {
                    "No qualifying times published for this round."
                },
            )
        }
        return
    }

    // Penalties are applied after qualifying, so the order the times were set in is not the order
    // the cars line up in. Both are worth reading, so neither is hidden behind the other.
    item(key = "qualifying-switch") {
        GridSwitch(showGrid = showGrid, onSelect = onShowGrid)
    }

    if (!showGrid) {
        item(key = "qualifying-card") {
            SectionCard {
                results.forEachIndexed { index, result ->
                    QualifyingRow(result)
                    if (index != results.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        return
    }

    if (grid.isEmpty()) {
        item(key = "grid-empty") {
            SectionPlaceholder(
                if (gridLoading) {
                    "Checking the grid…"
                } else {
                    "The grid for this round hasn't been published yet."
                },
            )
        }
        return
    }

    item(key = "grid-card") {
        SectionCard {
            TableHeader(listOf("POS" to 28, "DRIVER" to 0, "FROM QUALI" to 76))
            grid.forEachIndexed { index, slot ->
                GridRow(slot)
                if (index != grid.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** Two-way switch between the qualifying classification and the grid it turned into. */
@Composable
private fun GridSwitch(showGrid: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(3.dp),
    ) {
        SwitchHalf("Qualifying", selected = !showGrid) { onSelect(false) }
        SwitchHalf("Starting grid", selected = showGrid) { onSelect(true) }
    }
}

@Composable
private fun RowScope.SwitchHalf(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun GridRow(slot: GridSlot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PositionBadge(
            text = if (slot.startsFromPitLane) "PL" else slot.position.toString(),
            highlighted = !slot.startsFromPitLane && slot.position <= 3,
        )
        Spacer(Modifier.width(8.dp))
        TeamAccent(slot.teamId, height = 36)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = slot.driverName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = slot.teamName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        GridMovement(slot)
    }
}

/** How far a penalty (or someone else's) moved this driver from where they qualified. */
@Composable
private fun GridMovement(slot: GridSlot) {
    val movement = slot.movement
    val qualified = slot.qualifyingPosition
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.width(76.dp),
    ) {
        Text(
            text = when {
                qualified == null -> "—"
                movement == null || movement == 0 -> "P$qualified"
                movement > 0 -> "▲ $movement"
                else -> "▼ ${-movement}"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (movement != null && movement != 0) FontWeight.Bold else FontWeight.Normal,
            color = when {
                movement == null || movement == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                movement > 0 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            },
        )
        if (qualified != null && movement != null && movement != 0) {
            Text(
                text = "qualified P$qualified",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QualifyingRow(result: QualifyingResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PositionBadge(text = result.position.toString(), highlighted = result.position <= 3)
        Spacer(Modifier.width(8.dp))
        TeamAccent(result.team.id, height = 36)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = result.driver.fullName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.team.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            listOf("Q1" to result.q1, "Q2" to result.q2, "Q3" to result.q3)
                .filter { it.second != null }
                .forEach { (label, time) ->
                    Row {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = time.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            // The lap that set the grid slot is the one worth emphasising.
                            fontWeight = if (time == result.bestTime) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
        }
    }
}

fun LazyListScope.driverStandingsSection(
    standings: List<DriverStanding>,
    season: Int,
) {
    item(key = "standings-header") { SectionHeader("Drivers' championship", trailing = "$season") }

    if (standings.isEmpty()) {
        item(key = "standings-empty") {
            SectionPlaceholder("Standings appear once the season's first race has been run.")
        }
        return
    }

    item(key = "standings-card") {
        SectionCard {
            TableHeader(listOf("POS" to 28, "DRIVER" to 0, "WINS" to 40, "PTS" to 44))
            standings.forEachIndexed { index, standing ->
                DriverStandingRow(standing)
                if (index != standings.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun DriverStandingRow(standing: DriverStanding) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PositionBadge(text = standing.position.toString(), highlighted = standing.position <= 3)
        Spacer(Modifier.width(8.dp))
        TeamAccent(standing.teams.lastOrNull()?.id, height = 32)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = standing.driver.fullName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // A driver who switched teams mid-season is listed against both.
                text = standing.teams.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = standing.wins.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = standing.points.formatPoints(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

/** Column labels; a width of 0 means "take the remaining space". */
@Composable
private fun TableHeader(columns: List<Pair<String, Int>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEachIndexed { index, (label, width) ->
            if (index > 0) Spacer(Modifier.width(if (index == 1) 22.dp else 8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (width > 0 && index > 0) TextAlign.End else TextAlign.Start,
                modifier = if (width > 0) Modifier.width(width.dp) else Modifier.weight(1f),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SectionPlaceholder(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
