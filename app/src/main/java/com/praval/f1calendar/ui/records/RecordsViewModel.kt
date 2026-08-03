package com.praval.f1calendar.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.praval.f1calendar.core.Res
import com.praval.f1calendar.data.repository.RaceRepository
import com.praval.f1calendar.data.repository.RecordSyncProgress
import com.praval.f1calendar.data.repository.RecordsRepository
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.domain.model.CircuitRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

enum class RecordSort(val label: String) {
    FASTEST("Fastest"),
    CIRCUIT("Circuit"),
}

enum class RecordFilter(val label: String) {
    ALL("All circuits"),
    CALENDAR("This season"),
}

data class RecordsUiState(
    val rows: List<CircuitRecord> = emptyList(),
    val sort: RecordSort = RecordSort.FASTEST,
    val filter: RecordFilter = RecordFilter.ALL,
    val season: Int = Year.now().value,
    val syncing: Boolean = false,
    val syncCompleted: Int = 0,
    val syncTotal: Int = 0,
    val errorMessage: String? = null,
) {
    val withRecord: Int get() = rows.count { it.record != null }

    /** The outright quickest lap on the list, used as the reference for gap columns. */
    val benchmarkMillis: Long? get() = rows.mapNotNull { it.record?.millis }.minOrNull()
}

private data class SyncState(
    val syncing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val error: String? = null,
)

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val recordsRepository: RecordsRepository,
    private val raceRepository: RaceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val syncState = MutableStateFlow(SyncState())
    private val sort = MutableStateFlow(RecordSort.FASTEST)
    private val filter = MutableStateFlow(RecordFilter.ALL)

    /** Circuit ids used by the season currently being shown elsewhere in the app. */
    private val seasonCircuitIds = MutableStateFlow<Set<String>>(emptySet())
    private val season = MutableStateFlow(Year.now().value)

    private var syncJob: Job? = null

    val uiState: StateFlow<RecordsUiState> = combine(
        recordsRepository.observeCircuits(),
        recordsRepository.observeRecords(),
        combine(sort, filter) { s, f -> s to f },
        combine(seasonCircuitIds, season) { ids, year -> ids to year },
        syncState,
    ) { circuits, records, (sortBy, filterBy), (seasonIds, year), sync ->
        val visible = when (filterBy) {
            RecordFilter.ALL -> circuits
            RecordFilter.CALENDAR -> circuits.filter { it.id in seasonIds }
        }
        val rows = visible.map { circuit ->
            CircuitRecord(
                circuit = circuit,
                record = records[circuit.id],
                // Nothing left to fetch means an absent record genuinely means "none exists".
                loaded = !sync.syncing,
            )
        }
        RecordsUiState(
            rows = rows.sortedWith(comparatorFor(sortBy)),
            sort = sortBy,
            filter = filterBy,
            season = year,
            syncing = sync.syncing,
            syncCompleted = sync.completed,
            syncTotal = sync.total,
            errorMessage = sync.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    init {
        viewModelScope.launch {
            val effectiveSeason = resolveSeason()
            season.value = effectiveSeason
            seasonCircuitIds.value = raceRepository.racesForSeason(effectiveSeason)
                .map { it.circuitId }
                .toSet()
        }
        load(force = false)
    }

    fun setSort(value: RecordSort) {
        sort.value = value
    }

    fun setFilter(value: RecordFilter) {
        filter.value = value
    }

    fun refresh() = load(force = true)

    fun dismissError() = syncState.update { it.copy(error = null) }

    private fun load(force: Boolean) {
        // Restarting mid-sync would double up requests against a rate-limited API.
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            syncState.update { it.copy(syncing = true, error = null) }
            val circuits = recordsRepository.refreshCircuits(force)
            if (circuits is Res.Error) {
                syncState.update { it.copy(syncing = false, error = circuits.message) }
                return@launch
            }
            recordsRepository.syncRecords(force).collect { progress: RecordSyncProgress ->
                syncState.value = SyncState(
                    syncing = progress.running,
                    completed = progress.completed,
                    total = progress.total,
                    error = progress.error,
                )
            }
        }
    }

    private suspend fun resolveSeason(): Int {
        val selected = settings.selectedSeason.first()
        if (selected != SettingsStore.FOLLOW_CURRENT) return selected
        val resolved = settings.resolvedCurrentSeason.first()
        return if (resolved != 0) resolved else Year.now().value
    }

    private fun comparatorFor(sortBy: RecordSort): Comparator<CircuitRecord> = when (sortBy) {
        // Circuits with no record sink to the bottom rather than sorting as instantaneous laps.
        RecordSort.FASTEST -> compareBy(
            { it.record?.millis ?: Long.MAX_VALUE },
            { it.circuit.name },
        )

        RecordSort.CIRCUIT -> compareBy { it.circuit.name }
    }
}
