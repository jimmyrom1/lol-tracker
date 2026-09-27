package dev.jose.loltracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jose.loltracker.core.data.ChampionRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Actualiza el catálogo de campeones al abrir la app. Si falla (sin conexión) se avisa una vez,
 * pero la app sigue funcionando con lo que haya en Room.
 */
@HiltViewModel
class MainViewModel @Inject constructor(private val champions: ChampionRepository) : ViewModel() {

    private val _offline = Channel<Unit>(Channel.CONFLATED)
    val offline = _offline.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (champions.refresh().isFailure) _offline.send(Unit)
        }
    }
}
