package com.praval.f1calendar.ui.racedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.praval.f1calendar.core.Res
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.data.repository.RaceRepository
import com.praval.f1calendar.domain.model.QualifyingResult
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.RaceResult
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.notifications.NotificationScheduler
import com.praval.f1calendar.ui.nav.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class RaceDetailUiState(
    val season: Int = 0,
    val round: Int = 0,
    val race: Race? = null,
    val results: List<RaceResult> = emptyList(),
    val qualifying: List<QualifyingResult> = emptyList(),
    val remindedSessions: Set<SessionType> = emptySet(),
    val useUtc: Boolean = false,
    val isRefreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val errorMessage: String? = null,
) {
    /** True when every session that *can* carry a reminder already has one. */
    val allRemindersOn: Boolean
        get() {
            val remindable = race?.sessions?.filter { it.startsAt != null }?.map { it.type }.orEmpty()
            return remindable.isNotEmpty() && remindedSessions.containsAll(remindable)
        }
}

private data class LoadState(
    val refreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RaceDetailViewModel @Inject constructor(
    private val raceRepository: RaceRepository,
    private val scheduler: NotificationScheduler,
    settings: SettingsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val season: Int = checkNotNull(savedStateHandle[Destinations.ARG_SEASON])
    private val round: Int = checkNotNull(savedStateHandle[Destinations.ARG_ROUND])

    private val loadState = MutableStateFlow(LoadState())

    val uiState: StateFlow<RaceDetailUiState> = combine(
        raceRepository.observeRace(season, round),
        raceRepository.observeResults(season, round),
        raceRepository.observeQualifying(season, round),
        scheduler.observeReminders(season, round),
        combine(settings.useUtc, loadState) { useUtc, load -> useUtc to load },
    ) { race, results, qualifying, reminders, (useUtc, load) ->
        RaceDetailUiState(
            season = season,
            round = round,
            race = race,
            results = results,
            qualifying = qualifying,
            remindedSessions = reminders,
            useUtc = useUtc,
            isRefreshing = load.refreshing,
            loadedOnce = load.loadedOnce,
            errorMessage = load.error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RaceDetailUiState(season = season, round = round),
    )

    init {
        viewModelScope.launch { load(force = false) }
    }

    fun refresh() {
        viewModelScope.launch { load(force = true) }
    }

    fun dismissError() = loadState.update { it.copy(error = null) }

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()

    fun setReminder(type: SessionType, enabled: Boolean) {
        viewModelScope.launch {
            val race = raceRepository.observeRace(season, round).first() ?: return@launch
            scheduler.setReminder(race, type, enabled)
        }
    }

    fun setAllReminders(enabled: Boolean) {
        viewModelScope.launch {
            val race = raceRepository.observeRace(season, round).first() ?: return@launch
            scheduler.setAllForRace(race, enabled)
        }
    }

    private suspend fun load(force: Boolean) {
        loadState.update { it.copy(refreshing = true, error = null) }
        var error: String? = null

        // Deep-linking in from a notification can land here with no cached calendar for the season.
        if (raceRepository.observeRace(season, round).first() == null) {
            (raceRepository.refreshSchedule(season, force) as? Res.Error)?.let { error = it.message }
        }

        val race = raceRepository.observeRace(season, round).first()
        if (race != null) {
            val now = Instant.now()
            // Fetching a classification before the session has run just burns a request.
            val qualifyingRun = race.session(SessionType.QUALIFYING)?.startsAt?.isBefore(now) ?: false
            if (qualifyingRun) {
                (raceRepository.refreshQualifying(season, round, force) as? Res.Error)
                    ?.let { if (error == null) error = it.message }
            }
            if (race.isCompleted(now)) {
                (raceRepository.refreshResults(season, round, force) as? Res.Error)
                    ?.let { if (error == null) error = it.message }
            }
        }

        loadState.update { it.copy(refreshing = false, loadedOnce = true, error = error) }
    }
}
