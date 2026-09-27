package dev.jose.loltracker.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchDetail
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.core.model.TimelineSummary
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json

@Entity(
    tableName = "matches",
    indices = [Index("played_at"), Index("champion_id"), Index(value = ["riot_match_id"], unique = true)],
)
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @androidx.room.ColumnInfo(name = "champion_id") val championId: String,
    @androidx.room.ColumnInfo(name = "champion_name") val championName: String,
    val role: Role,
    val queue: Queue,
    val result: MatchResult,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    @androidx.room.ColumnInfo(name = "creep_score") val creepScore: Int,
    @androidx.room.ColumnInfo(name = "duration_seconds") val durationSeconds: Long,
    @androidx.room.ColumnInfo(name = "played_at") val playedAt: Instant,
    val notes: String,
    // Único: es lo que impide importar dos veces la misma partida (SQLite permite varios NULL).
    @androidx.room.ColumnInfo(name = "riot_match_id") val riotMatchId: String? = null,
)

fun MatchEntity.toModel() = Match(
    id = id, championId = championId, championName = championName, role = role, queue = queue,
    result = result, kills = kills, deaths = deaths, assists = assists, creepScore = creepScore,
    duration = Duration.ofSeconds(durationSeconds), playedAt = playedAt, notes = notes, riotMatchId = riotMatchId,
)

fun Match.toEntity() = MatchEntity(
    id = id, championId = championId, championName = championName, role = role, queue = queue,
    result = result, kills = kills, deaths = deaths, assists = assists, creepScore = creepScore,
    durationSeconds = duration.seconds, playedAt = playedAt, notes = notes, riotMatchId = riotMatchId,
)

/** Caché local de los campeones de Data Dragon, para que la app funcione sin conexión. */
@Entity(tableName = "champions")
data class ChampionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val title: String,
    @androidx.room.ColumnInfo(name = "icon_url") val iconUrl: String,
    val tags: String,
    /** Versión del parche de la que proceden los datos, p. ej. "16.19.1". */
    @androidx.room.ColumnInfo(name = "patch_version") val patchVersion: String,
    /** Id numérico de Riot. Vacío en catálogos descargados antes de la v3 (se vuelven a pedir). */
    @androidx.room.ColumnInfo(defaultValue = "") val key: String = "",
)

fun ChampionEntity.toModel() = Champion(
    id = id, name = name, title = title, iconUrl = iconUrl,
    tags = tags.split(',').filter { it.isNotBlank() },
    key = key,
)

/**
 * Detalle de una partida importada. Los 10 jugadores, los objetivos y la línea temporal se
 * guardan como JSON: nunca se consultan por columnas y así el esquema no depende de lo que
 * cambie Riot. Una partida jugada no cambia, así que se descarga una sola vez.
 */
@Entity(tableName = "match_details")
data class MatchDetailEntity(
    @PrimaryKey @androidx.room.ColumnInfo(name = "riot_match_id") val riotMatchId: String,
    @androidx.room.ColumnInfo(name = "my_puuid") val myPuuid: String,
    @androidx.room.ColumnInfo(name = "game_version") val gameVersion: String,
    @androidx.room.ColumnInfo(name = "participants_json") val participantsJson: String,
    @androidx.room.ColumnInfo(name = "teams_json") val teamsJson: String,
    @androidx.room.ColumnInfo(name = "timeline_json") val timelineJson: String?,
)

private val detailJson = Json { ignoreUnknownKeys = true }

fun MatchDetailEntity.toModel() = MatchDetail(
    riotMatchId = riotMatchId,
    myPuuid = myPuuid,
    gameVersion = gameVersion,
    participants = detailJson.decodeFromString(participantsJson),
    teams = detailJson.decodeFromString(teamsJson),
    timeline = timelineJson?.let { detailJson.decodeFromString<TimelineSummary>(it) },
)

fun MatchDetail.toEntity() = MatchDetailEntity(
    riotMatchId = riotMatchId,
    myPuuid = myPuuid,
    gameVersion = gameVersion,
    participantsJson = detailJson.encodeToString(participants),
    teamsJson = detailJson.encodeToString(teams),
    timelineJson = timeline?.let { detailJson.encodeToString(it) },
)

/** Respuestas de Riot que cambian poco (rango, maestría): se reutilizan durante un tiempo. */
@Entity(tableName = "riot_cache")
data class CacheEntity(
    @PrimaryKey @androidx.room.ColumnInfo(name = "cache_key") val cacheKey: String,
    val json: String,
    @androidx.room.ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
)
