package dev.jose.loltracker.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.MatchRepository
import dev.jose.loltracker.core.domain.PlayerStats
import dev.jose.loltracker.core.domain.StatsCalculator
import dev.jose.loltracker.core.model.Queue
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Content(
        val stats: PlayerStats,
        /** null = todas las colas. */
        val queue: Queue?,
        /** Solo se ofrecen las colas en las que hay partidas. */
        val availableQueues: List<Queue>,
        val championIcons: Map<String, String>,
    ) : StatsUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    matches: MatchRepository,
    champions: ChampionRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val queue = MutableStateFlow<Queue?>(null)

    val uiState: StateFlow<StatsUiState> = combine(
        matches.observeMatches(),
        champions.observeChampions(),
        queue,
    ) { all, champs, selected ->
        val available = all.map { it.queue }.distinct().sortedBy { it.ordinal }
        // Si se borra la última partida de la cola elegida, se vuelve a "todas".
        val effective = selected?.takeIf { it in available }
        StatsUiState.Content(
            stats = StatsCalculator.calculate(if (effective == null) all else all.filter { it.queue == effective }),
            queue = effective,
            availableQueues = available,
            championIcons = champs.associate { it.id to it.iconUrl },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    init {
        analytics.track(AnalyticsEvent.screenView("stats"))
    }

    fun selectQueue(value: Queue?) {
        queue.value = value
        analytics.track(AnalyticsEvent("stats_filter", mapOf("queue" to (value?.name ?: "ALL"))))
    }
}
