package dev.jose.loltracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotImportRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface StartupMessage {
    data object Offline : StartupMessage
    data class NewMatches(val count: Int) : StartupMessage
    data object KeyRejected : StartupMessage
}

/**
 * Al abrir la app: actualiza el catálogo de campeones y, si hay una cuenta guardada, descarga
 * las partidas nuevas (como mucho una vez cada 15 minutos, para no gastar la cuota de la key).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val champions: ChampionRepository,
    private val importer: RiotImportRepository,
) : ViewModel() {

    private val _messages = Channel<StartupMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (champions.refresh().isFailure) {
                _messages.send(StartupMessage.Offline)
                return@launch
            }
            when (val result = importer.syncSavedIfStale()) {
                is ImportResult.Success -> if (result.imported > 0) _messages.send(StartupMessage.NewMatches(result.imported))
                is ImportResult.Failure -> if (result.error == RiotError.INVALID_API_KEY) _messages.send(StartupMessage.KeyRejected)
                null -> Unit
            }
        }
    }
}
