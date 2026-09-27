package dev.jose.loltracker.feature.matches.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.MatchRepository
import dev.jose.loltracker.core.data.riot.MatchDetailRepository
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotResult
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchDetail
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TimelineState {
    data object Loading : TimelineState
    data object Loaded : TimelineState
    data class Failed(val error: RiotError) : TimelineState
}

sealed interface MatchDetailUiState {
    data object Loading : MatchDetailUiState
    data object NotFound : MatchDetailUiState
    data class Content(
        val match: Match,
        /** null si la partida se apuntó a mano o se importó antes de guardar el detalle. */
        val detail: MatchDetail?,
        val championIcons: Map<String, String>,
        val championNames: Map<String, String>,
        /** Para las URLs de objetos y hechizos. */
        val patchVersion: String?,
        val timeline: TimelineState,
    ) : MatchDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MatchDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val matches: MatchRepository,
    private val details: MatchDetailRepository,
    champions: ChampionRepository,
    analytics: AnalyticsTracker,
) : ViewModel() {

    private val matchId: Long = savedStateHandle.get<Long>(MATCH_ID_ARG) ?: 0L
    private val timeline = MutableStateFlow<TimelineState>(TimelineState.Loading)

    private val match = flow { emit(matches.getMatch(matchId)) }

    val uiState: StateFlow<MatchDetailUiState> = match.flatMapLatest { match ->
        if (match == null) return@flatMapLatest flowOf(MatchDetailUiState.NotFound)
        val detailFlow = match.riotMatchId?.let(details::observe) ?: flowOf(null)
        combine(detailFlow, champions.observeChampions(), champions.observePatchVersion(), timeline) { detail, champs, version, tl ->
            MatchDetailUiState.Content(
                match = match,
                detail = detail,
                championIcons = champs.associate { it.id to it.iconUrl },
                championNames = champs.associate { it.id to it.name },
                patchVersion = version,
                timeline = if (detail?.timeline != null) TimelineState.Loaded else tl,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchDetailUiState.Loading)

    init {
        analytics.track(AnalyticsEvent.screenView("match_detail"))
        viewModelScope.launch { loadTimeline() }
    }

    fun retryTimeline() {
        viewModelScope.launch { loadTimeline() }
    }

    /** La línea temporal pesa (≈1 MB): solo se pide al abrir el detalle, y una sola vez por partida. */
    private suspend fun loadTimeline() {
        val riotMatchId = matches.getMatch(matchId)?.riotMatchId
        if (riotMatchId == null) {
            timeline.value = TimelineState.Loaded
            return
        }
        timeline.value = TimelineState.Loading
        timeline.value = when (val result = details.loadTimeline(riotMatchId)) {
            is RiotResult.Success -> TimelineState.Loaded
            is RiotResult.Failure -> TimelineState.Failed(result.error)
        }
    }

    companion object {
        const val MATCH_ID_ARG = "matchId"
    }
}
