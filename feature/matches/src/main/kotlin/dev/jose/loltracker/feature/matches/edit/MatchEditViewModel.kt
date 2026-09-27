package dev.jose.loltracker.feature.matches.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.MatchRepository
import dev.jose.loltracker.core.domain.MatchDraft
import dev.jose.loltracker.core.domain.MatchField
import dev.jose.loltracker.core.domain.MatchValidator
import dev.jose.loltracker.core.domain.ValidationError
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del formulario. Los números se guardan como texto: es lo que escribe el usuario. */
data class MatchForm(
    val champion: Champion? = null,
    val role: Role = Role.MID,
    val queue: Queue = Queue.RANKED_SOLO,
    val result: MatchResult = MatchResult.WIN,
    val kills: String = "",
    val deaths: String = "",
    val assists: String = "",
    val creepScore: String = "",
    val durationMinutes: String = "",
    val playedAt: Instant,
    val notes: String = "",
)

data class MatchEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean,
    val form: MatchForm,
    /** Los errores solo se muestran después del primer intento de guardar. */
    val errors: Map<MatchField, ValidationError> = emptyMap(),
    val isSaving: Boolean = false,
)

sealed interface MatchEditEvent {
    data object Saved : MatchEditEvent
    data object NotFound : MatchEditEvent
}

@HiltViewModel
class MatchEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val matches: MatchRepository,
    champions: ChampionRepository,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
) : ViewModel() {

    // Con navegación type-safe los argumentos de la ruta llegan con el nombre de la propiedad.
    private val matchId: Long = savedStateHandle.get<Long>(MATCH_ID_ARG) ?: 0L

    private val _uiState = MutableStateFlow(
        MatchEditUiState(
            isNew = matchId == 0L,
            isLoading = matchId != 0L,
            form = MatchForm(playedAt = clock.instant()),
        ),
    )
    val uiState: StateFlow<MatchEditUiState> = _uiState.asStateFlow()

    val champions: StateFlow<List<Champion>> =
        champions.observeChampions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<MatchEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var submitted = false

    init {
        analytics.track(AnalyticsEvent.screenView(if (matchId == 0L) "match_new" else "match_edit"))
        if (matchId != 0L) load()
    }

    private fun load() = viewModelScope.launch {
        val match = matches.getMatch(matchId)
        if (match == null) {
            _events.send(MatchEditEvent.NotFound)
            return@launch
        }
        _uiState.update { it.copy(isLoading = false, form = match.toForm()) }
    }

    fun onChampionSelected(champion: Champion) = edit { copy(champion = champion) }
    fun onRoleSelected(role: Role) = edit { copy(role = role) }
    fun onQueueSelected(queue: Queue) = edit { copy(queue = queue) }
    fun onResultSelected(result: MatchResult) = edit { copy(result = result) }
    fun onKillsChange(value: String) = edit { copy(kills = value.digitsOnly()) }
    fun onDeathsChange(value: String) = edit { copy(deaths = value.digitsOnly()) }
    fun onAssistsChange(value: String) = edit { copy(assists = value.digitsOnly()) }
    fun onCreepScoreChange(value: String) = edit { copy(creepScore = value.digitsOnly()) }
    fun onDurationChange(value: String) = edit { copy(durationMinutes = value.digitsOnly()) }
    fun onNotesChange(value: String) = edit { copy(notes = value.take(NOTES_MAX_LENGTH)) }

    /** Cambia el día conservando la hora; si el resultado queda en el futuro, se usa "ahora". */
    fun onDateSelected(date: LocalDate) = edit {
        val time = playedAt.atZone(clock.zone).toLocalTime()
        val candidate = LocalDateTime.of(date, time).atZone(clock.zone).toInstant()
        copy(playedAt = minOf(candidate, clock.instant()))
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        submitted = true
        val errors = MatchValidator.validate(state.form.toDraft(), clock.instant())
        _uiState.update { it.copy(errors = errors) }
        if (errors.isNotEmpty()) {
            analytics.track(AnalyticsEvent("match_form_invalid", mapOf("fields" to errors.keys.joinToString(","))))
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val form = state.form
            val champion = requireNotNull(form.champion)
            matches.saveMatch(
                Match(
                    id = matchId,
                    championId = champion.id,
                    championName = champion.name,
                    role = form.role,
                    queue = form.queue,
                    result = form.result,
                    kills = form.kills.trim().toInt(),
                    deaths = form.deaths.trim().toInt(),
                    assists = form.assists.trim().toInt(),
                    creepScore = form.creepScore.trim().toInt(),
                    duration = MatchValidator.durationOf(form.toDraft()),
                    playedAt = form.playedAt,
                    notes = form.notes.trim(),
                ),
            )
            analytics.track(
                AnalyticsEvent(
                    if (state.isNew) "match_created" else "match_updated",
                    mapOf("champion" to champion.id, "result" to form.result.name),
                ),
            )
            _events.send(MatchEditEvent.Saved)
        }
    }

    private inline fun edit(crossinline change: MatchForm.() -> MatchForm) {
        _uiState.update { state ->
            val form = state.form.change()
            // Tras el primer intento, los errores se recalculan en vivo para que desaparezcan al corregir.
            val errors = if (submitted) MatchValidator.validate(form.toDraft(), clock.instant()) else state.errors
            state.copy(form = form, errors = errors)
        }
    }

    private fun MatchForm.toDraft() = MatchDraft(
        championId = champion?.id,
        kills = kills,
        deaths = deaths,
        assists = assists,
        creepScore = creepScore,
        durationMinutes = durationMinutes,
        playedAt = playedAt,
    )

    private fun Match.toForm() = MatchForm(
        // Si el campeón ya no está en el catálogo (o no hay caché), se reconstruye con lo guardado.
        champion = this@MatchEditViewModel.champions.value.firstOrNull { it.id == championId }
            ?: Champion(championId, championName, "", "", emptyList()),
        role = role,
        queue = queue,
        result = result,
        kills = kills.toString(),
        deaths = deaths.toString(),
        assists = assists.toString(),
        creepScore = creepScore.toString(),
        durationMinutes = duration.toMinutes().toString(),
        playedAt = playedAt,
        notes = notes,
    )

    private fun String.digitsOnly() = filter(Char::isDigit).take(4)

    companion object {
        const val MATCH_ID_ARG = "matchId"
        const val NOTES_MAX_LENGTH = 280
    }
}
