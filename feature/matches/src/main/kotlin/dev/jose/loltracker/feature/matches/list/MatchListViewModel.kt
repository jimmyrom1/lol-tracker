package dev.jose.loltracker.feature.matches.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.MatchRepository
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchResult
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ResultFilter { ALL, WINS, LOSSES }

data class DaySection(val date: LocalDate, val matches: List<Match>)

sealed interface MatchListUiState {
    data object Loading : MatchListUiState
    data class Content(
        val sections: List<DaySection>,
        val filter: ResultFilter,
        val totalMatches: Int,
        /** id de campeón → URL del icono (puede faltar si aún no se ha descargado). */
        val championIcons: Map<String, String>,
    ) : MatchListUiState
}

@HiltViewModel
class MatchListViewModel @Inject constructor(
    private val matches: MatchRepository,
    champions: ChampionRepository,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(ResultFilter.ALL)
    private var recentlyDeleted: Match? = null

    val uiState: StateFlow<MatchListUiState> = combine(
        matches.observeMatches(),
        champions.observeChampions(),
        filter,
    ) { all, champs, currentFilter ->
        val visible = all.filter {
            when (currentFilter) {
                ResultFilter.ALL -> true
                ResultFilter.WINS -> it.result == MatchResult.WIN
                ResultFilter.LOSSES -> it.result == MatchResult.LOSS
            }
        }
        MatchListUiState.Content(
            sections = visible
                .groupBy { it.playedAt.atZone(clock.zone).toLocalDate() }
                .map { (date, games) -> DaySection(date, games) },
            filter = currentFilter,
            totalMatches = all.size,
            championIcons = champs.associate { it.id to it.iconUrl },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchListUiState.Loading)

    init {
        analytics.track(AnalyticsEvent.screenView("match_list"))
    }

    fun setFilter(value: ResultFilter) {
        filter.value = value
    }

    fun delete(match: Match) {
        recentlyDeleted = match
        viewModelScope.launch {
            matches.deleteMatch(match.id)
            analytics.track(AnalyticsEvent("match_deleted", mapOf("champion" to match.championId)))
        }
    }

    /** Deshacer desde el snackbar: la partida vuelve con el mismo id. */
    fun undoDelete() {
        val match = recentlyDeleted ?: return
        recentlyDeleted = null
        viewModelScope.launch {
            matches.saveMatch(match)
            analytics.track(AnalyticsEvent("match_delete_undone"))
        }
    }
}
