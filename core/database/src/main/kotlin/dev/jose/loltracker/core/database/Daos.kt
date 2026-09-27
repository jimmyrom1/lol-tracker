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
interface MatchDetailDao {

    @Query("SELECT * FROM match_details WHERE riot_match_id = :riotMatchId")
    suspend fun get(riotMatchId: String): MatchDetailEntity?

    @Query("SELECT * FROM match_details WHERE riot_match_id = :riotMatchId")
    fun observe(riotMatchId: String): Flow<MatchDetailEntity?>

    @Query("SELECT * FROM match_details")
    fun observeAll(): Flow<List<MatchDetailEntity>>

    /** Partidas importadas cuyo detalle falta (importadas antes de la v3). */
    @Query(
        "SELECT m.riot_match_id FROM matches m LEFT JOIN match_details d ON d.riot_match_id = m.riot_match_id " +
            "WHERE m.riot_match_id IS NOT NULL AND d.riot_match_id IS NULL",
    )
    suspend fun matchIdsWithoutDetail(): List<String>

    @Upsert
    suspend fun upsert(detail: MatchDetailEntity)

    @Upsert
    suspend fun upsertAll(details: List<MatchDetailEntity>)
}

@Dao
interface CacheDao {

    @Query("SELECT * FROM riot_cache WHERE cache_key = :key")
    suspend fun get(key: String): CacheEntity?

    @Upsert
    suspend fun put(entry: CacheEntity)
}

@Dao
interface ChampionDao {

    @Query("SELECT * FROM champions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ChampionEntity>>

    @Query("SELECT patch_version FROM champions LIMIT 1")
    suspend fun cachedPatchVersion(): String?

    @Query("SELECT patch_version FROM champions LIMIT 1")
    fun observePatchVersion(): Flow<String?>

    /** Catálogos guardados antes de la v3 no tienen el id numérico: hay que volver a descargarlos. */
    @Query("SELECT EXISTS(SELECT 1 FROM champions WHERE `key` = '')")
    suspend fun hasMissingKeys(): Boolean

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
