package dev.jose.loltracker.feature.matches.riot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.RiotId
import dev.jose.loltracker.core.data.riot.RiotImportRepository
import dev.jose.loltracker.core.data.riot.RiotSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RiotImportUiState(
    val riotId: String = "",
    val apiKey: String = "",
    /** Hay una key compilada desde local.properties: el campo es opcional. */
    val hasBuiltInKey: Boolean = false,
    val riotIdError: Boolean = false,
    val isImporting: Boolean = false,
    val result: ImportResult? = null,
)

@HiltViewModel
class RiotImportViewModel @Inject constructor(
    private val repository: RiotImportRepository,
    private val settings: RiotSettings,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<RiotImportUiState> = _uiState.asStateFlow()

    private fun initialState(): RiotImportUiState {
        val userKey = settings.userApiKey.orEmpty()
        return RiotImportUiState(
            riotId = settings.riotId.orEmpty(),
            apiKey = userKey,
            hasBuiltInKey = userKey.isBlank() && settings.apiKey() != null,
        )
    }

    fun onRiotIdChange(value: String) = _uiState.update { it.copy(riotId = value, riotIdError = false, result = null) }

    fun onApiKeyChange(value: String) = _uiState.update { it.copy(apiKey = value.trim(), result = null) }

    fun import() {
        val state = _uiState.value
        if (state.isImporting) return
        val riotId = RiotId.parse(state.riotId)
        if (riotId == null) {
            _uiState.update { it.copy(riotIdError = true) }
            return
        }
        // Solo se sobrescribe la key guardada si el usuario ha escrito una.
        if (state.apiKey.isNotBlank()) settings.userApiKey = state.apiKey

        _uiState.update { it.copy(isImporting = true, result = null) }
        viewModelScope.launch {
            val result = repository.import(riotId)
            analytics.track(
                when (result) {
                    is ImportResult.Success -> AnalyticsEvent("riot_import", mapOf("imported" to result.imported.toString()))
                    is ImportResult.Failure -> AnalyticsEvent("riot_import_failed", mapOf("error" to result.error.name))
                },
            )
            _uiState.update { it.copy(isImporting = false, result = result) }
        }
    }
}
