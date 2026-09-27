package dev.jose.loltracker.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RiotApiTest {

    private val server = MockWebServer()
    private var key: String? = "RGAPI-test"
    private val sleeps = mutableListOf<Long>()
    private lateinit var api: RiotApi

    @Before
    fun setUp() {
        server.start()
        api = createRiotApi(OkHttpClient(), server.url("/").toString(), { key }, sleep = { sleeps += it })
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun sendsTheApiKeyAndEncodesTheRiotId() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"puuid":"abc","gameName":"jimmyrom","tagLine":"uarra"}""").build())

        assertEquals("abc", api.accountByRiotId("jimmy rom", "uarra").puuid)

        val request = server.takeRequest()
        assertEquals("RGAPI-test", request.headers["X-Riot-Token"])
        assertEquals("/riot/account/v1/accounts/by-riot-id/jimmy%20rom/uarra", request.url.encodedPath)
    }

    @Test
    fun parsesAMatchIgnoringTheHundredsOfFieldsWeDoNotUse() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """
                {"metadata":{"dataVersion":"2","matchId":"EUW1_7000000001","participants":["abc"]},
                 "info":{"gameCreation":1790000000000,"gameDuration":1834,"gameEndTimestamp":1790001900000,
                         "gameMode":"CLASSIC","queueId":420,
                         "participants":[{"puuid":"abc","championName":"MonkeyKing","championId":62,
                                          "teamPosition":"JUNGLE","individualPosition":"JUNGLE",
                                          "kills":7,"deaths":3,"assists":12,"totalMinionsKilled":40,
                                          "neutralMinionsKilled":150,"win":true,"goldEarned":13000,
                                          "gameEndedInEarlySurrender":false,"challenges":{"kda":6.3}}]}}
                """.trimIndent(),
            ).build(),
        )

        val match = api.match("EUW1_7000000001")

        assertEquals("EUW1_7000000001", match.metadata.matchId)
        assertEquals(420, match.info.queueId)
        val me = match.info.participants.single()
        assertEquals("MonkeyKing", me.championName)
        assertEquals(150, me.neutralMinionsKilled)
    }

    @Test
    fun retriesOnceAfterRateLimitHonouringRetryAfter() = runTest {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "2").build())
        server.enqueue(MockResponse.Builder().body("""["EUW1_1","EUW1_2"]""").build())

        assertEquals(listOf("EUW1_1", "EUW1_2"), api.matchIds("abc", count = 2))
        assertEquals(listOf(2_000L), sleeps)
        assertEquals("/lol/match/v5/matches/by-puuid/abc/ids", server.takeRequest().url.encodedPath)
    }

    @Test
    fun withoutAKeyNoRequestIsMade() = runTest {
        key = " "
        try {
            api.matchIds("abc")
            fail("debería fallar sin API key")
        } catch (e: MissingRiotApiKeyException) {
            assertTrue(e.message!!.contains("API key"))
        }
        assertEquals(0, server.requestCount)
    }
}
