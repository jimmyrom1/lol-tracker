package dev.jose.loltracker.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import java.time.Duration
import java.time.Instant

@Entity(tableName = "matches", indices = [Index("played_at"), Index("champion_id")])
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
)

fun MatchEntity.toModel() = Match(
    id = id, championId = championId, championName = championName, role = role, queue = queue,
    result = result, kills = kills, deaths = deaths, assists = assists, creepScore = creepScore,
    duration = Duration.ofSeconds(durationSeconds), playedAt = playedAt, notes = notes,
)

fun Match.toEntity() = MatchEntity(
    id = id, championId = championId, championName = championName, role = role, queue = queue,
    result = result, kills = kills, deaths = deaths, assists = assists, creepScore = creepScore,
    durationSeconds = duration.seconds, playedAt = playedAt, notes = notes,
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
)

fun ChampionEntity.toModel() = Champion(
    id = id, name = name, title = title, iconUrl = iconUrl,
    tags = tags.split(',').filter { it.isNotBlank() },
)
