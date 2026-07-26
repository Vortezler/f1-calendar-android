package com.praval.f1calendar.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.praval.f1calendar.domain.model.LiveStanding
import com.praval.f1calendar.ui.common.EmptyState
import com.praval.f1calendar.ui.common.ErrorBanner
import com.praval.f1calendar.ui.common.LoadingState
import com.praval.f1calendar.ui.common.PositionBadge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = session?.name ?: "Live",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (session != null) {
                                Text(
                                    text = listOfNotNull(session.location, session.countryName)
                                        .distinct()
                                        .joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
            state.errorMessage?.let { ErrorBanner(message = it) }

            when {
                state.loading && state.standings.isEmpty() -> LoadingState()

                session == null -> EmptyState(
                    title = "No session running",
                    subtitle = "Live timing appears here once a practice, qualifying or race " +
                        "session is under way.",
                )

                state.standings.isEmpty() -> EmptyState(
                    title = "Waiting for timing data",
                    subtitle = "${session.name} has started but no cars are on track yet.",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") { LiveHeader(isRace = state.isRace) }
                    items(state.standings, key = { it.driverNumber }) { row ->
                        LiveRow(row, isRace = state.isRace)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveHeader(isRace: Boolean) {
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
            text = "DRIVER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (isRace) "INTERVAL" else "BEST LAP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(84.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun LiveRow(row: LiveStanding, isRace: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PositionBadge(
            text = row.position?.toString() ?: "–",
            highlighted = row.position != null && row.position <= 3,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .width(4.dp)
                .size(width = 4.dp, height = 34.dp)
                .clip(CircleShape)
                .background(Color(row.teamColour)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.acronym,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = row.fullName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = row.teamName.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.width(84.dp),
        ) {
            if (isRace) {
                Text(
                    // The leader has no car ahead, so it shows the race position instead of a gap.
                    text = row.interval ?: if (row.position == 1) "LEADER" else "—",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
                row.gapToLeader?.let {
                    Text(
                        text = "$it to P1",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            } else {
                Text(
                    text = row.bestLapSeconds?.let(::formatLapTime) ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
                row.lastLapSeconds?.let {
                    Text(
                        text = "last ${formatLapTime(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/** Lap times are reported in seconds; F1 convention is m:ss.SSS. */
internal fun formatLapTime(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    val rest = seconds - minutes * 60
    return if (minutes > 0) {
        String.format(Locale.US, "%d:%06.3f", minutes, rest)
    } else {
        String.format(Locale.US, "%.3f", rest)
    }
}
