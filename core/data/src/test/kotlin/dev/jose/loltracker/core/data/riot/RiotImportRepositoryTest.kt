package dev.jose.loltracker.core.data.riot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.database.LolDatabase
import dev.jose.loltracker.core.database.MatchEntity
import dev.jose.loltracker.core.database.toModel
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.core.network.createRiotApi
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reloj que el test puede adelantar. */
class TestClock(var now: Instant = Instant.parse("2026-09-27T10:00:00Z")) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

class FakeSettings : RiotSettings {
    var key: String? = "RGAPI-test"
    override var riotId: String? = null
    override var userApiKey: String? = null
    override var puuid: String? = null
    override var lastSyncAt: Instant? = null
    override fun apiKey() = key
}

class FakeChampions : ChampionRepository {
    override fun observeChampions(): Flow<List<Champion>> = flowOf(
        listOf(
            Champion("MonkeyKing", "Wukong", "el Rey Mono", "", emptyList(), key = "62"),
            Champion("Fiddlesticks", "Fiddlesticks", "el terror ancestral", "", emptyList(), key = "9"),
            Champion("LeeSin", "Lee Sin", "el monje ciego", "", emptyList(), key = "64"),
            Champion("Gwen", "Gwen", "la costurera sagrada", "", emptyList(), key = "887"),
        ),
    )

    override fun observePatchVersion(): Flow<String?> = flowOf("16.19.1")

    override suspend fun refresh() = Result.success(Unit)
}

@RunWith(RobolectricTestRunner::class)
class RiotImportRepositoryTest {

    private val riot = FakeRiotServer().start()
    private val settings = FakeSettings()
    private val clock = TestClock()
    private lateinit var db: LolDatabase
    private lateinit var repository: DefaultRiotImportRepository

    private val jimmy = RiotId("jimmyrom", "uarra")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val api = createRiotApi(OkHttpClient(), riot.url(), settings::apiKey, sleep = {})
        repository = DefaultRiotImportRepository(api, db.matchDao(), db.matchDetailDao(), db.cacheDao(), FakeChampions(), settings, clock)
    }

    @After
    fun tearDown() {
        db.close()
        riot.close()
    }

    private suspend fun matches() = db.matchDao().observeAll().first().map { it.toModel() }

    @Test
    fun importsNewMatchesWithTheirDetailAndSkipsRemakes() = runTest {
        val result = repository.import(jimmy)

        assertEquals(ImportResult.Success(imported = 2, alreadyImported = 0, skipped = 1), result)
        val jungle = matches().single { it.riotMatchId == "EUW1_3" }
        assertEquals("MonkeyKing", jungle.championId)
        assertEquals("Wukong", jungle.championName) // nombre traducido del catálogo
        assertEquals(Role.JUNGLE, jungle.role)
        assertEquals(Queue.RANKED_SOLO, jungle.queue)
        assertEquals(MatchResult.WIN, jungle.result)
        assertEquals(190, jungle.creepScore) // súbditos + monstruos neutrales
        assertEquals(Duration.ofSeconds(1834), jungle.duration)
        val aram = matches().single { it.riotMatchId == "EUW1_2" }
        assertEquals(Queue.ARAM, aram.queue)
        assertEquals("Fiddlesticks", aram.championId) // capitalización de Data Dragon

        val detail = db.matchDetailDao().get("EUW1_3")!!.toModel()
        assertEquals(2, detail.participants.size)
        assertEquals("jimmyrom#uarra", detail.me?.riotId)
        assertEquals(0.61, detail.me?.killParticipation!!, 1e-9)
        assertEquals(listOf(11, 4), detail.me?.summonerSpells)
        assertEquals(9, detail.teams.first { it.teamId == 100 }.towers)
        assertNull("la línea temporal se pide al abrir el detalle", detail.timeline)

        assertEquals("jimmyrom#uarra", settings.riotId)
        assertEquals(riot.puuid, settings.puuid)
        assertEquals(clock.now, settings.lastSyncAt)
    }

    @Test
    fun aSecondImportOnlyAsksForTheListOfIds() = runTest {
        repository.import(jimmy)
        riot.paths.clear()

        val result = repository.import(jimmy)

        // Ni la cuenta (el PUUID está guardado) ni el remake (se recuerda que se descartó).
        assertEquals(listOf("/lol/match/v5/matches/by-puuid/${riot.puuid}/ids"), riot.paths)
        assertEquals(ImportResult.Success(imported = 0, alreadyImported = 2, skipped = 0), result)
    }

    @Test
    fun onlyTheNewMatchIsDownloaded() = runTest {
        repository.import(jimmy)
        riot.paths.clear()
        riot.matchIds = listOf("EUW1_4") + riot.matchIds

        val result = repository.import(jimmy)

        assertEquals(1, riot.paths.count { it.endsWith("/EUW1_4") })
        assertEquals(2, riot.paths.size) // lista de ids + la partida nueva
        assertEquals(1, (result as ImportResult.Success).imported)
    }

    @Test
    fun matchesImportedBeforeDetailsExistedAreBackfilled() = runTest {
        // Como quedaron las partidas importadas con la versión anterior: sin fila de detalle.
        db.matchDao().insertAll(
            listOf(
                MatchEntity(
                    championId = "MonkeyKing", championName = "Wukong", role = Role.JUNGLE, queue = Queue.RANKED_SOLO,
                    result = MatchResult.WIN, kills = 7, deaths = 3, assists = 12, creepScore = 190, durationSeconds = 1834,
                    playedAt = Instant.parse("2026-09-20T10:00:00Z"), notes = "", riotMatchId = "EUW1_OLD",
                ),
            ),
        )
        riot.matchIds = emptyList()

        repository.import(jimmy)

        assertNotNull(db.matchDetailDao().get("EUW1_OLD"))
        assertEquals(1, matches().size) // no se duplica la partida
    }

    @Test
    fun syncIfStaleDoesNothingForFifteenMinutes() = runTest {
        settings.riotId = jimmy.toString()
        repository.syncSaved()
        riot.paths.clear()

        clock.now = clock.now.plus(Duration.ofMinutes(10))
        assertNull(repository.syncSavedIfStale())
        assertEquals(emptyList<String>(), riot.paths)

        clock.now = clock.now.plus(Duration.ofMinutes(6))
        assertNotNull(repository.syncSavedIfStale())
        assertEquals(1, riot.paths.size)
    }

    @Test
    fun syncWithoutAnAccountIsNotConfigured() = runTest {
        assertEquals(ImportResult.Failure(RiotError.NOT_CONFIGURED), repository.syncSaved())
        assertEquals(0, riot.server.requestCount)
    }

    @Test
    fun unknownAccountAndBadKeyAreReported() = runTest {
        riot.accountStatus = 404
        assertEquals(ImportResult.Failure(RiotError.ACCOUNT_NOT_FOUND), repository.import(RiotId("nadie", "euw")))
        riot.accountStatus = 403
        assertEquals(ImportResult.Failure(RiotError.INVALID_API_KEY), repository.import(jimmy))
    }

    @Test
    fun withoutKeyNothingIsRequested() = runTest {
        settings.key = null
        assertEquals(ImportResult.Failure(RiotError.MISSING_API_KEY), repository.import(jimmy))
        assertEquals(0, riot.server.requestCount)
    }

    @Test
    fun riotIdParsing() {
        assertEquals(RiotId("jimmyrom", "uarra"), RiotId.parse("  jimmyrom # uarra "))
        assertEquals(RiotId("Lee Sin Main", "EUW"), RiotId.parse("Lee Sin Main#EUW"))
        assertNull(RiotId.parse("jimmyrom"))
        assertNull(RiotId.parse("a#b#c"))
        assertNull(RiotId.parse("jimmyrom#x"))
    }
}
