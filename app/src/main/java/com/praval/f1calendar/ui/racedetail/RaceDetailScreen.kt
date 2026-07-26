package com.praval.f1calendar.ui.racedetail

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.praval.f1calendar.domain.model.QualifyingResult
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.RaceResult
import com.praval.f1calendar.domain.model.RaceSession
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.ui.common.EmptyState
import com.praval.f1calendar.ui.common.ErrorBanner
import com.praval.f1calendar.ui.common.LoadingState
import com.praval.f1calendar.ui.common.PositionBadge
import com.praval.f1calendar.ui.common.TeamAccent
import com.praval.f1calendar.ui.common.displayZone
import com.praval.f1calendar.ui.common.formatCountdown
import com.praval.f1calendar.ui.common.formatDateTime
import com.praval.f1calendar.ui.common.formatFullDate
import com.praval.f1calendar.ui.common.formatLeadTime
import com.praval.f1calendar.ui.common.rememberTickingNow
import java.time.Instant
import java.time.ZoneId

private enum class DetailTab(val title: String) {
    SESSIONS("Sessions"),
    RESULTS("Race"),
    QUALIFYING("Qualifying"),
}

/** Remembers what the user was toggling while the notification permission dialog is up. */
private sealed interface PendingReminder {
    data class Session(val type: SessionType) : PendingReminder
    data object All : PendingReminder
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaceDetailScreen(
    onBack: () -> Unit,
    viewModel: RaceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by rememberTickingNow()
    val zone = remember(state.useUtc) { displayZone(state.useUtc) }
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(DetailTab.SESSIONS) }
    var pending by remember { mutableStateOf<PendingReminder?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pending
        pending = null
        if (!granted || request == null) return@rememberLauncherForActivityResult
        when (request) {
            is PendingReminder.Session -> viewModel.setAlarm(request.type, true)
            PendingReminder.All -> viewModel.setAllAlarms(true)
        }
    }

    fun enableWithPermission(request: PendingReminder) {
        if (hasNotificationPermission(context)) {
            when (request) {
                is PendingReminder.Session -> viewModel.setAlarm(request.type, true)
                PendingReminder.All -> viewModel.setAllAlarms(true)
            }
        } else {
            pending = request
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val race = state.race

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = race?.name ?: "Race ${state.round}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (race != null) {
                            Text(
                                text = "Round ${race.round} · ${race.season}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (race != null && race.sessions.any { it.startsAt != null }) {
                        val allOn = state.allAlarmsOn
                        IconButton(
                            onClick = {
                                if (allOn) viewModel.setAllAlarms(false)
                                else enableWithPermission(PendingReminder.All)
                            },
                        ) {
                            Icon(
                                imageVector = if (allOn) {
                                    Icons.Filled.Notifications
                                } else {
                                    Icons.Outlined.Notifications
                                },
                                contentDescription = if (allOn) {
                                    "Turn off all reminders for this race"
                                } else {
                                    "Remind me about every session"
                                },
                                tint = if (allOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                DetailTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) },
                    )
                }
            }

            state.errorMessage?.let { message ->
                ErrorBanner(
                    message = message,
                    onRetry = viewModel::refresh,
                    onDismiss = viewModel::dismissError,
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    race == null && !state.loadedOnce -> LoadingState()
                    race == null -> EmptyState(
                        title = "Race not found",
                        subtitle = "Pull down to reload the calendar for ${state.season}.",
                    )

                    else -> when (selectedTab) {
                        DetailTab.SESSIONS -> SessionsTab(
                            race = race,
                            now = now,
                            zone = zone,
                            state = state,
                            exactAlarmsAvailable = viewModel.canScheduleExactAlarms(),
                            onToggle = { type, enabled ->
                                if (enabled) enableWithPermission(PendingReminder.Session(type))
                                else viewModel.setAlarm(type, false)
                            },
                        )

                        DetailTab.RESULTS -> ResultsTab(
                            results = state.results,
                            race = race,
                            now = now,
                            loadedOnce = state.loadedOnce,
                        )

                        DetailTab.QUALIFYING -> QualifyingTab(
                            results = state.qualifying,
                            race = race,
                            now = now,
                            loadedOnce = state.loadedOnce,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsTab(
    race: Race,
    now: Instant,
    zone: ZoneId,
    state: RaceDetailUiState,
    exactAlarmsAvailable: Boolean,
    onToggle: (SessionType, Boolean) -> Unit,
) {
    val sessions = remember(race) { race.sessionsInOrder() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "venue") {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(race.flag, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = race.circuitName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = listOfNotNull(race.locality, race.country).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (race.isSprintWeekend) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sprint weekend format",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (!exactAlarmsAvailable && sessions.any { state.alarmOn(it.type) }) {
            item(key = "exact-alarm-note") {
                Text(
                    text = "Exact alarms are off for this app, so alarms may arrive a few " +
                        "minutes late. You can allow them in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        items(sessions, key = { it.type.name }) { session ->
            SessionRow(
                session = session,
                now = now,
                zone = zone,
                alarmOn = state.alarmOn(session.type),
                leadMinutes = state.leadMinutes(session.type),
                overridden = state.isOverridden(session.type),
                onToggle = { onToggle(session.type, it) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = session.type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = start?.let { formatDateTime(it, zone) } ?: "Start time to be confirmed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (upcoming && start != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "in ${formatCountdown(now, start)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (upcoming && alarmOn) {
                Spacer(Modifier.height(2.dp))
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

@Composable
private fun ResultsTab(
    results: List<RaceResult>,
    race: Race,
    now: Instant,
    loadedOnce: Boolean,
) {
    when {
        results.isNotEmpty() -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "results-header") { ResultsHeader() }
            items(results, key = { it.position }) { result ->
                ResultRow(result)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        !race.isCompleted(now) -> EmptyState(
            title = "Race hasn't run yet",
            subtitle = "Results appear here once the chequered flag drops on " +
                "${race.raceStart?.let { formatFullDate(it, ZoneId.systemDefault()) } ?: race.raceDate}.",
        )

        !loadedOnce -> LoadingState()

        else -> EmptyState(
            title = "No results published",
            subtitle = "Pull down to check again.",
        )
    }
}

@Composable
private fun ResultsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "POS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "DRIVER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "TIME / STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "PTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ResultRow(result: RaceResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PositionBadge(
            // Retirements report "R"/"D"/"W" rather than a finishing position.
            text = if (result.isClassified) result.position.toString() else result.positionText,
            highlighted = result.position <= 3 && result.isClassified,
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
            if (result.grid != null) {
                Text(
                    text = "from P${result.grid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = result.points.formatPoints(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(28.dp),
        )
    }
}

@Composable
private fun QualifyingTab(
    results: List<QualifyingResult>,
    race: Race,
    now: Instant,
    loadedOnce: Boolean,
) {
    val qualifyingStart = race.session(SessionType.QUALIFYING)?.startsAt

    when {
        results.isNotEmpty() -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(results, key = { it.position }) { result ->
                QualifyingRow(result)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        qualifyingStart != null && qualifyingStart.isAfter(now) -> EmptyState(
            title = "Qualifying hasn't run yet",
            subtitle = "Grid positions appear here once the session is over.",
        )

        !loadedOnce -> LoadingState()

        else -> EmptyState(
            title = "No qualifying data",
            subtitle = "The API doesn't publish qualifying times for every historical season.",
        )
    }
}

@Composable
private fun QualifyingRow(result: QualifyingResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PositionBadge(
            text = result.position.toString(),
            highlighted = result.position <= 3,
        )
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

/** Points are whole numbers except for the half-points seasons (1954-1960s, and 2021 Spa). */
internal fun Double.formatPoints(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
