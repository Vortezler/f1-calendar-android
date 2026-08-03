package com.praval.f1calendar.ui.records

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.praval.f1calendar.core.LapTime
import com.praval.f1calendar.domain.model.CircuitRecord
import com.praval.f1calendar.ui.common.EmptyState
import com.praval.f1calendar.ui.common.ErrorBanner
import com.praval.f1calendar.ui.common.PositionBadge
import com.praval.f1calendar.ui.common.TeamAccent

/**
 * Outright lap records for every circuit the championship has ever visited, not only those on a
 * calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Lap records",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${state.withRecord} of ${state.rows.size} circuits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            FilterRow(
                sort = state.sort,
                filter = state.filter,
                season = state.season,
                onSort = viewModel::setSort,
                onFilter = viewModel::setFilter,
            )

            if (state.syncing && state.syncTotal > 0) {
                // 78 circuits at one request each takes a while; show it filling in rather than
                // leaving the list looking broken.
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        text = "Loading records… ${state.syncCompleted} of ${state.syncTotal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            state.syncCompleted.toFloat() / state.syncTotal.coerceAtLeast(1)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.errorMessage?.let {
                ErrorBanner(
                    message = it,
                    onRetry = viewModel::refresh,
                    onDismiss = viewModel::dismissError,
                )
            }

            PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.rows.isEmpty()) {
                    EmptyState(
                        title = if (state.syncing) "Loading circuits…" else "No circuits",
                        subtitle = "Pull down to try again.",
                    )
                } else {
                    val benchmark = state.benchmarkMillis
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        itemsIndexed(
                            state.rows,
                            key = { _, row -> row.circuit.id },
                        ) { index, row ->
                            RecordRow(
                                row = row,
                                rank = index + 1,
                                showRank = state.sort == RecordSort.FASTEST,
                                benchmarkMillis = benchmark,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        item(key = "footnote") { Footnote() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    sort: RecordSort,
    filter: RecordFilter,
    season: Int,
    onSort: (RecordSort) -> Unit,
    onFilter: (RecordFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordFilter.entries.forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { onFilter(option) },
                label = {
                    Text(
                        if (option == RecordFilter.CALENDAR) season.toString() else option.label,
                    )
                },
            )
        }
        Spacer(Modifier.weight(1f))
        FilterChip(
            selected = false,
            onClick = {
                onSort(
                    if (sort == RecordSort.FASTEST) RecordSort.CIRCUIT else RecordSort.FASTEST,
                )
            },
            label = { Text("Sort: ${sort.label}") },
        )
    }
}

@Composable
private fun RecordRow(
    row: CircuitRecord,
    rank: Int,
    showRank: Boolean,
    benchmarkMillis: Long?,
) {
    val record = row.record

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRank && record != null) {
            PositionBadge(text = rank.toString(), highlighted = rank <= 3)
            Spacer(Modifier.width(6.dp))
        }
        Text(text = row.circuit.flag, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        if (record != null) {
            TeamAccent(record.teamId, height = 36)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = row.circuit.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (record != null) {
                    "${record.driverName} · ${record.teamName} · ${record.season}"
                } else {
                    listOfNotNull(row.circuit.locality, row.circuit.country).joinToString(", ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = record?.time ?: if (row.loaded) "—" else "…",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
            val gap = if (record != null && benchmarkMillis != null) {
                LapTime.formatGap(record.millis, benchmarkMillis)
            } else {
                null
            }
            Text(
                text = gap ?: if (record != null && benchmarkMillis == record.millis) {
                    "fastest ever"
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun Footnote() {
    Text(
        text = "The outright lap record is the fastest lap set during a race; quicker qualifying " +
            "laps don't count. Lap times are only recorded in the dataset from 2004 onward, so " +
            "circuits last used before then show no record.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
    )
}
