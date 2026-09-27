package dev.jose.loltracker.feature.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.riot.MatchDetailRepository
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotProfileRepository
import dev.jose.loltracker.core.data.riot.RiotResult
import dev.jose.loltracker.core.domain.DraftAdvice
import dev.jose.loltracker.core.domain.DraftAdvisor
import dev.jose.loltracker.core.domain.DraftQuery
import dev.jose.loltracker.core.domain.Record
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.LiveGame
import dev.jose.loltracker.core.model.Role
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LiveGameState {
    data object Idle : LiveGameState
    data object Searching : LiveGameState
    data object NotInGame : LiveGameState
    data class Found(val game: LiveGame) : LiveGameState
    data class Failed(val error: RiotError) : LiveGameState
}

data class DraftUiState(
    val query: DraftQuery = DraftQuery(),
    val advice: DraftAdvice = DraftAdvice(emptyList(), emptyList(), emptyList(), 0),
    val champions: Map<String, Champion> = emptyMap(),
    val championsByKey: Map<String, Champion> = emptyMap(),
    /** Partidas con detalle en total (sin filtrar por rol): si es 0 no hay nada que recomendar. */
    val historySize: Int = 0,
    val live: LiveGameState = LiveGameState.Idle,
    /** Tu historial contra cada campeón rival de la partida en curso (por id de Data Dragon). */
    val liveRecords: Map<String, Record> = emptyMap(),
)

@HiltViewModel
class DraftViewModel @Inject constructor(
    details: MatchDetailRepository,
    champions: ChampionRepository,
    private val profiles: RiotProfileRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val query = MutableStateFlow(DraftQuery())
    private val live = MutableStateFlow<LiveGameState>(LiveGameState.Idle)

    val uiState: StateFlow<DraftUiState> = combine(details.observeAll(), champions.observeChampions(), query, live) { history, champs, q, l ->
        val byKey = champs.filter { it.key.isNotBlank() }.associateBy { it.key }
        val liveEnemies = (l as? LiveGameState.Found)?.game?.let { game ->
            val myTeam = game.me?.teamId
            game.players.filter { it.teamId != myTeam }.mapNotNull { byKey[it.championKey]?.id }.toSet()
        }.orEmpty()
        DraftUiState(
            query = q,
            advice = DraftAdvisor.advise(history, q),
            champions = champs.associateBy { it.id },
            championsByKey = byKey,
            historySize = history.size,
            live = l,
            liveRecords = if (liveEnemies.isEmpty()) emptyMap() else DraftAdvisor.advise(history, DraftQuery(enemies = liveEnemies)).enemies.associate { it.championId to it.record },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DraftUiState())

    init {
        analytics.track(AnalyticsEvent.screenView("draft"))
    }

    fun setRole(role: Role?) = query.update { it.copy(role = role) }

    fun addEnemy(championId: String) = query.update { if (it.enemies.size >= 5) it else it.copy(enemies = it.enemies + championId) }

    fun removeEnemy(championId: String) = query.update { it.copy(enemies = it.enemies - championId) }

    fun addAlly(championId: String) = query.update { if (it.allies.size >= 4) it else it.copy(allies = it.allies + championId) }

    fun removeAlly(championId: String) = query.update { it.copy(allies = it.allies - championId) }

    fun clear() = query.update { DraftQuery(role = it.role) }

    /**
     * Busca tu partida en curso (1 petición, más rango y maestría de los 10 con caché). Solo se
     * lanza al pulsar el botón: nada de sondeos automáticos que gasten la cuota de la key.
     */
    fun findLiveGame() {
        if (live.value == LiveGameState.Searching) return
        live.value = LiveGameState.Searching
        viewModelScope.launch {
            live.value = when (val result = profiles.liveGame()) {
                is RiotResult.Success -> result.value?.let { LiveGameState.Found(it) } ?: LiveGameState.NotInGame
                is RiotResult.Failure -> LiveGameState.Failed(result.error)
            }
            analytics.track(AnalyticsEvent("live_game_lookup", mapOf("found" to (live.value is LiveGameState.Found).toString())))
            (live.value as? LiveGameState.Found)?.let { fillFromLiveGame(it.game) }
        }
    }

    /** Rellena el draft con los campeones de la partida en curso para ver tus enfrentamientos. */
    private fun fillFromLiveGame(game: LiveGame) {
        val byKey = uiState.value.championsByKey
        val me = game.me ?: return
        query.update {
            it.copy(
                enemies = game.players.filter { p -> p.teamId != me.teamId }.mapNotNull { p -> byKey[p.championKey]?.id }.toSet(),
                allies = game.players.filter { p -> p.teamId == me.teamId && !p.isMe }.mapNotNull { p -> byKey[p.championKey]?.id }.toSet(),
            )
        }
    }
}
