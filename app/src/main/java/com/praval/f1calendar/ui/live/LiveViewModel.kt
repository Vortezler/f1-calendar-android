package com.praval.f1calendar.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.praval.f1calendar.core.Res
import com.praval.f1calendar.data.live.LiveRepository
import com.praval.f1calendar.domain.model.LiveSession
import com.praval.f1calendar.domain.model.LiveStanding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class LiveUiState(
    val session: LiveSession? = null,
    val standings: List<LiveStanding> = emptyList(),
    val isRace: Boolean = false,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * A clock that re-evaluates "is a session running" between session polls. Without it the tab would
 * only notice a session starting or ending when the 60-second session poll next fired.
 */
private fun ticker(period: Duration): Flow<Instant> = flow {
    while (true) {
        emit(Instant.now())
        delay(period.toMillis())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
) : ViewModel() {

    val uiState: StateFlow<LiveUiState> = liveRepository.session
        .flatMapLatest { result ->
            when (result) {
                is Res.Loading -> flowOf(LiveUiState(loading = true))
                is Res.Error -> flowOf(LiveUiState(loading = false, errorMessage = result.message))
                is Res.Success -> {
                    val session = result.data
                    if (session == null || !session.isLive(Instant.now())) {
                        // Between sessions there is nothing to poll for; hold the last known
                        // session so the screen can say which one it's waiting on.
                        flowOf(LiveUiState(session = session, loading = false))
                    } else {
                        liveRepository.standings(session).map { standings ->
                            LiveUiState(
                                session = session,
                                standings = standings.dataOrEmpty(),
                                isRace = session.isRace,
                                loading = false,
                                errorMessage = (standings as? Res.Error)?.message,
                            )
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    private fun Res<List<LiveStanding>>.dataOrEmpty(): List<LiveStanding> =
        (this as? Res.Success)?.data.orEmpty()
}

/**
 * Decides whether the Live tab is offered at all. Kept separate from [LiveViewModel] so the
 * navigation bar can observe it without holding the whole live screen's state.
 */
@HiltViewModel
class LiveTabViewModel @Inject constructor(
    liveRepository: LiveRepository,
) : ViewModel() {

    val visible: StateFlow<Boolean> = combine(
        liveRepository.session,
        ticker(Duration.ofSeconds(30)),
    ) { result, now ->
        val session = (result as? Res.Success)?.data ?: return@combine false
        session.isLive(now) || session.isImminent(now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
