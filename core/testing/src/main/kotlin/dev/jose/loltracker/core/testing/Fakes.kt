package dev.jose.loltracker.core.testing

import dev.jose.loltracker.core.analytics.AnalyticsEvent
import dev.jose.loltracker.core.analytics.AnalyticsTracker
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.data.MatchRepository
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.MatchDetailRepository
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotProfileRepository
import dev.jose.loltracker.core.data.riot.RiotResult
import dev.jose.loltracker.core.data.riot.RiotId
import dev.jose.loltracker.core.data.riot.RiotImportRepository
import dev.jose.loltracker.core.data.riot.RiotSettings
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.LiveGame
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchDetail
import dev.jose.loltracker.core.model.ParticipantStats
import dev.jose.loltracker.core.model.PlayerProfile
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.core.model.TeamObjectives
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

    val patchVersion = MutableStateFlow<String?>("16.19.1")
    override fun observePatchVersion(): Flow<String?> = patchVersion

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
    override var puuid: String? = null,
    override var lastSyncAt: java.time.Instant? = null,
) : RiotSettings {
    override fun apiKey() = userApiKey?.takeIf { it.isNotBlank() } ?: builtInKey
}

class FakeRiotImportRepository(var result: ImportResult = ImportResult.Success(0, 0, 0)) : RiotImportRepository {
    val requests = mutableListOf<RiotId>()
    override suspend fun import(riotId: RiotId, count: Int): ImportResult {
        requests += riotId
        return result
    }

    var syncs = 0
    override suspend fun syncSaved(): ImportResult {
        syncs++
        return result
    }

    override suspend fun syncSavedIfStale(minInterval: java.time.Duration): ImportResult? = syncSaved()
}

class FakeMatchDetailRepository(initial: List<MatchDetail> = emptyList()) : MatchDetailRepository {
    val details = MutableStateFlow(initial)
    var timelineResult: RiotResult<Unit> = RiotResult.Success(Unit)
    var timelineRequests = 0

    override fun observe(riotMatchId: String): Flow<MatchDetail?> = details.map { list -> list.firstOrNull { it.riotMatchId == riotMatchId } }
    override fun observeAll(): Flow<List<MatchDetail>> = details
    override suspend fun loadTimeline(riotMatchId: String): RiotResult<Unit> {
        timelineRequests++
        return timelineResult
    }
}

class FakeRiotProfileRepository(
    var profileResult: RiotResult<PlayerProfile> = RiotResult.Failure(RiotError.NOT_CONFIGURED),
    var liveGameResult: RiotResult<LiveGame?> = RiotResult.Success(null),
) : RiotProfileRepository {
    var forcedRefreshes = 0
    override suspend fun profile(forceRefresh: Boolean): RiotResult<PlayerProfile> {
        if (forceRefresh) forcedRefreshes++
        return profileResult
    }
    override suspend fun liveGame(): RiotResult<LiveGame?> = liveGameResult
}

class TestAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events += event
    }
}

object TestData {
    fun participant(puuid: String, team: Int, champion: String, role: Role? = null, win: Boolean = true) = ParticipantStats(
        puuid = puuid, participantId = 0, teamId = team, riotId = "$puuid#EUW", championId = champion, role = role,
        champLevel = 18, kills = 5, deaths = 3, assists = 7, creepScore = 180, gold = 11000, damageToChampions = 20000,
        damageTaken = 15000, visionScore = 25, wardsPlaced = 10, items = listOf(6676, 0, 0, 0, 0, 0, 3364),
        summonerSpells = listOf(4, 14), killParticipation = 0.5, win = win,
    )

    /** Partida importada vista por "me": mi campeón, mis aliados y mis rivales. */
    fun detail(
        riotMatchId: String,
        me: String,
        win: Boolean,
        role: Role = Role.ADC,
        allies: List<String> = emptyList(),
        enemies: List<String> = emptyList(),
    ) = MatchDetail(
        riotMatchId = riotMatchId,
        myPuuid = "me",
        gameVersion = "16.19.1",
        participants = listOf(participant("me", 100, me, role, win)) +
            allies.mapIndexed { i, c -> participant("ally$i", 100, c, win = win) } +
            enemies.mapIndexed { i, c -> participant("enemy$i", 200, c, win = !win) },
        teams = listOf(TeamObjectives(100, win, 8, 3, 1, 1), TeamObjectives(200, !win, 3, 1, 0, 0)),
        timeline = null,
    )

    val ahri = Champion("Ahri", "Ahri", "la Vastaya de nueve colas", "", listOf("Mage"), key = "103")
    val jinx = Champion("Jinx", "Jinx", "la bala perdida", "", listOf("Marksman"), key = "222")
    val leeSin = Champion("LeeSin", "Lee Sin", "el monje ciego", "", listOf("Fighter"), key = "64")
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
