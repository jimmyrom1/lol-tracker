package dev.jose.loltracker.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotId
import dev.jose.loltracker.core.data.riot.RiotImportRepository
import dev.jose.loltracker.core.data.riot.RiotProfileRepository
import dev.jose.loltracker.core.data.riot.RiotResult
import dev.jose.loltracker.core.data.riot.RiotSettings
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.PlayerProfile
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProfileLoad {
    data object NotConfigured : ProfileLoad
    data object Loading : ProfileLoad
    data class Loaded(val profile: PlayerProfile) : ProfileLoad
    data class Failed(val error: RiotError) : ProfileLoad
}

data class ProfileUiState(
    val load: ProfileLoad = ProfileLoad.Loading,
    /** Catálogo por id numérico: la maestría de Riot solo trae ese id. */
    val championsByKey: Map<String, Champion> = emptyMap(),
    val lastSyncAt: Instant? = null,
    val isSyncing: Boolean = false,
    /** Resultado de la última sincronización manual, para mostrarlo una vez. */
    val syncResult: ImportResult? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: RiotProfileRepository,
    private val importer: RiotImportRepository,
    private val settings: RiotSettings,
    champions: ChampionRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val state = MutableStateFlow(ProfileUiState(lastSyncAt = settings.lastSyncAt))

    val uiState: StateFlow<ProfileUiState> = combine(state, champions.observeChampions()) { s, champs ->
        s.copy(championsByKey = champs.filter { it.key.isNotBlank() }.associateBy { it.key })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    init {
        analytics.track(AnalyticsEvent.screenView("profile"))
        load(force = false)
    }

    fun refresh() = load(force = true)

    /** Sincroniza ya y, si se pide, descarga hasta 100 partidas antiguas (el limitador las reparte). */
    fun sync(count: Int = RiotImportRepository.DEFAULT_COUNT) {
        val riotId = settings.riotId?.let(RiotId::parse) ?: return
        if (state.value.isSyncing) return
        state.update { it.copy(isSyncing = true, syncResult = null) }
        viewModelScope.launch {
            val result = importer.import(riotId, count)
            analytics.track(AnalyticsEvent("riot_sync_manual", mapOf("count" to count.toString())))
            state.update { it.copy(isSyncing = false, syncResult = result, lastSyncAt = settings.lastSyncAt) }
        }
    }

    fun consumeSyncResult() = state.update { it.copy(syncResult = null) }

    private fun load(force: Boolean) {
        if (settings.riotId == null) {
            state.update { it.copy(load = ProfileLoad.NotConfigured) }
            return
        }
        state.update { it.copy(load = ProfileLoad.Loading) }
        viewModelScope.launch {
            val load = when (val result = profiles.profile(forceRefresh = force)) {
                is RiotResult.Success -> ProfileLoad.Loaded(result.value)
                is RiotResult.Failure ->
                    if (result.error == RiotError.NOT_CONFIGURED) ProfileLoad.NotConfigured else ProfileLoad.Failed(result.error)
            }
            state.update { it.copy(load = load, lastSyncAt = settings.lastSyncAt) }
        }
    }
}
