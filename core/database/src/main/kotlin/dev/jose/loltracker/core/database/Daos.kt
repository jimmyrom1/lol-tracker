package dev.jose.loltracker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {

    @Query("SELECT * FROM matches ORDER BY played_at DESC")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun getById(id: Long): MatchEntity?

    @Upsert
    suspend fun upsert(match: MatchEntity): Long

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun delete(id: Long)

    /** De los ids de Riot recibidos, cuáles ya están guardados (para no volver a descargarlos). */
    @Query("SELECT riot_match_id FROM matches WHERE riot_match_id IN (:riotMatchIds)")
    suspend fun existingRiotMatchIds(riotMatchIds: List<String>): List<String>

    /** IGNORE: si dos importaciones se solapan, el índice único descarta la repetida sin fallar. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(matches: List<MatchEntity>): List<Long>
}

@Dao
interface ChampionDao {

    @Query("SELECT * FROM champions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ChampionEntity>>

    @Query("SELECT patch_version FROM champions LIMIT 1")
    suspend fun cachedPatchVersion(): String?

    @Upsert
    suspend fun upsertAll(champions: List<ChampionEntity>)

    @Query("DELETE FROM champions WHERE patch_version != :patchVersion")
    suspend fun deleteOtherVersions(patchVersion: String)

    /** Sustituye el catálogo de forma atómica: nunca se ve una lista a medio actualizar. */
    @Transaction
    suspend fun replaceAll(champions: List<ChampionEntity>, patchVersion: String) {
        upsertAll(champions)
        deleteOtherVersions(patchVersion)
    }
}
