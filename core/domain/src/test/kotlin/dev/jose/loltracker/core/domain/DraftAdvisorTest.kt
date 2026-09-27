package dev.jose.loltracker.core.domain

import dev.jose.loltracker.core.model.MatchDetail
import dev.jose.loltracker.core.model.ParticipantStats
import dev.jose.loltracker.core.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftAdvisorTest {

    private var nextId = 0

    private fun player(puuid: String, team: Int, champion: String, role: Role?, win: Boolean) = ParticipantStats(
        puuid = puuid, participantId = 0, teamId = team, riotId = "", championId = champion, role = role,
        champLevel = 18, kills = 0, deaths = 0, assists = 0, creepScore = 0, gold = 0, damageToChampions = 0,
        damageTaken = 0, visionScore = 0, wardsPlaced = 0, items = emptyList(), summonerSpells = emptyList(),
        killParticipation = null, win = win,
    )

    private fun game(
        me: String,
        win: Boolean,
        role: Role = Role.ADC,
        allies: List<String> = listOf("Thresh"),
        enemies: List<String> = listOf("Azir"),
    ) = MatchDetail(
        riotMatchId = "EUW1_${nextId++}",
        myPuuid = "me",
        gameVersion = "16.19",
        participants = listOf(player("me", 100, me, role, win)) +
            allies.mapIndexed { i, c -> player("a$i", 100, c, null, win) } +
            enemies.mapIndexed { i, c -> player("e$i", 200, c, null, !win) },
        teams = emptyList(),
        timeline = null,
    )

    @Test
    fun `a single lucky win does not beat a solid record`() {
        val history = listOf(game("Veigar", true)) + List(12) { game("Jinx", it < 8) }

        val picks = DraftAdvisor.advise(history, DraftQuery(role = Role.ADC)).picks

        assertEquals(listOf("Jinx", "Veigar"), picks.map { it.championId })
        assertEquals(Record(8, 12), picks.first().overall)
        assertEquals((8 + 2.0) / (12 + 4), picks.first().score, 1e-9)
        assertEquals(0.6, picks.last().score, 1e-9) // 1-0 → (1+2)/(1+4)
    }

    @Test
    fun `games against the enemies in this draft weigh double`() {
        // Caitlyn y Jinx tienen el mismo 2-2 en general, pero Caitlyn ganó las dos contra Zed.
        val history = listOf(
            game("Caitlyn", true, enemies = listOf("Zed")), game("Caitlyn", true, enemies = listOf("Zed")),
            game("Caitlyn", false), game("Caitlyn", false),
            game("Jinx", true), game("Jinx", true),
            game("Jinx", false, enemies = listOf("Zed")), game("Jinx", false, enemies = listOf("Zed")),
        )

        val advice = DraftAdvisor.advise(history, DraftQuery(role = Role.ADC, enemies = setOf("Zed")))

        assertEquals("Caitlyn", advice.picks.first().championId)
        assertEquals(mapOf("Zed" to Record(2, 2)), advice.picks.first().vsEnemies)
        assertEquals(mapOf("Zed" to Record(0, 2)), advice.picks.last().vsEnemies)
        assertEquals(listOf(ChampionInsight("Zed", Record(2, 4))), advice.enemies)
    }

    @Test
    fun `role filter and already picked champions are respected`() {
        val history = listOf(
            game("Garen", true, role = Role.TOP),
            game("Jinx", false),
            game("Ashe", true),
        )

        val advice = DraftAdvisor.advise(history, DraftQuery(role = Role.ADC, enemies = setOf("Ashe")))

        assertEquals(listOf("Jinx"), advice.picks.map { it.championId }) // Garen es top; Ashe ya la tiene el rival
        assertEquals(2, advice.sampleSize)
    }

    @Test
    fun `ally synergy is reported`() {
        val history = listOf(game("Jinx", true, allies = listOf("Lulu")), game("Jinx", true, allies = listOf("Lulu")), game("Jinx", false))

        val advice = DraftAdvisor.advise(history, DraftQuery(allies = setOf("Lulu")))

        assertEquals(mapOf("Lulu" to Record(2, 2)), advice.picks.single().withAllies)
        assertEquals(listOf(ChampionInsight("Lulu", Record(2, 2))), advice.allies)
    }

    @Test
    fun `no history gives no suggestions`() {
        val advice = DraftAdvisor.advise(emptyList(), DraftQuery(enemies = setOf("Zed")))
        assertTrue(advice.picks.isEmpty())
        assertEquals(listOf(ChampionInsight("Zed", Record.EMPTY)), advice.enemies)
    }
}
