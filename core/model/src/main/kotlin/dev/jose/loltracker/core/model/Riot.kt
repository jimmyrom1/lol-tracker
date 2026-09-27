package dev.jose.loltracker.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/** Estadísticas de un jugador en una partida importada de Riot (los 10 jugadores se guardan). */
@Serializable
data class ParticipantStats(
    val puuid: String,
    val participantId: Int,
    val teamId: Int,
    val riotId: String,
    val championId: String,
    val role: Role?,
    val champLevel: Int,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val creepScore: Int,
    val gold: Int,
    val damageToChampions: Int,
    val damageTaken: Int,
    val visionScore: Int,
    val wardsPlaced: Int,
    /** Ids de objeto de Data Dragon; 0 es una casilla vacía. El último es el trinket. */
    val items: List<Int>,
    val summonerSpells: List<Int>,
    val killParticipation: Double?,
    val win: Boolean,
)

@Serializable
data class TeamObjectives(val teamId: Int, val win: Boolean, val towers: Int, val dragons: Int, val barons: Int, val heralds: Int)

/** Resumen de la línea temporal: basta para la gráfica de oro y las diferencias en línea. */
@Serializable
data class TimelineSummary(
    /** Oro de tu equipo menos el del rival, minuto a minuto (índice = minuto). */
    val teamGoldDiff: List<Int>,
    /** Frente a tu rival directo de línea; null si no se pudo emparejar (ARAM, roles cambiados). */
    val laneGoldDiffAt15: Int?,
    val laneCsDiffAt10: Int?,
    val laneOpponentChampion: String?,
)

data class MatchDetail(
    val riotMatchId: String,
    val myPuuid: String,
    val gameVersion: String,
    val participants: List<ParticipantStats>,
    val teams: List<TeamObjectives>,
    val timeline: TimelineSummary?,
) {
    val me: ParticipantStats? get() = participants.firstOrNull { it.puuid == myPuuid }
    val allies: List<ParticipantStats> get() = me?.let { m -> participants.filter { it.teamId == m.teamId && it.puuid != m.puuid } }.orEmpty()
    val enemies: List<ParticipantStats> get() = me?.let { m -> participants.filter { it.teamId != m.teamId } }.orEmpty()
}

@Serializable
enum class RankedQueue { SOLO, FLEX }

@Serializable
data class RankEntry(
    val queue: RankedQueue,
    /** "SILVER", "GOLD"... tal cual lo devuelve Riot. */
    val tier: String,
    /** "I" a "IV" (vacío en Master o superior). */
    val division: String,
    val leaguePoints: Int,
    val wins: Int,
    val losses: Int,
) {
    val games: Int get() = wins + losses
    val winRate: Double get() = if (games == 0) 0.0 else wins.toDouble() / games
}

@Serializable
data class ChampionMastery(val championKey: String, val level: Int, val points: Int)

data class PlayerProfile(
    val riotId: String,
    val profileIconUrl: String?,
    val summonerLevel: Long,
    val ranks: List<RankEntry>,
    val topMasteries: List<ChampionMastery>,
    val fetchedAt: Instant,
)

data class LivePlayer(
    val puuid: String?,
    val riotId: String,
    val teamId: Int,
    val championKey: String,
    val soloRank: RankEntry?,
    /** Maestría con el campeón que lleva en esta partida. */
    val mastery: ChampionMastery?,
    val isMe: Boolean,
)

data class LiveGame(val gameId: Long, val queueId: Int, val startedAt: Instant?, val players: List<LivePlayer>) {
    val me: LivePlayer? get() = players.firstOrNull { it.isMe }
}

/** Por qué ha fallado una llamada a Riot, en términos que se le pueden explicar al usuario. */
enum class RiotError { NOT_CONFIGURED, MISSING_API_KEY, INVALID_API_KEY, ACCOUNT_NOT_FOUND, RATE_LIMITED, NETWORK }
