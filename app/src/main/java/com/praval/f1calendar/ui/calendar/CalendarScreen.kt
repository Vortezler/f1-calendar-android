package com.praval.f1calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.ui.common.EmptyState
import com.praval.f1calendar.ui.common.ErrorBanner
import com.praval.f1calendar.ui.common.LoadingState
import com.praval.f1calendar.ui.common.SectionHeader
import com.praval.f1calendar.ui.common.formatCountdown
import com.praval.f1calendar.ui.common.formatDate
import com.praval.f1calendar.ui.common.formatDateTime
import com.praval.f1calendar.ui.common.formatRelativeDays
import com.praval.f1calendar.ui.common.rememberTickingNow
import com.praval.f1calendar.ui.common.displayZone
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onRaceClick: (season: Int, round: Int) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by rememberTickingNow()
    val zone = remember(state.useUtc) { displayZone(state.useUtc) }

    // Recomputed as the clock passes each race's end, so a finished GP moves sections on its own.
    val upcoming = remember(state.races, now) { state.races.filterNot { it.isCompleted(now) } }
    val completed = remember(state.races, now) {
        state.races.filter { it.isCompleted(now) }.sortedByDescending { it.round }
    }
    val nextRace = upcoming.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SeasonSelector(
                        season = state.season,
                        followingCurrent = state.followingCurrentSeason,
                        seasons = state.availableSeasons,
                        onSelect = viewModel::selectSeason,
                        onFollowCurrent = viewModel::followCurrentSeason,
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.races.isEmpty() && !state.loadedOnce -> LoadingState()

                state.races.isEmpty() -> EmptyState(
                    title = state.errorMessage ?: "No races for ${state.season}",
                    subtitle = state.errorMessage?.let { "Pull down to try again." }
                        ?: "The calendar for this season hasn't been published yet.",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    state.errorMessage?.let { message ->
                        item(key = "error") {
                            ErrorBanner(
                                message = message,
                                onRetry = viewModel::refresh,
                                onDismiss = viewModel::dismissError,
                            )
                        }
                    }

                    if (nextRace != null) {
                        item(key = "next") {
                            NextRaceCard(
                                race = nextRace,
                                now = now,
                                zone = zone,
                                onClick = { onRaceClick(nextRace.season, nextRace.round) },
                            )
                        }
                    }

                    if (upcoming.isNotEmpty()) {
                        item(key = "upcoming-header") {
                            SectionHeader("Upcoming", trailing = "${upcoming.size} races")
                        }
                        items(upcoming, key = { "u-${it.season}-${it.round}" }) { race ->
                            RaceRow(
                                race = race,
                                now = now,
                                zone = zone,
                                completed = false,
                                onClick = { onRaceClick(race.season, race.round) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    if (completed.isNotEmpty()) {
                        item(key = "completed-header") {
                            SectionHeader("Completed", trailing = "${completed.size} races")
                        }
                        items(completed, key = { "c-${it.season}-${it.round}" }) { race ->
                            RaceRow(
                                race = race,
                                now = now,
                                zone = zone,
                                completed = true,
                                onClick = { onRaceClick(race.season, race.round) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonSelector(
    season: Int,
    followingCurrent: Boolean,
    seasons: List<Int>,
    onSelect: (Int) -> Unit,
    onFollowCurrent: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$season season",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change season")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Current season",
                        fontWeight = if (followingCurrent) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = {
                    onFollowCurrent()
                    expanded = false
                },
            )
            HorizontalDivider()
            seasons.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.toString(),
                            fontWeight = if (!followingCurrent && option == season) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NextRaceCard(
    race: Race,
    now: Instant,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    // Prefer counting down to the next session that hasn't started, not always to the race.
    val nextSession = race.sessionsInOrder()
        .firstOrNull { it.startsAt != null && it.startsAt.isAfter(now) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "NEXT UP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(race.flag, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = race.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Round ${race.round} · ${race.circuitName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (race.isSprintWeekend) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "SPRINT WEEKEND",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (nextSession?.startsAt != null) {
                Text(
                    text = "${nextSession.type.label} in ${formatCountdown(now, nextSession.startsAt)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatDateTime(nextSession.startsAt, zone),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = formatDate(
                        race.raceStart ?: race.raceDate.atStartOfDay(zone).toInstant(),
                        zone,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RaceRow(
    race: Race,
    now: Instant,
    zone: ZoneId,
    completed: Boolean,
    onClick: () -> Unit,
) {
    val raceInstant = race.raceStart
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = race.flag,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "R${race.round}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = race.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(race.circuitName, race.locality).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = raceInstant?.let { formatDateTime(it, zone) }
                    ?: formatDate(race.raceDate.atStartOfDay(zone).toInstant(), zone),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                completed -> "Done"
                raceInstant != null -> formatRelativeDays(now, raceInstant)
                else -> formatRelativeDays(now, race.raceDate.atStartOfDay(zone).toInstant())
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (completed) muted else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}
