package com.praval.f1calendar.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.praval.f1calendar.domain.model.DefaultAlarmRules
import com.praval.f1calendar.domain.model.SessionAlarmRule
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.ui.common.SectionHeader
import com.praval.f1calendar.ui.common.displayZone
import com.praval.f1calendar.ui.common.formatDateTime
import com.praval.f1calendar.ui.common.formatLeadTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Read on each composition rather than cached: the user can change either of these in system
    // settings and come straight back to this screen.
    val notificationsAllowed = hasNotificationPermission(context)
    val exactAlarmsAllowed = viewModel.canScheduleExactAlarms()
    val zone = remember(state.useUtc) { displayZone(state.useUtc) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { SectionHeader("Alarms") }

            item {
                SettingRow(
                    title = "Session alarms",
                    subtitle = if (state.remindersEnabled) {
                        "${state.armedCount} of ${SessionType.entries.size} session types armed"
                    } else {
                        "All alarms are paused"
                    },
                    trailing = {
                        Switch(
                            checked = state.remindersEnabled,
                            onCheckedChange = viewModel::setRemindersEnabled,
                        )
                    },
                )
            }

            state.nextAlarm?.let { next ->
                item(key = "next-alarm") {
                    val start = next.race.session(next.type)?.startsAt
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = "NEXT ALARM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${next.type.label} · ${next.race.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (start != null) {
                                Text(
                                    text = "Session starts ${formatDateTime(start, zone)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            if (!notificationsAllowed) {
                item {
                    PermissionRow(
                        title = "Notifications are blocked",
                        subtitle = "Alarms can't be delivered until notifications are allowed.",
                        buttonText = "Open settings",
                        onClick = { context.openNotificationSettings() },
                    )
                }
            }

            if (!exactAlarmsAllowed) {
                item {
                    PermissionRow(
                        title = "Exact alarms not allowed",
                        subtitle = "Alarms still work, but may arrive a few minutes late.",
                        buttonText = "Allow",
                        onClick = { context.openExactAlarmSettings() },
                    )
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item {
                SectionHeader("Which sessions get an alarm")
            }
            item {
                Text(
                    text = "Applies to every weekend on the calendar. Individual sessions can " +
                        "still be changed from a race's page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }

            items(state.orderedRules, key = { it.type.name }) { rule ->
                SessionRuleRow(
                    rule = rule,
                    enabled = state.remindersEnabled,
                    onToggle = { viewModel.setRuleEnabled(rule.type, it) },
                    onLeadChange = { viewModel.setRuleLeadMinutes(rule.type, it) },
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (state.overrideCount == 0) {
                                "No per-weekend changes"
                            } else {
                                "${state.overrideCount} session${
                                    if (state.overrideCount == 1) "" else "s"
                                } changed for one weekend only"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.overrideCount > 0) {
                        TextButton(onClick = viewModel::clearOverrides) { Text("Clear") }
                    }
                    OutlinedButton(onClick = viewModel::resetRules) { Text("Reset") }
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item { SectionHeader("Times") }

            item {
                val deviceZone = remember { ZoneId.systemDefault() }
                Column(Modifier.padding(vertical = 4.dp)) {
                    TimezoneOption(
                        label = "Device timezone",
                        detail = deviceZone.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        selected = !state.useUtc,
                        onSelect = { viewModel.setUseUtc(false) },
                    )
                    TimezoneOption(
                        label = "UTC",
                        detail = "Show every session in the API's native timezone",
                        selected = state.useUtc,
                        onSelect = { viewModel.setUseUtc(true) },
                    )
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item { SectionHeader("Season") }

            item {
                SeasonSetting(
                    followingCurrent = state.followingCurrentSeason,
                    effectiveSeason = state.effectiveSeason,
                    seasons = state.availableSeasons,
                    onFollowCurrent = viewModel::followCurrentSeason,
                    onSelect = viewModel::selectSeason,
                )
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item { SectionHeader("About") }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Schedule, results and standings from the Jolpica-F1 API, the " +
                            "community successor to Ergast. Live timing from OpenF1. Times are " +
                            "published in UTC and converted for display.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Not affiliated with Formula 1 or the FIA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRuleRow(
    rule: SessionAlarmRule,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onLeadChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rule.type.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // The lead time is meaningless while the type is off, so hide rather than grey it.
        if (rule.enabled) {
            LeadTimePicker(
                minutes = rule.leadMinutes,
                enabled = enabled,
                onSelect = onLeadChange,
            )
            Spacer(Modifier.width(8.dp))
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
            enabled = enabled,
        )
    }
}

@Composable
private fun LeadTimePicker(
    minutes: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatLeadTime(minutes),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Change lead time",
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DefaultAlarmRules.LEAD_TIME_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            formatLeadTime(option),
                            fontWeight = if (option == minutes) FontWeight.Bold else FontWeight.Normal,
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
private fun SettingRow(
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onClick) { Text(buttonText) }
    }
}

@Composable
private fun TimezoneOption(
    label: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeasonSetting(
    followingCurrent: Boolean,
    effectiveSeason: Int,
    seasons: List<Int>,
    onFollowCurrent: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "Season shown", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (followingCurrent) {
                        "Current season ($effectiveSeason)"
                    } else {
                        effectiveSeason.toString()
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
            seasons.forEach { season ->
                DropdownMenuItem(
                    text = {
                        Text(
                            season.toString(),
                            fontWeight = if (!followingCurrent && season == effectiveSeason) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        onSelect(season)
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

private fun Context.openNotificationSettings() {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(
        AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        "package:$packageName".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
