package dev.jose.loltracker.core.data.riot

import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchDetail
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.ParticipantStats
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.core.model.TeamObjectives
import dev.jose.loltracker.core.model.TimelineSummary
import dev.jose.loltracker.core.network.ParticipantFrameDto
import dev.jose.loltracker.core.network.RiotMatchDto
import dev.jose.loltracker.core.network.TimelineDto
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

    /** Los 10 jugadores con todo lo que enseña la pantalla de detalle. */
    fun toDetail(dto: RiotMatchDto, puuid: String, catalog: Map<String, Champion>): MatchDetail = MatchDetail(
        riotMatchId = dto.metadata.matchId,
        myPuuid = puuid,
        gameVersion = dto.info.gameVersion,
        participants = dto.info.participants.map { p ->
            ParticipantStats(
                puuid = p.puuid,
                participantId = p.participantId,
                teamId = p.teamId,
                riotId = if (p.riotIdGameName.isBlank()) "" else "${p.riotIdGameName}#${p.riotIdTagline}",
                championId = catalog[p.championName.lowercase()]?.id ?: p.championName,
                role = p.teamPosition.takeIf { it.isNotBlank() }?.let(::role),
                champLevel = p.champLevel,
                kills = p.kills,
                deaths = p.deaths,
                assists = p.assists,
                creepScore = p.totalMinionsKilled + p.neutralMinionsKilled,
                gold = p.goldEarned,
                damageToChampions = p.totalDamageDealtToChampions,
                damageTaken = p.totalDamageTaken,
                visionScore = p.visionScore,
                wardsPlaced = p.wardsPlaced,
                items = p.items,
                summonerSpells = listOf(p.summoner1Id, p.summoner2Id),
                killParticipation = p.challenges?.killParticipation,
                win = p.win,
            )
        },
        teams = dto.info.teams.map { t ->
            val o = t.objectives
            TeamObjectives(
                teamId = t.teamId,
                win = t.win,
                towers = o?.tower?.kills ?: 0,
                dragons = o?.dragon?.kills ?: 0,
                barons = o?.baron?.kills ?: 0,
                heralds = o?.riftHerald?.kills ?: 0,
            )
        },
        timeline = null,
    )

    /**
     * Resume la línea temporal: diferencia de oro entre equipos minuto a minuto, y oro a los 15
     * y CS a los 10 frente al rival de la misma posición.
     */
    fun summarizeTimeline(timeline: TimelineDto, detail: MatchDetail): TimelineSummary {
        val me = requireNotNull(detail.me)
        val opponent = me.role?.let { role -> detail.enemies.firstOrNull { it.role == role } }
        val teamOf = detail.participants.associate { it.participantId to it.teamId }
        val frames = timeline.info.frames

        val goldDiff = frames.map { frame ->
            frame.participantFrames.values.sumOf { f ->
                if (teamOf[f.participantId] == me.teamId) f.totalGold else -f.totalGold
            }
        }
        fun frameAt(minute: Int) = frames.getOrNull(minute)?.participantFrames
        fun lane(minute: Int, value: (ParticipantFrameDto) -> Int): Int? {
            val frame = frameAt(minute) ?: return null
            val mine = frame[me.participantId.toString()] ?: return null
            val theirs = opponent?.let { frame[it.participantId.toString()] } ?: return null
            return value(mine) - value(theirs)
        }
        return TimelineSummary(
            teamGoldDiff = goldDiff,
            laneGoldDiffAt15 = lane(15) { it.totalGold },
            laneCsDiffAt10 = lane(10) { it.minionsKilled + it.jungleMinionsKilled },
            laneOpponentChampion = opponent?.championId,
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
