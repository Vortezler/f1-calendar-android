package com.praval.f1calendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.praval.f1calendar.core.PendingRaceSelection
import com.praval.f1calendar.core.Res
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.data.repository.RaceRepository
import com.praval.f1calendar.data.repository.StandingsRepository
import com.praval.f1calendar.domain.model.DriverStanding
import com.praval.f1calendar.domain.model.QualifyingResult
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.RaceResult
import com.praval.f1calendar.domain.model.SessionAlarmRule
import com.praval.f1calendar.domain.model.SessionType
import com.praval.f1calendar.notifications.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Year
import javax.inject.Inject

data class CalendarUiState(
    val season: Int = Year.now().value,
    val races: List<Race> = emptyList(),
    val availableSeasons: List<Int> = emptyList(),
    val followingCurrentSeason: Boolean = true,
    val selectedRound: Int? = null,
    val selectedRace: Race? = null,
    val results: List<RaceResult> = emptyList(),
    val qualifying: List<QualifyingResult> = emptyList(),
    val driverStandings: List<DriverStanding> = emptyList(),
    val rules: Map<SessionType, SessionAlarmRule> = emptyMap(),
    val overrides: Map<SessionType, Boolean> = emptyMap(),
    val useUtc: Boolean = false,
    val isRefreshing: Boolean = false,
    /** False until the first refresh attempt settles, so "empty" isn't shown while loading. */
    val loadedOnce: Boolean = false,
    val errorMessage: String? = null,
) {
    fun alarmOn(type: SessionType): Boolean = overrides[type] ?: rules[type]?.enabled ?: false

    fun leadMinutes(type: SessionType): Int = rules[type]?.leadMinutes ?: 0

    /** True when this weekend's setting differs from the standing rule for that session type. */
    fun isOverridden(type: SessionType): Boolean = type in overrides

    val allAlarmsOn: Boolean
        get() {
            val armable = selectedRace?.sessions?.filter { it.startsAt != null }?.map { it.type }
                .orEmpty()
            return armable.isNotEmpty() && armable.all { alarmOn(it) }
        }
}

private data class SeasonState(
    val season: Int,
    val races: List<Race>,
    val followingCurrent: Boolean,
)

private data class RaceDetail(
    val results: List<RaceResult> = emptyList(),
    val qualifying: List<QualifyingResult> = emptyList(),
    val rules: Map<SessionType, SessionAlarmRule> = emptyMap(),
    val overrides: Map<SessionType, Boolean> = emptyMap(),
)

private data class LoadState(
    val refreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val raceRepository: RaceRepository,
    private val standingsRepository: StandingsRepository,
    private val scheduler: NotificationScheduler,
    private val pendingRaceSelection: PendingRaceSelection,
    private val settings: SettingsStore,
) : ViewModel() {

    private val loadState = MutableStateFlow(LoadState())
    private val selectedRound = MutableStateFlow<Int?>(null)

    /** Survives until the schedule that contains it has actually loaded. */
    private var requestedRound: Int? = null

    /**
     * Reads straight from settings rather than from a [StateFlow] with a placeholder, so a load is
     * never kicked off for the wrong season before preferences have been read.
     */
    private val seasonFlow: Flow<Int> =
        combine(settings.selectedSeason, settings.resolvedCurrentSeason) { selected, resolved ->
            resolveSeason(selected, resolved)
        }.distinctUntilChanged()

    private val seasonState: Flow<SeasonState> = combine(
        seasonFlow,
        seasonFlow.flatMapLatest { raceRepository.observeSeason(it) },
        settings.selectedSeason,
    ) { season, races, selectedPref ->
        SeasonState(season, races, selectedPref == SettingsStore.FOLLOW_CURRENT)
    }

    private val raceDetail: Flow<RaceDetail> =
        combine(seasonFlow, selectedRound) { season, round -> season to round }
            .distinctUntilChanged()
            .flatMapLatest { (season, round) ->
                if (round == null) {
                    flowOf(RaceDetail())
                } else {
                    combine(
                        raceRepository.observeResults(season, round),
                        raceRepository.observeQualifying(season, round),
                        scheduler.observeRules(),
                        scheduler.observeOverrides(season, round),
                    ) { results, qualifying, rules, overrides ->
                        RaceDetail(results, qualifying, rules, overrides)
                    }
                }
            }

    val uiState: StateFlow<CalendarUiState> = combine(
        seasonState,
        selectedRound,
        raceDetail,
        seasonFlow.flatMapLatest { standingsRepository.observeDrivers(it) },
        combine(settings.useUtc, loadState) { useUtc, load -> useUtc to load },
    ) { season, round, detail, standings, (useUtc, load) ->
        CalendarUiState(
            season = season.season,
            races = season.races,
            availableSeasons = seasonOptions(season.season),
            followingCurrentSeason = season.followingCurrent,
            selectedRound = round,
            selectedRace = season.races.firstOrNull { it.round == round },
            results = detail.results,
            qualifying = detail.qualifying,
            driverStandings = standings,
            rules = detail.rules,
            overrides = detail.overrides,
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
            seasonFlow.collectLatest { season ->
                loadSchedule(force = false)
                loadStandings(season, force = false)
            }
        }

        viewModelScope.launch {
            var lastSeason: Int? = null
            combine(seasonFlow, seasonFlow.flatMapLatest { raceRepository.observeSeason(it) }) { s, r -> s to r }
                .collect { (season, races) ->
                    if (season != lastSeason) {
                        lastSeason = season
                        selectedRound.value = null
                    }
                    if (races.isEmpty()) return@collect

                    // An alarm the user tapped wins over the default, but only once its schedule
                    // has arrived.
                    val requested = requestedRound?.takeIf { round -> races.any { it.round == round } }
                    if (requested != null) {
                        requestedRound = null
                        selectedRound.value = requested
                        return@collect
                    }
                    val current = selectedRound.value
                    if (current == null || races.none { it.round == current }) {
                        selectedRound.value = defaultRound(races)
                    }
                }
        }

        viewModelScope.launch {
            pendingRaceSelection.race.filterNotNull().collect { key ->
                pendingRaceSelection.consume()
                requestedRound = key.round
                // Only pin the season if the app isn't already showing it, so tapping an alarm for
                // the live season doesn't silently switch the user off "current season".
                val effective = resolveSeason(
                    settings.selectedSeason.first(),
                    settings.resolvedCurrentSeason.first(),
                )
                if (effective != key.season) {
                    settings.setSelectedSeason(key.season)
                } else {
                    // Season is unchanged, so the schedule collector above won't re-fire; apply now.
                    val races = raceRepository.observeSeason(key.season).first()
                    if (races.any { it.round == key.round }) {
                        requestedRound = null
                        selectedRound.value = key.round
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(seasonFlow, selectedRound.filterNotNull()) { season, round -> season to round }
                .distinctUntilChanged()
                // Selection updates continuously while the wheel spins; only fetch once it rests.
                .debounce(SELECTION_SETTLE_MS)
                .collectLatest { (season, round) -> loadRaceDetail(season, round, force = false) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val season = seasonFlow.first()
            loadSchedule(force = true)
            loadStandings(season, force = true)
            selectedRound.value?.let { loadRaceDetail(season, it, force = true) }
        }
    }

    fun selectRound(round: Int) {
        selectedRound.value = round
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

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()

    fun setAlarm(type: SessionType, enabled: Boolean) {
        viewModelScope.launch {
            val race = uiState.value.selectedRace ?: return@launch
            scheduler.setOverride(race, type, enabled)
        }
    }

    fun setAllAlarms(enabled: Boolean) {
        viewModelScope.launch {
            val race = uiState.value.selectedRace ?: return@launch
            scheduler.setAllForRace(race, enabled)
        }
    }

    private suspend fun loadSchedule(force: Boolean) {
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

    private suspend fun loadStandings(season: Int, force: Boolean) {
        // A standings failure must not mask a schedule that loaded fine, so it only reports an
        // error when there is nothing else to say.
        val result = standingsRepository.refreshDrivers(season, force)
        if (result is Res.Error) {
            loadState.update { if (it.error == null) it.copy(error = result.message) else it }
        }
    }

    private suspend fun loadRaceDetail(season: Int, round: Int, force: Boolean) {
        val race = raceRepository.observeRace(season, round).first() ?: return
        val now = Instant.now()

        // Fetching a classification before the session has run just burns a request.
        val qualifyingRun = race.session(SessionType.QUALIFYING)?.startsAt?.isBefore(now) == true
        if (qualifyingRun) {
            raceRepository.refreshQualifying(season, round, force)
        }
        if (race.isCompleted(now)) {
            raceRepository.refreshResults(season, round, force)
        }
    }

    /** Opens on the next race still to run; once the season is over, on the finale. */
    private fun defaultRound(races: List<Race>): Int {
        val now = Instant.now()
        return races.firstOrNull { !it.isCompleted(now) }?.round
            ?: races.last().round
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
        const val SELECTION_SETTLE_MS = 350L
    }
}
