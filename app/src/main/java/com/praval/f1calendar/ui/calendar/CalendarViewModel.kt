package com.praval.f1calendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.praval.f1calendar.core.Res
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.data.repository.RaceRepository
import com.praval.f1calendar.domain.model.Race
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

data class CalendarUiState(
    val season: Int = Year.now().value,
    val races: List<Race> = emptyList(),
    val availableSeasons: List<Int> = emptyList(),
    val followingCurrentSeason: Boolean = true,
    val useUtc: Boolean = false,
    val isRefreshing: Boolean = false,
    /** False until the first refresh attempt settles, so "empty" isn't shown while loading. */
    val loadedOnce: Boolean = false,
    val errorMessage: String? = null,
)

private data class LoadState(
    val refreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val raceRepository: RaceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val loadState = MutableStateFlow(LoadState())

    /**
     * Reads straight from settings rather than from a [StateFlow] with a placeholder, so a load is
     * never kicked off for the wrong season before preferences have been read.
     */
    private val seasonFlow: Flow<Int> =
        combine(settings.selectedSeason, settings.resolvedCurrentSeason) { selected, resolved ->
            resolveSeason(selected, resolved)
        }.distinctUntilChanged()

    private val racesFlow: Flow<List<Race>> =
        seasonFlow.flatMapLatest { raceRepository.observeSeason(it) }

    val uiState: StateFlow<CalendarUiState> = combine(
        seasonFlow,
        racesFlow,
        combine(settings.useUtc, settings.selectedSeason) { useUtc, selected ->
            useUtc to (selected == SettingsStore.FOLLOW_CURRENT)
        },
        loadState,
    ) { season, races, (useUtc, following), load ->
        CalendarUiState(
            season = season,
            races = races,
            availableSeasons = seasonOptions(season),
            followingCurrentSeason = following,
            useUtc = useUtc,
            isRefreshing = load.refreshing,
            loadedOnce = load.loadedOnce,
            errorMessage = load.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    init {
        viewModelScope.launch {
            // Re-runs whenever the chosen season changes; the TTL keeps it off the network when the
            // cache is still warm.
            seasonFlow.collectLatest { load(force = false) }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(force = true) }
    }

    fun selectSeason(season: Int) {
        viewModelScope.launch { settings.setSelectedSeason(season) }
    }

    fun followCurrentSeason() {
        viewModelScope.launch { settings.setSelectedSeason(SettingsStore.FOLLOW_CURRENT) }
    }

    fun dismissError() {
        loadState.update { it.copy(error = null) }
    }

    private suspend fun load(force: Boolean) {
        loadState.update { it.copy(refreshing = true, error = null) }
        val selected = settings.selectedSeason.first()
        val result: Res<*> = if (selected == SettingsStore.FOLLOW_CURRENT) {
            raceRepository.refreshCurrentSeason(force)
        } else {
            raceRepository.refreshSchedule(selected, force)
        }
        loadState.update {
            it.copy(
                refreshing = false,
                loadedOnce = true,
                error = (result as? Res.Error)?.message,
            )
        }
    }

    private fun resolveSeason(selected: Int, resolved: Int): Int = when {
        selected != SettingsStore.FOLLOW_CURRENT -> selected
        resolved != 0 -> resolved
        // Before the first successful call, the device clock is the best guess available.
        else -> Year.now().value
    }

    private fun seasonOptions(season: Int): List<Int> {
        val newest = maxOf(season, Year.now().value)
        return (newest downTo FIRST_SEASON).toList()
    }

    private companion object {
        /** The championship began in 1950; the API has no data before it. */
        const val FIRST_SEASON = 1950
    }
}
