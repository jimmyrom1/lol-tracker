package dev.jose.loltracker.feature.matches

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotResult
import dev.jose.loltracker.core.testing.FakeChampionRepository
import dev.jose.loltracker.core.testing.FakeMatchDetailRepository
import dev.jose.loltracker.core.testing.FakeMatchRepository
import dev.jose.loltracker.core.testing.MainDispatcherRule
import dev.jose.loltracker.core.testing.TestAnalyticsTracker
import dev.jose.loltracker.core.testing.TestData
import dev.jose.loltracker.feature.matches.detail.MatchDetailUiState
import dev.jose.loltracker.feature.matches.detail.MatchDetailViewModel
import dev.jose.loltracker.feature.matches.detail.TimelineState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MatchDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val imported = TestData.match(id = 1, champion = TestData.jinx).copy(riotMatchId = "EUW1_1")
    private val manual = TestData.match(id = 2)
    private val matches = FakeMatchRepository(listOf(imported, manual))
    private val details = FakeMatchDetailRepository(listOf(TestData.detail("EUW1_1", "Jinx", win = true, enemies = listOf("LeeSin"))))

    private fun viewModel(id: Long) = MatchDetailViewModel(
        SavedStateHandle(mapOf(MatchDetailViewModel.MATCH_ID_ARG to id)),
        matches,
        details,
        FakeChampionRepository(),
        TestAnalyticsTracker(),
    )

    @Test
    fun importedMatchShowsItsDetailAndRequestsTheTimelineOnce() = runTest {
        viewModel(1).uiState.test {
            val content = expectMostRecentItem() as MatchDetailUiState.Content
            assertEquals("Jinx", content.detail?.me?.championId)
            assertEquals(listOf("LeeSin"), content.detail?.enemies?.map { it.championId })
            assertEquals("16.19.1", content.patchVersion)
            assertEquals(TimelineState.Loaded, content.timeline)
        }
        assertEquals(1, details.timelineRequests)
    }

    @Test
    fun manualMatchHasNoDetailAndNoRequests() = runTest {
        viewModel(2).uiState.test {
            val content = expectMostRecentItem() as MatchDetailUiState.Content
            assertNull(content.detail)
        }
        assertEquals(0, details.timelineRequests)
    }

    @Test
    fun timelineErrorsCanBeRetried() = runTest {
        details.timelineResult = RiotResult.Failure(RiotError.NETWORK)
        val vm = viewModel(1)
        vm.uiState.test {
            assertEquals(TimelineState.Failed(RiotError.NETWORK), (expectMostRecentItem() as MatchDetailUiState.Content).timeline)

            details.timelineResult = RiotResult.Success(Unit)
            vm.retryTimeline()
            assertEquals(TimelineState.Loaded, (expectMostRecentItem() as MatchDetailUiState.Content).timeline)
        }
    }

    @Test
    fun missingMatchIsNotFound() = runTest {
        viewModel(99).uiState.test {
            assertEquals(MatchDetailUiState.NotFound, expectMostRecentItem())
        }
    }
}
