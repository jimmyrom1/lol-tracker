package dev.jose.loltracker.core.domain

import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.MatchResult.LOSS
import dev.jose.loltracker.core.model.MatchResult.WIN
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsCalculatorTest {

    private var minute = 0L

    private fun match(
        result: MatchResult,
        champion: String = "Ahri",
        role: Role = Role.MID,
        k: Int = 5, d: Int = 5, a: Int = 5,
        cs: Int = 180,
        minutes: Long = 30,
    ) = Match(
        championId = champion, championName = champion, role = role, queue = Queue.RANKED_SOLO,
        result = result, kills = k, deaths = d, assists = a, creepScore = cs,
        duration = Duration.ofMinutes(minutes),
        playedAt = Instant.parse("2026-09-01T18:00:00Z").plusSeconds(60 * minute++),
    )

    @Test
    fun `no matches gives empty stats`() {
        val stats = StatsCalculator.calculate(emptyList())
        assertEquals(0, stats.games)
        assertEquals(0.0, stats.winRate, 0.0)
        assertNull(stats.currentStreak)
    }

    @Test
    fun `win rate and totals`() {
        val stats = StatsCalculator.calculate(listOf(match(WIN), match(WIN), match(LOSS), match(WIN)))
        assertEquals(4, stats.games)
        assertEquals(3, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(0.75, stats.winRate, 1e-9)
    }

    @Test
    fun `average KDA is aggregated, not the mean of each game`() {
        // 10/0/10 (KDA 20) + 2/8/2 (KDA 0,5): media de KDAs = 10,25 pero la real es 24/8 = 3
        val stats = StatsCalculator.calculate(listOf(match(WIN, k = 10, d = 0, a = 10), match(LOSS, k = 2, d = 8, a = 2)))
        assertEquals(3.0, stats.averageKda, 1e-9)
    }

    @Test
    fun `KDA without deaths divides by one`() {
        val m = match(WIN, k = 7, d = 0, a = 3)
        assertEquals(10.0, m.kda, 1e-9)
        assertEquals(true, m.isPerfectKda)
    }

    @Test
    fun `cs per minute`() {
        assertEquals(6.0, match(WIN, cs = 180, minutes = 30).csPerMinute, 1e-9)
    }

    @Test
    fun `current streak counts the most recent consecutive results`() {
        val stats = StatsCalculator.calculate(listOf(match(WIN), match(LOSS), match(WIN), match(WIN), match(WIN)))
        assertEquals(Streak(WIN, 3), stats.currentStreak)
    }

    @Test
    fun `recent form is newest first and capped`() {
        val matches = List(12) { i -> match(if (i % 3 == 0) LOSS else WIN) }
        val stats = StatsCalculator.calculate(matches.shuffled())
        assertEquals(StatsCalculator.RECENT_FORM_SIZE, stats.recentForm.size)
        assertEquals(matches.reversed().take(10).map { it.result }, stats.recentForm)
    }

    @Test
    fun `stats by champion sorted by games played`() {
        val stats = StatsCalculator.calculate(
            listOf(
                match(WIN, "Ahri"), match(LOSS, "Ahri"),
                match(WIN, "Jinx", k = 10, d = 2, a = 6), match(WIN, "Jinx"), match(LOSS, "Jinx"),
                match(WIN, "Lux"),
            ),
        )
        assertEquals(listOf("Jinx", "Ahri", "Lux"), stats.byChampion.map { it.championId })
        val jinx = stats.byChampion.first()
        assertEquals(3, jinx.games)
        assertEquals(2.0 / 3, jinx.winRate, 1e-9)
        assertEquals((10 + 6 + 5 + 5 + 5 + 5).toDouble() / (2 + 5 + 5), jinx.averageKda, 1e-9)
    }

    @Test
    fun `stats by role`() {
        val stats = StatsCalculator.calculate(
            listOf(match(WIN, role = Role.ADC), match(LOSS, role = Role.ADC), match(WIN, role = Role.TOP)),
        )
        assertEquals(listOf(Role.ADC, Role.TOP), stats.byRole.map { it.role })
        assertEquals(0.5, stats.byRole.first().winRate, 1e-9)
    }
}
