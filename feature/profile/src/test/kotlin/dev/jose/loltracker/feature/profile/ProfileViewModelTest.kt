package dev.jose.loltracker.feature.profile

import app.cash.turbine.test
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotId
import dev.jose.loltracker.core.data.riot.RiotResult
import dev.jose.loltracker.core.model.ChampionMastery
import dev.jose.loltracker.core.model.PlayerProfile
import dev.jose.loltracker.core.model.RankEntry
import dev.jose.loltracker.core.model.RankedQueue
import dev.jose.loltracker.core.testing.FakeChampionRepository
import dev.jose.loltracker.core.testing.FakeRiotImportRepository
import dev.jose.loltracker.core.testing.FakeRiotProfileRepository
import dev.jose.loltracker.core.testing.FakeRiotSettings
import dev.jose.loltracker.core.testing.MainDispatcherRule
import dev.jose.loltracker.core.testing.TestAnalyticsTracker
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profile = PlayerProfile(
        riotId = "jimmyrom#uarra",
        profileIconUrl = null,
        summonerLevel = 312,
        ranks = listOf(RankEntry(RankedQueue.SOLO, "SILVER", "IV", 0, 226, 233)),
        topMasteries = listOf(ChampionMastery("103", 38, 390_649)),
        fetchedAt = Instant.parse("2026-09-27T10:00:00Z"),
    )
    private val profiles = FakeRiotProfileRepository(profileResult = RiotResult.Success(profile))
    private val importer = FakeRiotImportRepository(ImportResult.Success(imported = 3, alreadyImported = 17, skipped = 0))
    private val settings = FakeRiotSettings(riotId = "jimmyrom#uarra")

    private fun viewModel() = ProfileViewModel(profiles, importer, settings, FakeChampionRepository(), TestAnalyticsTracker())

    @Test
    fun withoutAccountItAsksToImportFirst() = runTest {
        settings.riotId = null
        viewModel().uiState.test { assertEquals(ProfileLoad.NotConfigured, expectMostRecentItem().load) }
    }

    @Test
    fun loadsProfileAndMapsMasteriesByNumericKey() = runTest {
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(ProfileLoad.Loaded(profile), state.load)
            assertEquals("Ahri", state.championsByKey["103"]?.name)
        }
    }

    @Test
    fun refreshForcesANewRequest() = runTest {
        val vm = viewModel()
        vm.refresh()
        assertEquals(1, profiles.forcedRefreshes)
    }

    @Test
    fun manualSyncCanImportTheLastHundred() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.sync(100)
            val state = expectMostRecentItem()
            assertEquals(importer.result, state.syncResult)
            assertEquals(listOf(RiotId("jimmyrom", "uarra")), importer.requests)

            vm.consumeSyncResult()
            assertEquals(null, awaitItem().syncResult)
        }
    }

    @Test
    fun errorsAreShown() = runTest {
        profiles.profileResult = RiotResult.Failure(RiotError.INVALID_API_KEY)
        viewModel().uiState.test {
            val load = expectMostRecentItem().load
            assertTrue(load is ProfileLoad.Failed)
            assertEquals(RiotError.INVALID_API_KEY, (load as ProfileLoad.Failed).error)
        }
    }
}
