package com.praval.f1calendar.ui.standings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.praval.f1calendar.domain.model.ConstructorStanding
import com.praval.f1calendar.domain.model.DriverStanding
import com.praval.f1calendar.ui.common.EmptyState
import com.praval.f1calendar.ui.common.ErrorBanner
import com.praval.f1calendar.ui.common.LoadingState
import com.praval.f1calendar.ui.common.PositionBadge
import com.praval.f1calendar.ui.common.TeamAccent
import com.praval.f1calendar.ui.racedetail.formatPoints

private enum class StandingsTab(val title: String) {
    DRIVERS("Drivers"),
    CONSTRUCTORS("Constructors"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(
    viewModel: StandingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(StandingsTab.DRIVERS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${state.season} standings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
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
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                StandingsTab.entries.forEach { tab ->
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
                val rows = if (selectedTab == StandingsTab.DRIVERS) {
                    state.drivers.size
                } else {
                    state.constructors.size
                }

                when {
                    rows == 0 && !state.loadedOnce -> LoadingState()

                    rows == 0 -> EmptyState(
                        title = "No standings for ${state.season}",
                        subtitle = "Standings appear once the season's first race has been run.",
                    )

                    selectedTab == StandingsTab.DRIVERS -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item(key = "header") { StandingsHeader("DRIVER") }
                        items(state.drivers, key = { it.driver.id }) { standing ->
                            DriverStandingRow(standing)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item(key = "header") { StandingsHeader("CONSTRUCTOR") }
                        items(state.constructors, key = { it.team.id }) { standing ->
                            ConstructorStandingRow(standing)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandingsHeader(nameLabel: String) {
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
        Spacer(Modifier.width(22.dp))
        Text(
            text = nameLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "WINS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "PTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun DriverStandingRow(standing: DriverStanding) {
    StandingRowScaffold(
        position = standing.position,
        constructorId = standing.teams.lastOrNull()?.id,
        title = standing.driver.fullName,
        // A driver who switched teams mid-season is listed against both.
        subtitle = standing.teams.joinToString(", ") { it.name },
        wins = standing.wins,
        points = standing.points,
    )
}

@Composable
private fun ConstructorStandingRow(standing: ConstructorStanding) {
    StandingRowScaffold(
        position = standing.position,
        constructorId = standing.team.id,
        title = standing.team.name,
        subtitle = standing.nationality,
        wins = standing.wins,
        points = standing.points,
    )
}

@Composable
private fun StandingRowScaffold(
    position: Int,
    constructorId: String?,
    title: String,
    subtitle: String?,
    wins: Int,
    points: Double,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PositionBadge(text = position.toString(), highlighted = position <= 3)
        Spacer(Modifier.width(8.dp))
        TeamAccent(constructorId, height = 32)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = wins.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = points.formatPoints(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}
