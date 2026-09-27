package dev.jose.loltracker.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MatchDaoTest {

    private lateinit var db: LolDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(champion: String, playedAt: String) = MatchEntity(
        championId = champion, championName = champion, role = Role.MID, queue = Queue.RANKED_SOLO,
        result = MatchResult.WIN, kills = 1, deaths = 2, assists = 3, creepScore = 150,
        durationSeconds = 1800, playedAt = Instant.parse(playedAt), notes = "",
    )

    @Test
    fun matchesAreObservedNewestFirstAndRoundTripAllFields() = runTest {
        val dao = db.matchDao()
        dao.observeAll().test {
            assertEquals(emptyList<MatchEntity>(), awaitItem())

            dao.upsert(entity("Ahri", "2026-09-01T10:00:00Z"))
            awaitItem()
            val id = dao.upsert(entity("Jinx", "2026-09-02T10:00:00Z"))

            val items = awaitItem()
            assertEquals(listOf("Jinx", "Ahri"), items.map { it.championId })
            assertEquals(entity("Jinx", "2026-09-02T10:00:00Z").copy(id = id), items.first())
        }
    }

    @Test
    fun upsertUpdatesAndDeleteRemoves() = runTest {
        val dao = db.matchDao()
        val id = dao.upsert(entity("Ahri", "2026-09-01T10:00:00Z"))
        dao.upsert(entity("Ahri", "2026-09-01T10:00:00Z").copy(id = id, kills = 12))
        assertEquals(12, dao.getById(id)?.kills)

        dao.delete(id)
        assertNull(dao.getById(id))
    }

    @Test
    fun championCatalogIsReplacedAtomically() = runTest {
        val dao = db.championDao()
        dao.replaceAll(listOf(ChampionEntity("Ahri", "Ahri", "", "", "Mage", "16.18.1")), "16.18.1")
        dao.replaceAll(
            listOf(
                ChampionEntity("Ahri", "Ahri", "", "", "Mage", "16.19.1"),
                ChampionEntity("Zyra", "Zyra", "", "", "Mage", "16.19.1"),
            ),
            "16.19.1",
        )
        dao.observeAll().test {
            assertEquals(listOf("Ahri", "Zyra"), awaitItem().map { it.id })
        }
        assertEquals("16.19.1", dao.cachedPatchVersion())
    }
}
