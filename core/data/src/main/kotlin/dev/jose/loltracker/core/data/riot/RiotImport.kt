package dev.jose.loltracker.core.data.riot

import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.database.CacheDao
import dev.jose.loltracker.core.database.CacheEntity
import dev.jose.loltracker.core.database.MatchDao
import dev.jose.loltracker.core.database.MatchDetailDao
import dev.jose.loltracker.core.database.toEntity
import dev.jose.loltracker.core.network.RiotApi
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ImportResult {
    data class Success(val imported: Int, val alreadyImported: Int, val skipped: Int) : ImportResult
    data class Failure(val error: RiotError) : ImportResult
}

interface RiotImportRepository {
    /** Importa las últimas [count] partidas (hasta 100) de [riotId] y la recuerda para sincronizar. */
    suspend fun import(riotId: RiotId, count: Int = DEFAULT_COUNT): ImportResult

    /** Sincroniza la cuenta guardada: es lo que hacen el arranque de la app y la tarea periódica. */
    suspend fun syncSaved(): ImportResult

    /** Como [syncSaved], pero no hace nada (null) si la última sincronización es de hace menos de [minInterval]. */
    suspend fun syncSavedIfStale(minInterval: Duration = MIN_SYNC_INTERVAL): ImportResult?

    companion object {
        const val DEFAULT_COUNT = 20
        const val MAX_COUNT = 100

        /** Tope de detalles antiguos que se rellenan por sincronización, para no gastar la cuota. */
        const val BACKFILL_PER_SYNC = 20

        val MIN_SYNC_INTERVAL: Duration = Duration.ofMinutes(15)
    }
}

internal class DefaultRiotImportRepository @Inject constructor(
    private val api: RiotApi,
    private val dao: MatchDao,
    private val detailDao: MatchDetailDao,
    private val cache: CacheDao,
    private val champions: ChampionRepository,
    private val settings: RiotSettings,
    private val clock: Clock,
) : RiotImportRepository {

    // Si la tarea periódica y el usuario importan a la vez, la segunda espera: así no se piden
    // dos veces las mismas partidas.
    private val mutex = Mutex()

    override suspend fun syncSaved(): ImportResult {
        val riotId = settings.riotId?.let(RiotId::parse) ?: return ImportResult.Failure(RiotError.NOT_CONFIGURED)
        return import(riotId, RiotImportRepository.DEFAULT_COUNT)
    }

    override suspend fun syncSavedIfStale(minInterval: Duration): ImportResult? {
        val last = settings.lastSyncAt
        if (last != null && Duration.between(last, clock.instant()) < minInterval) return null
        return syncSaved()
    }

    override suspend fun import(riotId: RiotId, count: Int): ImportResult = mutex.withLock {
        if (!settings.canCallRiot()) return ImportResult.Failure(RiotError.MISSING_API_KEY)
        val result = riotCall {
            val puuid = puuidFor(riotId)
            val ids = api.matchIds(puuid, count = count.coerceIn(1, RiotImportRepository.MAX_COUNT))
            // Solo se descargan las partidas nuevas: cada una es una petición contra el límite de la key.
            val known = dao.existingRiotMatchIds(ids).toSet()
            val catalog = champions.observeChampions().first().associateBy { it.id.lowercase() }

            // Los remakes no se guardan como partida; se recuerdan para no descargarlos cada vez.
            val newIds = ids.filterNot { it in known || cache.get(skippedKey(it)) != null }
            // Las importadas antes de guardar el detalle se completan poco a poco.
            val backfill = detailDao.matchIdsWithoutDetail().take(RiotImportRepository.BACKFILL_PER_SYNC)

            var imported = 0
            var skipped = 0
            for (id in (newIds + backfill).distinct()) {
                val dto = api.match(id)
                val match = RiotMatchMapper.toMatch(dto, puuid, catalog)
                if (match == null) {
                    skipped++
                    cache.put(CacheEntity(skippedKey(id), "", clock.instant()))
                    continue
                }
                detailDao.upsert(RiotMatchMapper.toDetail(dto, puuid, catalog).toEntity())
                if (id in newIds) imported += dao.insertAll(listOf(match.toEntity())).count { it != -1L }
            }
            settings.riotId = riotId.toString()
            settings.puuid = puuid
            settings.lastSyncAt = clock.instant()
            ImportResult.Success(imported = imported, alreadyImported = known.size, skipped = skipped)
        }
        when (result) {
            is RiotResult.Success -> result.value
            is RiotResult.Failure -> ImportResult.Failure(result.error)
        }
    }

    private fun skippedKey(matchId: String) = "skipped:$matchId"

    private suspend fun puuidFor(riotId: RiotId): String {
        val saved = settings.puuid?.takeIf { settings.riotId == riotId.toString() }
        return saved ?: api.accountByRiotId(riotId.gameName, riotId.tagLine).puuid
    }
}
