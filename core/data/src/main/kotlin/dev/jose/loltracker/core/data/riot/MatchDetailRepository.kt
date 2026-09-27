package dev.jose.loltracker.core.data.riot

import dev.jose.loltracker.core.database.MatchDetailDao
import dev.jose.loltracker.core.database.toEntity
import dev.jose.loltracker.core.database.toModel
import dev.jose.loltracker.core.model.MatchDetail
import dev.jose.loltracker.core.network.RiotApi
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MatchDetailRepository {
    fun observe(riotMatchId: String): Flow<MatchDetail?>

    /** Todas las partidas con detalle: es el historial que usa el asistente de draft. */
    fun observeAll(): Flow<List<MatchDetail>>

    /** Descarga la línea temporal si aún no está. Una partida terminada no cambia: se pide una vez. */
    suspend fun loadTimeline(riotMatchId: String): RiotResult<Unit>
}

internal class DefaultMatchDetailRepository @Inject constructor(
    private val api: RiotApi,
    private val dao: MatchDetailDao,
) : MatchDetailRepository {

    override fun observe(riotMatchId: String): Flow<MatchDetail?> = dao.observe(riotMatchId).map { it?.toModel() }

    override fun observeAll(): Flow<List<MatchDetail>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun loadTimeline(riotMatchId: String): RiotResult<Unit> {
        val detail = dao.get(riotMatchId)?.toModel() ?: return RiotResult.Success(Unit)
        if (detail.timeline != null || detail.me == null) return RiotResult.Success(Unit)
        return riotCall {
            val summary = RiotMatchMapper.summarizeTimeline(api.timeline(riotMatchId), detail)
            dao.upsert(detail.copy(timeline = summary).toEntity())
        }
    }
}
