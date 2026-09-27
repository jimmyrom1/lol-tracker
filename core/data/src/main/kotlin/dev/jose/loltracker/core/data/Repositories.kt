package dev.jose.loltracker.core.data

import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.Match
import kotlinx.coroutines.flow.Flow

/** Las features solo conocen estas interfaces: ni Room ni Retrofit se filtran a la UI. */
interface MatchRepository {
    fun observeMatches(): Flow<List<Match>>
    suspend fun getMatch(id: Long): Match?
    suspend fun saveMatch(match: Match): Long
    suspend fun deleteMatch(id: Long)
}

interface ChampionRepository {
    /** Catálogo local (puede estar vacío la primera vez, hasta que termine [refresh]). */
    fun observeChampions(): Flow<List<Champion>>

    /** Versión del parche del catálogo guardado: hace falta para las URLs de iconos de Data Dragon. */
    fun observePatchVersion(): Flow<String?>

    /**
     * Descarga el catálogo si hay un parche nuevo. Devuelve fallo si no hay conexión,
     * pero la app sigue funcionando con la caché.
     */
    suspend fun refresh(): Result<Unit>
}
