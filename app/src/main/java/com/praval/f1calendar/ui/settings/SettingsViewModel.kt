package com.praval.f1calendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.notifications.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

data class SettingsUiState(
    val leadMinutes: Int = SettingsStore.DEFAULT_LEAD_MINUTES,
    val useUtc: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** [SettingsStore.FOLLOW_CURRENT] when tracking whichever season is live. */
    val selectedSeason: Int = SettingsStore.FOLLOW_CURRENT,
    val resolvedCurrentSeason: Int = 0,
    val activeReminderCount: Int = 0,
) {
    val followingCurrentSeason: Boolean get() = selectedSeason == SettingsStore.FOLLOW_CURRENT

    val effectiveSeason: Int
        get() = when {
            !followingCurrentSeason -> selectedSeason
            resolvedCurrentSeason != 0 -> resolvedCurrentSeason
            else -> Year.now().value
        }

    val availableSeasons: List<Int>
        get() = (maxOf(effectiveSeason, Year.now().value) downTo FIRST_SEASON).toList()

    private companion object {
        const val FIRST_SEASON = 1950
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val scheduler: NotificationScheduler,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.leadMinutes,
        settings.useUtc,
        settings.remindersEnabled,
        combine(settings.selectedSeason, settings.resolvedCurrentSeason) { a, b -> a to b },
        scheduler.observeReminderCount(),
    ) { leadMinutes, useUtc, remindersEnabled, (selected, resolved), reminderCount ->
        SettingsUiState(
            leadMinutes = leadMinutes,
            useUtc = useUtc,
            remindersEnabled = remindersEnabled,
            selectedSeason = selected,
            resolvedCurrentSeason = resolved,
            activeReminderCount = reminderCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            settings.setLeadMinutes(minutes)
            // Every pending alarm was set relative to the old lead time.
            scheduler.rescheduleAll()
        }
    }

    fun setUseUtc(value: Boolean) {
        viewModelScope.launch { settings.setUseUtc(value) }
    }

    fun setRemindersEnabled(value: Boolean) {
        viewModelScope.launch {
            settings.setRemindersEnabled(value)
            scheduler.rescheduleAll()
        }
    }

    fun selectSeason(season: Int) {
        viewModelScope.launch { settings.setSelectedSeason(season) }
    }

    fun followCurrentSeason() {
        viewModelScope.launch { settings.setSelectedSeason(SettingsStore.FOLLOW_CURRENT) }
    }

    fun clearAllReminders() {
        viewModelScope.launch { scheduler.clearAllReminders() }
    }

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()
}
