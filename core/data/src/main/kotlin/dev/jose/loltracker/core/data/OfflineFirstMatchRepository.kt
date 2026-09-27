package dev.jose.loltracker.core.data

import dev.jose.loltracker.core.database.MatchDao
import dev.jose.loltracker.core.database.toEntity
import dev.jose.loltracker.core.database.toModel
import dev.jose.loltracker.core.model.Match
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class OfflineFirstMatchRepository @Inject constructor(
    private val dao: MatchDao,
) : MatchRepository {

    override fun observeMatches(): Flow<List<Match>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun getMatch(id: Long): Match? = dao.getById(id)?.toModel()

    override suspend fun saveMatch(match: Match): Long {
        val rowId = dao.upsert(match.toEntity())
        // @Upsert devuelve -1 cuando actualiza una fila existente.
        return if (match.id != 0L) match.id else rowId
    }

    override suspend fun deleteMatch(id: Long) = dao.delete(id)
}
