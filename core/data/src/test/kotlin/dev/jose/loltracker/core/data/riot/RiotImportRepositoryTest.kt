package dev.jose.loltracker.core.data.riot

import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.database.MatchDao
import dev.jose.loltracker.core.database.MatchEntity
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.core.network.createRiotApi
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RiotImportRepositoryTest {

    private val puuid = "puuid-jimmy"
    private val server = MockWebServer()
    private val settings = FakeSettings()
    private val dao = FakeMatchDao()
    private var accountStatus = 200
    private val matchIds = listOf("EUW1_3", "EUW1_2", "EUW1_1")
    private lateinit var repository: DefaultRiotImportRepository

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path.startsWith("/riot/account") ->
                        MockResponse.Builder().code(accountStatus).body("""{"puuid":"$puuid"}""").build()
                    path.endsWith("/ids") -> MockResponse.Builder().body(matchIds.joinToString(",", "[", "]") { "\"$it\"" }).build()
                    else -> MockResponse.Builder().body(matchJson(path.substringAfterLast('/'))).build()
                }
            }
        }
        server.start()
        val api = createRiotApi(OkHttpClient(), server.url("/").toString(), settings::apiKey, sleep = {})
        repository = DefaultRiotImportRepository(api, dao, FakeChampions(), settings)
    }

    @After
    fun tearDown() = server.close()

    /** EUW1_1 es un remake de 3 minutos; EUW1_2 una ARAM; EUW1_3 una clasificatoria de jungla. */
    private fun matchJson(id: String): String {
        val (queue, seconds, position, champion) = when (id) {
            "EUW1_1" -> listOf("420", "190", "MIDDLE", "Ahri")
            "EUW1_2" -> listOf("450", "1100", "", "FiddleSticks")
            else -> listOf("420", "1834", "JUNGLE", "MonkeyKing")
        }
        return """
            {"metadata":{"matchId":"$id"},
             "info":{"gameCreation":1790000000000,"gameDuration":$seconds,"queueId":$queue,
                     "participants":[
                       {"puuid":"otro","championName":"Jinx","kills":0,"deaths":9,"assists":1,"totalMinionsKilled":10,"win":false},
                       {"puuid":"$puuid","championName":"$champion","teamPosition":"$position",
                        "kills":7,"deaths":3,"assists":12,"totalMinionsKilled":40,"neutralMinionsKilled":150,"win":true}]}}
        """.trimIndent()
    }

    @Test
    fun importsNewMatchesSkippingRemakes() = runTest {
        val result = repository.import(RiotId("jimmyrom", "uarra"))

        assertEquals(ImportResult.Success(imported = 2, alreadyImported = 0, skipped = 1), result)
        val jungle = dao.rows.value.single { it.riotMatchId == "EUW1_3" }
        assertEquals("MonkeyKing", jungle.championId)
        assertEquals("Wukong", jungle.championName) // nombre traducido del catálogo
        assertEquals(Role.JUNGLE, jungle.role)
        assertEquals(Queue.RANKED_SOLO, jungle.queue)
        assertEquals(MatchResult.WIN, jungle.result)
        assertEquals(190, jungle.creepScore) // súbditos + monstruos neutrales
        assertEquals(Duration.ofSeconds(1834).seconds, jungle.durationSeconds)

        val aram = dao.rows.value.single { it.riotMatchId == "EUW1_2" }
        assertEquals("Fiddlesticks", aram.championId) // capitalización de Data Dragon
        assertEquals(Queue.ARAM, aram.queue)
        assertEquals("jimmyrom#uarra", settings.riotId)
    }

    @Test
    fun aSecondImportOnlyDownloadsWhatIsNew() = runTest {
        repository.import(RiotId("jimmyrom", "uarra"))
        val requestsAfterFirst = server.requestCount

        val result = repository.import(RiotId("jimmyrom", "uarra"))

        // Cuenta + lista de ids + el remake (que no se guarda y por tanto se vuelve a consultar).
        assertEquals(3, server.requestCount - requestsAfterFirst)
        assertEquals(ImportResult.Success(imported = 0, alreadyImported = 2, skipped = 1), result)
        assertEquals(2, dao.rows.value.size)
    }

    @Test
    fun unknownAccountAndBadKeyAreReported() = runTest {
        accountStatus = 404
        assertEquals(ImportResult.Failure(ImportError.ACCOUNT_NOT_FOUND), repository.import(RiotId("nadie", "euw")))
        accountStatus = 403
        assertEquals(ImportResult.Failure(ImportError.INVALID_API_KEY), repository.import(RiotId("jimmyrom", "uarra")))
    }

    @Test
    fun withoutKeyNothingIsRequested() = runTest {
        settings.key = null
        assertEquals(ImportResult.Failure(ImportError.MISSING_API_KEY), repository.import(RiotId("jimmyrom", "uarra")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun riotIdParsing() {
        assertEquals(RiotId("jimmyrom", "uarra"), RiotId.parse("  jimmyrom # uarra "))
        assertEquals(RiotId("Lee Sin Main", "EUW"), RiotId.parse("Lee Sin Main#EUW"))
        assertNull(RiotId.parse("jimmyrom"))
        assertNull(RiotId.parse("a#b#c"))
        assertNull(RiotId.parse("jimmyrom#x"))
    }

    private class FakeSettings : RiotSettings {
        var key: String? = "RGAPI-test"
        override var riotId: String? = null
        override var userApiKey: String? = null
        override fun apiKey() = key
    }

    private class FakeChampions : ChampionRepository {
        override fun observeChampions(): Flow<List<Champion>> = flowOf(
            listOf(
                Champion("MonkeyKing", "Wukong", "el Rey Mono", "", emptyList()),
                Champion("Fiddlesticks", "Fiddlesticks", "el terror ancestral", "", emptyList()),
            ),
        )

        override suspend fun refresh() = Result.success(Unit)
    }

    private class FakeMatchDao : MatchDao {
        val rows = MutableStateFlow<List<MatchEntity>>(emptyList())
        override fun observeAll() = rows
        override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }
        override suspend fun upsert(match: MatchEntity) = error("no se usa")
        override suspend fun delete(id: Long) = Unit
        override suspend fun existingRiotMatchIds(riotMatchIds: List<String>) =
            rows.value.mapNotNull { it.riotMatchId }.filter { it in riotMatchIds }

        override suspend fun insertAll(matches: List<MatchEntity>): List<Long> = matches.map { m ->
            if (rows.value.any { it.riotMatchId == m.riotMatchId }) {
                -1L
            } else {
                val id = rows.value.size + 1L
                rows.value = rows.value + m.copy(id = id)
                id
            }
        }
    }
}
