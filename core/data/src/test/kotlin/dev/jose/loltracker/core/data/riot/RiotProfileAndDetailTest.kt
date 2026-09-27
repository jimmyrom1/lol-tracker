package dev.jose.loltracker.core.data.riot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.jose.loltracker.core.database.ChampionEntity
import dev.jose.loltracker.core.database.LolDatabase
import dev.jose.loltracker.core.database.toModel
import dev.jose.loltracker.core.model.RankedQueue
import dev.jose.loltracker.core.network.RiotApi
import dev.jose.loltracker.core.network.RiotPlatformApi
import dev.jose.loltracker.core.network.RiotRateLimiter
import dev.jose.loltracker.core.network.createRiotService
import dev.jose.loltracker.core.network.riotClient
import java.time.Duration
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RiotProfileAndDetailTest {

    private val riot = FakeRiotServer().start()
    private val settings = FakeSettings().apply {
        riotId = "jimmyrom#uarra"
        puuid = "puuid-jimmy"
    }
    private val clock = TestClock()
    private lateinit var db: LolDatabase
    private lateinit var importer: DefaultRiotImportRepository
    private lateinit var profiles: DefaultRiotProfileRepository
    private lateinit var details: DefaultMatchDetailRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Las dos APIs apuntan al mismo servidor falso y comparten limitador, como en la app.
        val client = riotClient(OkHttpClient(), settings::apiKey, RiotRateLimiter(), sleep = {})
        val api = createRiotService<RiotApi>(client, riot.url())
        val platform = createRiotService<RiotPlatformApi>(client, riot.url())
        importer = DefaultRiotImportRepository(api, db.matchDao(), db.matchDetailDao(), db.cacheDao(), FakeChampions(), settings, clock)
        profiles = DefaultRiotProfileRepository(platform, importer, settings, db.cacheDao(), db.championDao(), clock)
        details = DefaultMatchDetailRepository(api, db.matchDetailDao())
    }

    @After
    fun tearDown() {
        db.close()
        riot.close()
    }

    @Test
    fun profileIsCachedForTenMinutes() = runTest {
        db.championDao().replaceAll(listOf(ChampionEntity("Gwen", "Gwen", "", "", "", "16.19.1", "887")), "16.19.1")

        val profile = (profiles.profile() as RiotResult.Success).value

        assertEquals(312, profile.summonerLevel)
        assertEquals("https://ddragon.leagueoflegends.com/cdn/16.19.1/img/profileicon/29.png", profile.profileIconUrl)
        // Solo las colas de clasificatoria (Arena y otras se ignoran).
        assertEquals(listOf(RankedQueue.SOLO), profile.ranks.map { it.queue })
        assertEquals(226, profile.ranks.single().wins)
        assertEquals("887", profile.topMasteries.single().championKey)
        assertEquals(3, riot.paths.size) // invocador + liga + maestría

        clock.now = clock.now.plus(Duration.ofMinutes(9))
        profiles.profile()
        assertEquals("sigue en caché", 3, riot.paths.size)

        profiles.profile(forceRefresh = true)
        assertEquals(6, riot.paths.size)
    }

    @Test
    fun notInGameIsNotAnError() = runTest {
        assertEquals(RiotResult.Success(null), profiles.liveGame())
    }

    @Test
    fun liveGameBringsRankAndMasteryOfEachPlayerAndCachesThem() = runTest {
        riot.inGame = true

        val game = (profiles.liveGame() as RiotResult.Success).value!!

        val me = game.me!!
        assertEquals("887", me.championKey)
        assertEquals(38, me.mastery?.level)
        assertEquals("SILVER", me.soloRank?.tier)
        val rival = game.players.single { !it.isMe }
        assertNull("nunca ha jugado Lee Sin (404)", rival.mastery)
        val firstLookup = riot.paths.size
        assertEquals(5, firstLookup) // partida + (liga + maestría) × 2

        profiles.liveGame()
        assertEquals("rango y maestría salen de la caché", firstLookup + 1, riot.paths.size)
    }

    @Test
    fun timelineIsSummarisedOnceAndStored() = runTest {
        importer.import(RiotId("jimmyrom", "uarra"))
        riot.paths.clear()

        assertEquals(RiotResult.Success(Unit), details.loadTimeline("EUW1_3"))
        val timeline = db.matchDetailDao().get("EUW1_3")!!.toModel().timeline!!

        assertEquals(17, timeline.teamGoldDiff.size) // minutos 0 a 16
        assertEquals(10, timeline.laneCsDiffAt10)
        assertEquals(800, timeline.laneGoldDiffAt15)
        assertEquals("LeeSin", timeline.laneOpponentChampion)
        assertTrue(timeline.teamGoldDiff.last() > 0)

        details.loadTimeline("EUW1_3")
        assertEquals("la segunda vez no se pide", 1, riot.paths.size)
    }
}
