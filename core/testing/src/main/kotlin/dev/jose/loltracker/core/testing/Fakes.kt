package dev.jose.loltracker.core.testing

import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.MatchRepository
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.RiotId
import dev.jose.loltracker.core.data.riot.RiotImportRepository
import dev.jose.loltracker.core.data.riot.RiotSettings
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Sustituye Dispatchers.Main (que no existe en la JVM) durante los tests de ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeMatchRepository(initial: List<Match> = emptyList()) : MatchRepository {
    private val matches = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val current: List<Match> get() = matches.value

    override fun observeMatches(): Flow<List<Match>> = matches.map { list -> list.sortedByDescending { it.playedAt } }

    override suspend fun getMatch(id: Long) = matches.value.firstOrNull { it.id == id }

    override suspend fun saveMatch(match: Match): Long {
        val id = if (match.id == 0L) nextId++ else match.id
        matches.value = matches.value.filterNot { it.id == id } + match.copy(id = id)
        return id
    }

    override suspend fun deleteMatch(id: Long) {
        matches.value = matches.value.filterNot { it.id == id }
    }
}

class FakeChampionRepository(champions: List<Champion> = TestData.champions) : ChampionRepository {
    val champions = MutableStateFlow(champions)
    var refreshResult: Result<Unit> = Result.success(Unit)
    var refreshCalls = 0

    override fun observeChampions(): Flow<List<Champion>> = champions

    override suspend fun refresh(): Result<Unit> {
        refreshCalls++
        return refreshResult
    }
}

class FakeRiotSettings(
    override var riotId: String? = null,
    override var userApiKey: String? = null,
    /** Simula una key compilada desde local.properties. */
    var builtInKey: String? = null,
) : RiotSettings {
    override fun apiKey() = userApiKey?.takeIf { it.isNotBlank() } ?: builtInKey
}

class FakeRiotImportRepository(var result: ImportResult = ImportResult.Success(0, 0, 0)) : RiotImportRepository {
    val requests = mutableListOf<RiotId>()
    override suspend fun import(riotId: RiotId, count: Int): ImportResult {
        requests += riotId
        return result
    }
}

class TestAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events += event
    }
}

object TestData {
    val ahri = Champion("Ahri", "Ahri", "la Vastaya de nueve colas", "", listOf("Mage"))
    val jinx = Champion("Jinx", "Jinx", "la bala perdida", "", listOf("Marksman"))
    val leeSin = Champion("LeeSin", "Lee Sin", "el monje ciego", "", listOf("Fighter"))
    val champions = listOf(ahri, jinx, leeSin)

    fun match(
        id: Long = 0,
        champion: Champion = ahri,
        result: MatchResult = MatchResult.WIN,
        role: Role = Role.MID,
        playedAt: Instant = Instant.parse("2026-09-20T18:00:00Z"),
        kills: Int = 7,
        deaths: Int = 3,
        assists: Int = 9,
    ) = Match(
        id = id, championId = champion.id, championName = champion.name, role = role,
        queue = Queue.RANKED_SOLO, result = result, kills = kills, deaths = deaths, assists = assists,
        creepScore = 190, duration = Duration.ofMinutes(31), playedAt = playedAt,
    )
}
