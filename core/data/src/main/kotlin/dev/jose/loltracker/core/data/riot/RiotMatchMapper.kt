package dev.jose.loltracker.core.data.riot

import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.core.network.RiotMatchDto
import java.time.Duration
import java.time.Instant

/** Convierte una partida de match-v5 en una [Match] desde el punto de vista del jugador [puuid]. */
internal object RiotMatchMapper {

    /** Por debajo de esto es un remake: no cuenta como partida jugada. */
    val MIN_DURATION: Duration = Duration.ofMinutes(5)

    fun toMatch(dto: RiotMatchDto, puuid: String, catalog: Map<String, Champion>): Match? {
        val info = dto.info
        val me = info.participants.firstOrNull { it.puuid == puuid } ?: return null
        val duration = Duration.ofSeconds(info.gameDuration)
        if (me.gameEndedInEarlySurrender || duration < MIN_DURATION) return null

        // match-v5 no siempre usa la misma capitalización que Data Dragon ("FiddleSticks" / "Fiddlesticks").
        val champion = catalog[me.championName.lowercase()]
        return Match(
            championId = champion?.id ?: me.championName,
            championName = champion?.name ?: me.championName,
            role = role(me.teamPosition.ifBlank { me.individualPosition }),
            queue = queue(info.queueId),
            result = if (me.win) MatchResult.WIN else MatchResult.LOSS,
            kills = me.kills,
            deaths = me.deaths,
            assists = me.assists,
            creepScore = me.totalMinionsKilled + me.neutralMinionsKilled,
            duration = duration,
            playedAt = Instant.ofEpochMilli(info.gameEndTimestamp ?: (info.gameCreation + duration.toMillis())),
            riotMatchId = dto.metadata.matchId,
        )
    }

    /** Ids de cola: https://static.developer.riotgames.com/docs/lol/queues.json */
    fun queue(queueId: Int): Queue = when (queueId) {
        420 -> Queue.RANKED_SOLO
        440 -> Queue.RANKED_FLEX
        400, 430, 480, 490 -> Queue.NORMAL // reclutamiento, selección a ciegas, Swiftplay, rápida
        450 -> Queue.ARAM
        else -> Queue.OTHER
    }

    /**
     * En ARAM y modos sin calles la posición viene vacía o como "Invalid". Se guarda como MID
     * por tener algún valor, pero las estadísticas por rol ignoran esas colas.
     */
    fun role(position: String): Role = when (position.uppercase()) {
        "TOP" -> Role.TOP
        "JUNGLE" -> Role.JUNGLE
        "BOTTOM" -> Role.ADC
        "UTILITY" -> Role.SUPPORT
        else -> Role.MID
    }
}
