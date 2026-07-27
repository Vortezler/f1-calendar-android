package com.praval.f1calendar.ui.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.ui.common.EmptyState
import com.praval.f1calendar.ui.common.ErrorBanner
import com.praval.f1calendar.ui.common.LoadingState
import com.praval.f1calendar.ui.common.displayZone
import com.praval.f1calendar.ui.common.rememberTickingNow

/** Remembers what the user was toggling while the notification permission dialog is up. */
private sealed interface PendingAlarm {
    data class Session(val type: SessionType) : PendingAlarm
    data object All : PendingAlarm
}

/**
 * The season as a drum picker, with everything about the selected round laid out beneath it —
 * session times and alarms, the race and qualifying classifications, and the championship.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by rememberTickingNow()
    val zone = remember(state.useUtc) { displayZone(state.useUtc) }
    val context = LocalContext.current

    var pickerExpanded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingAlarm?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pending
        pending = null
        if (!granted || request == null) return@rememberLauncherForActivityResult
        when (request) {
            is PendingAlarm.Session -> viewModel.setAlarm(request.type, true)
            PendingAlarm.All -> viewModel.setAllAlarms(true)
        }
    }

    fun enableWithPermission(request: PendingAlarm) {
        if (hasNotificationPermission(context)) {
            when (request) {
                is PendingAlarm.Session -> viewModel.setAlarm(request.type, true)
                PendingAlarm.All -> viewModel.setAllAlarms(true)
            }
        } else {
            pending = request
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.races.isEmpty() && !state.loadedOnce -> LoadingState()

                state.races.isEmpty() -> EmptyState(
                    title = state.errorMessage ?: "No races for ${state.season}",
                    subtitle = state.errorMessage?.let { "Tap refresh to try again." }
                        ?: "The calendar for this season hasn't been published yet.",
                )

                else -> {
                    // Collapsed by default so the round's own content owns the screen; the wheel is
                    // only worth its height while you are actually choosing.
                    SelectedRaceBar(
                        race = state.selectedRace,
                        expanded = pickerExpanded,
                        onClick = { pickerExpanded = !pickerExpanded },
                    )
                    AnimatedVisibility(
                        visible = pickerExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        RaceWheelPicker(
                            races = state.races,
                            selectedRound = state.selectedRound,
                            now = now,
                            onSelect = viewModel::selectRound,
                            onCommit = { round ->
                                viewModel.selectRound(round)
                                pickerExpanded = false
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val race = state.selectedRace
                        if (race == null) {
                            LoadingState()
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 32.dp),
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

                                raceHeaderItem(
                                    race = race,
                                    now = now,
                                    zone = zone,
                                    allAlarmsOn = state.allAlarmsOn,
                                    onToggleAll = { enable ->
                                        if (enable) enableWithPermission(PendingAlarm.All)
                                        else viewModel.setAllAlarms(false)
                                    },
                                )

                                sessionsSection(
                                    race = race,
                                    now = now,
                                    zone = zone,
                                    exactAlarmsAvailable = viewModel.canScheduleExactAlarms(),
                                    alarmOn = state::alarmOn,
                                    leadMinutes = state::leadMinutes,
                                    isOverridden = state::isOverridden,
                                    onToggle = { type, enable ->
                                        if (enable) enableWithPermission(PendingAlarm.Session(type))
                                        else viewModel.setAlarm(type, false)
                                    },
                                )

                                resultsSection(
                                    results = state.results,
                                    race = race,
                                    now = now,
                                )

                                qualifyingSection(
                                    results = state.qualifying,
                                    race = race,
                                    now = now,
                                )

                                driverStandingsSection(
                                    standings = state.driverStandings,
                                    season = state.season,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The wheel's resting state: the chosen round on one line, tappable to open the picker. The chevron
 * turns with the expansion so the control reads as a disclosure rather than a button.
 */
@Composable
private fun SelectedRaceBar(
    race: Race?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "picker-chevron",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = race?.flag ?: "🏁",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = race?.let { "R${it.round}" }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = race?.name ?: "Choose a race",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = if (expanded) "Close race picker" else "Open race picker",
            modifier = Modifier.rotate(chevronRotation),
        )
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

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
