package dev.jose.loltracker.core.domain

import dev.jose.loltracker.core.model.MatchDetail
import dev.jose.loltracker.core.model.Role

/** Lo que se sabe del draft: tu rol y los campeones ya elegidos por cada equipo. */
data class DraftQuery(
    val role: Role? = null,
    val allies: Set<String> = emptySet(),
    val enemies: Set<String> = emptySet(),
)

data class Record(val wins: Int, val games: Int) {
    val losses: Int get() = games - wins
    val winRate: Double get() = if (games == 0) 0.0 else wins.toDouble() / games

    operator fun plus(other: Record) = Record(wins + other.wins, games + other.games)

    companion object {
        val EMPTY = Record(0, 0)
    }
}

data class PickSuggestion(
    val championId: String,
    /** Tus partidas con este campeón (en el rol pedido, si hay rol). */
    val overall: Record,
    /** Con este campeón, contra cada enemigo ya elegido con el que has coincidido. */
    val vsEnemies: Map<String, Record>,
    /** Con este campeón, junto a cada aliado ya elegido con el que has coincidido. */
    val withAllies: Map<String, Record>,
    /** Probabilidad de victoria estimada (0..1), suavizada para que 1-0 no parezca 100 %. */
    val score: Double,
)

/** Tu historial frente a (o junto a) un campeón, juegues lo que juegues. */
data class ChampionInsight(val championId: String, val record: Record)

data class DraftAdvice(
    val picks: List<PickSuggestion>,
    val enemies: List<ChampionInsight>,
    val allies: List<ChampionInsight>,
    /** Partidas del historial que se han tenido en cuenta (con el filtro de rol aplicado). */
    val sampleSize: Int,
)

/**
 * Recomienda qué elegir a partir de **tu propio historial**. No usa estadísticas globales: la API
 * gratuita no da para descargar millones de partidas, y además lo que funciona para ti es más
 * útil que la media de todo el mundo.
 *
 * La puntuación es un porcentaje de victorias *bayesiano*: se añaden [PRIOR_GAMES] partidas
 * imaginarias al 50 %, así un 1-0 (≈ 60 %) no gana a un 8-4 (≈ 62 %). Las partidas contra los
 * enemigos o junto a los aliados de este draft cuentan el doble, porque se parecen más a la que
 * vas a jugar.
 */
object DraftAdvisor {

    const val PRIOR_GAMES = 4.0

    private data class Game(val champion: String, val role: Role?, val win: Boolean, val allies: Set<String>, val enemies: Set<String>)

    fun advise(history: List<MatchDetail>, query: DraftQuery, limit: Int = 5): DraftAdvice {
        val games = history.mapNotNull { detail ->
            val me = detail.me ?: return@mapNotNull null
            Game(
                champion = me.championId,
                role = me.role,
                win = me.win,
                allies = detail.allies.map { it.championId }.toSet(),
                enemies = detail.enemies.map { it.championId }.toSet(),
            )
        }
        val inRole = if (query.role == null) games else games.filter { it.role == query.role }
        val taken = query.allies + query.enemies

        val picks = inRole.groupBy { it.champion }
            .filterKeys { it !in taken } // no se puede elegir un campeón que ya está en el draft
            .map { (champion, played) ->
                val vs = query.enemies.associateWith { e -> played.filter { e in it.enemies }.record() }.filterValues { it.games > 0 }
                val with = query.allies.associateWith { a -> played.filter { a in it.allies }.record() }.filterValues { it.games > 0 }
                val context = played.filter { g -> query.enemies.any { it in g.enemies } || query.allies.any { it in g.allies } }.record()
                PickSuggestion(
                    championId = champion,
                    overall = played.record(),
                    vsEnemies = vs,
                    withAllies = with,
                    score = smoothed(played.record() + context),
                )
            }
            .sortedWith(compareByDescending<PickSuggestion> { it.score }.thenByDescending { it.overall.games })
            .take(limit)

        return DraftAdvice(
            picks = picks,
            enemies = query.enemies.map { e -> ChampionInsight(e, games.filter { e in it.enemies }.record()) },
            allies = query.allies.map { a -> ChampionInsight(a, games.filter { a in it.allies }.record()) },
            sampleSize = inRole.size,
        )
    }

    fun smoothed(record: Record): Double = (record.wins + PRIOR_GAMES / 2) / (record.games + PRIOR_GAMES)

    private fun List<Game>.record() = Record(wins = count { it.win }, games = size)
}
