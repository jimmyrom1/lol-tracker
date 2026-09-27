package dev.jose.loltracker.core.data

import dev.jose.loltracker.core.database.ChampionDao
import dev.jose.loltracker.core.database.ChampionEntity
import dev.jose.loltracker.core.network.ChampionDto
import dev.jose.loltracker.core.network.ChampionsResponse
import dev.jose.loltracker.core.network.DataDragonApi
import dev.jose.loltracker.core.network.ImageDto
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataDragonChampionRepositoryTest {

    private class FakeApi(var latest: String = "16.19.1", var failing: Boolean = false) : DataDragonApi {
        var championCalls = 0
        override suspend fun versions(): List<String> {
            if (failing) throw IOException("sin conexión")
            return listOf(latest, "16.18.1")
        }

        override suspend fun champions(version: String, locale: String): ChampionsResponse {
            championCalls++
            return ChampionsResponse(
                mapOf("Ahri" to ChampionDto(id = "Ahri", key = "103", name = "Ahri", title = "la Vastaya", tags = listOf("Mage"), image = ImageDto("Ahri.png"))),
            )
        }
    }

    private class FakeDao : ChampionDao {
        val rows = MutableStateFlow<List<ChampionEntity>>(emptyList())
        override fun observeAll(): Flow<List<ChampionEntity>> = rows
        override suspend fun cachedPatchVersion() = rows.value.firstOrNull()?.patchVersion
        override fun observePatchVersion(): Flow<String?> = rows.map { it.firstOrNull()?.patchVersion }
        override suspend fun hasMissingKeys() = rows.value.any { it.key.isEmpty() }
        override suspend fun upsertAll(champions: List<ChampionEntity>) {
            rows.value = (rows.value.filterNot { r -> champions.any { it.id == r.id } } + champions).sortedBy { it.name }
        }
        override suspend fun deleteOtherVersions(patchVersion: String) {
            rows.value = rows.value.filter { it.patchVersion == patchVersion }
        }
    }

    private val api = FakeApi()
    private val dao = FakeDao()
    private val repository = DataDragonChampionRepository(api, dao, "https://cdn.test/")

    @Test
    fun downloadsCatalogOnFirstRefresh() = runTest {
        assertTrue(repository.refresh().isSuccess)
        val ahri = repository.observeChampions().first().single()
        assertEquals("https://cdn.test/cdn/16.19.1/img/champion/Ahri.png", ahri.iconUrl)
        assertEquals(listOf("Mage"), ahri.tags)
    }

    @Test
    fun skipsDownloadWhenPatchHasNotChanged() = runTest {
        repository.refresh()
        repository.refresh()
        assertEquals(1, api.championCalls)

        api.latest = "16.20.1"
        repository.refresh()
        assertEquals(2, api.championCalls)
        assertEquals("16.20.1", dao.cachedPatchVersion())
    }

    @Test
    fun offlineRefreshFailsButKeepsTheCache() = runTest {
        repository.refresh()
        api.failing = true

        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertEquals(1, repository.observeChampions().first().size)
    }
}
