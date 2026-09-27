package dev.jose.loltracker.core.domain

import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role

data class PlayerStats(
    val games: Int,
    val wins: Int,
    val averageKda: Double,
    val averageCsPerMinute: Double,
    val currentStreak: Streak?,
    val recentForm: List<MatchResult>,
    val byRole: List<RoleStats>,
    val byChampion: List<ChampionStats>,
) {
    val losses: Int get() = games - wins
    val winRate: Double get() = if (games == 0) 0.0 else wins.toDouble() / games

    companion object {
        val EMPTY = PlayerStats(0, 0, 0.0, 0.0, null, emptyList(), emptyList(), emptyList())
    }
}

data class Streak(val result: MatchResult, val length: Int)

data class RoleStats(val role: Role, val games: Int, val wins: Int) {
    val winRate: Double get() = wins.toDouble() / games
}

data class ChampionStats(
    val championId: String,
    val championName: String,
    val games: Int,
    val wins: Int,
    val averageKda: Double,
) {
    val winRate: Double get() = wins.toDouble() / games
}

/**
 * Calcula las estadísticas a partir de las partidas. Es una función pura (sin Android,
 * sin base de datos), así que se testea en milisegundos con cualquier caso límite.
 */
object StatsCalculator {

    const val RECENT_FORM_SIZE = 10

    fun calculate(matches: List<Match>): PlayerStats {
        if (matches.isEmpty()) return PlayerStats.EMPTY
        val newestFirst = matches.sortedByDescending { it.playedAt }

        return PlayerStats(
            games = matches.size,
            wins = matches.count { it.result == MatchResult.WIN },
            // KDA medio "de la suma", no la media de KDAs: una partida 10/0/10 no debe pesar
            // lo mismo que diez partidas normales. Es como lo calculan op.gg y similares.
            averageKda = aggregateKda(matches),
            averageCsPerMinute = matches.map { it.csPerMinute }.average(),
            currentStreak = currentStreak(newestFirst),
            recentForm = newestFirst.take(RECENT_FORM_SIZE).map { it.result },
            // En ARAM no hay calles: el rol no significa nada y falsearía la tabla.
            byRole = matches.filter { it.queue != Queue.ARAM }.groupBy { it.role }
                .map { (role, games) -> RoleStats(role, games.size, games.count { it.result == MatchResult.WIN }) }
                .sortedWith(compareByDescending<RoleStats> { it.games }.thenBy { it.role.ordinal }),
            byChampion = matches.groupBy { it.championId }
                .map { (id, games) ->
                    ChampionStats(
                        championId = id,
                        championName = games.first().championName,
                        games = games.size,
                        wins = games.count { it.result == MatchResult.WIN },
                        averageKda = aggregateKda(games),
                    )
                }
                .sortedWith(compareByDescending<ChampionStats> { it.games }.thenByDescending { it.winRate }),
        )
    }

    private fun aggregateKda(matches: List<Match>): Double {
        val takedowns = matches.sumOf { it.kills + it.assists }
        val deaths = matches.sumOf { it.deaths }.coerceAtLeast(1)
        return takedowns.toDouble() / deaths
    }

    private fun currentStreak(newestFirst: List<Match>): Streak? {
        val last = newestFirst.firstOrNull()?.result ?: return null
        val length = newestFirst.takeWhile { it.result == last }.size
        return Streak(last, length)
    }
}
