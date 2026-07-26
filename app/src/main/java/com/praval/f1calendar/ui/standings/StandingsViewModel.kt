package com.praval.f1calendar.ui.standings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.praval.f1calendar.core.Res
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.data.repository.StandingsRepository
import com.praval.f1calendar.domain.model.ConstructorStanding
import com.praval.f1calendar.domain.model.DriverStanding
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

data class StandingsUiState(
    val season: Int = Year.now().value,
    val drivers: List<DriverStanding> = emptyList(),
    val constructors: List<ConstructorStanding> = emptyList(),
    val isRefreshing: Boolean = false,
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
class StandingsViewModel @Inject constructor(
    private val standingsRepository: StandingsRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val loadState = MutableStateFlow(LoadState())

    /** Follows the same season the calendar is showing, so the two screens never disagree. */
    private val seasonFlow: Flow<Int> =
        combine(settings.selectedSeason, settings.resolvedCurrentSeason) { selected, resolved ->
            when {
                selected != SettingsStore.FOLLOW_CURRENT -> selected
                resolved != 0 -> resolved
                else -> Year.now().value
            }
        }.distinctUntilChanged()

    val uiState: StateFlow<StandingsUiState> = combine(
        seasonFlow,
        seasonFlow.flatMapLatest { standingsRepository.observeDrivers(it) },
        seasonFlow.flatMapLatest { standingsRepository.observeConstructors(it) },
        loadState,
    ) { season, drivers, constructors, load ->
        StandingsUiState(
            season = season,
            drivers = drivers,
            constructors = constructors,
            isRefreshing = load.refreshing,
            loadedOnce = load.loadedOnce,
            errorMessage = load.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StandingsUiState())

    init {
        viewModelScope.launch {
            seasonFlow.collectLatest { season -> load(season, force = false) }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(seasonFlow.first(), force = true) }
    }

    fun dismissError() = loadState.update { it.copy(error = null) }

    private suspend fun load(season: Int, force: Boolean) {
        loadState.update { it.copy(refreshing = true, error = null) }
        val drivers = standingsRepository.refreshDrivers(season, force)
        val constructors = standingsRepository.refreshConstructors(season, force)
        val error = (drivers as? Res.Error)?.message ?: (constructors as? Res.Error)?.message
        loadState.update { it.copy(refreshing = false, loadedOnce = true, error = error) }
    }
}
