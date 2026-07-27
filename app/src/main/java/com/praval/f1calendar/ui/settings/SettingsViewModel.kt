package com.praval.f1calendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.SessionAlarmRule
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.notifications.NotificationScheduler
import com.praval.f1calendar.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

data class SettingsUiState(
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val useUtc: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** One entry per session type, defaults merged in. */
    val rules: Map<SessionType, SessionAlarmRule> = emptyMap(),
    /** How many individual weekends deviate from the standing rules. */
    val overrideCount: Int = 0,
    val selectedSeason: Int = SettingsStore.FOLLOW_CURRENT,
    val resolvedCurrentSeason: Int = 0,
    val nextAlarm: NextAlarm? = null,
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

    /** Ordered for display: the sessions of a weekend in the order they're run. */
    val orderedRules: List<SessionAlarmRule>
        get() = SessionType.entries.mapNotNull { rules[it] }

    val armedCount: Int get() = rules.values.count { it.enabled }

    private companion object {
        const val FIRST_SEASON = 1950
    }
}

data class NextAlarm(val race: Race, val type: SessionType)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val scheduler: NotificationScheduler,
) : ViewModel() {

    /**
     * The next alarm isn't derivable from a single flow — it depends on the cached calendar as well
     * as the rules — so it's recomputed whenever anything that feeds it changes.
     */
    private val nextAlarm = MutableStateFlow<NextAlarm?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settings.useUtc,
            settings.remindersEnabled,
            settings.appTheme,
        ) { utc, on, theme -> Triple(utc, on, theme) },
        scheduler.observeRules(),
        scheduler.observeOverrideCount(),
        combine(settings.selectedSeason, settings.resolvedCurrentSeason) { a, b -> a to b },
        nextAlarm,
    ) { (useUtc, remindersOn, theme), rules, overrideCount, (selected, resolved), next ->
        SettingsUiState(
            appTheme = theme,
            useUtc = useUtc,
            remindersEnabled = remindersOn,
            rules = rules,
            overrideCount = overrideCount,
            selectedSeason = selected,
            resolvedCurrentSeason = resolved,
            nextAlarm = next,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            combine(
                scheduler.observeRules(),
                scheduler.observeOverrideCount(),
                settings.remindersEnabled,
            ) { _, _, _ -> Unit }.collect { refreshNextAlarm() }
        }
    }

    fun setRuleEnabled(type: SessionType, enabled: Boolean) {
        viewModelScope.launch {
            scheduler.setRuleEnabled(type, enabled)
            refreshNextAlarm()
        }
    }

    fun setRuleLeadMinutes(type: SessionType, minutes: Int) {
        viewModelScope.launch {
            scheduler.setRuleLeadMinutes(type, minutes)
            refreshNextAlarm()
        }
    }

    fun resetRules() {
        viewModelScope.launch {
            scheduler.resetRulesToDefaults()
            refreshNextAlarm()
        }
    }

    fun clearOverrides() {
        viewModelScope.launch {
            scheduler.clearAllOverrides()
            refreshNextAlarm()
        }
    }

    fun setRemindersEnabled(value: Boolean) {
        viewModelScope.launch {
            settings.setRemindersEnabled(value)
            scheduler.rescheduleAll()
            refreshNextAlarm()
        }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch { settings.setAppTheme(theme) }
    }

    fun setUseUtc(value: Boolean) {
        viewModelScope.launch { settings.setUseUtc(value) }
    }

    fun selectSeason(season: Int) {
        viewModelScope.launch { settings.setSelectedSeason(season) }
    }

    fun followCurrentSeason() {
        viewModelScope.launch { settings.setSelectedSeason(SettingsStore.FOLLOW_CURRENT) }
    }

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()

    private suspend fun refreshNextAlarm() {
        nextAlarm.value = scheduler.nextAlarm()?.let { (race, type) -> NextAlarm(race, type) }
    }
}
