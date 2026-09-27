package dev.jose.loltracker.feature.stats

import app.cash.turbine.test
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.testing.FakeChampionRepository
import dev.jose.loltracker.core.testing.FakeMatchRepository
import dev.jose.loltracker.core.testing.MainDispatcherRule
import dev.jose.loltracker.core.testing.TestAnalyticsTracker
import dev.jose.loltracker.core.testing.TestData
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val matches = FakeMatchRepository(
        listOf(
            TestData.match(id = 1, result = MatchResult.WIN, playedAt = Instant.parse("2026-09-20T18:00:00Z")),
            TestData.match(id = 2, result = MatchResult.LOSS, playedAt = Instant.parse("2026-09-21T18:00:00Z")),
            TestData.match(id = 3, result = MatchResult.WIN, playedAt = Instant.parse("2026-09-22T18:00:00Z"))
                .copy(queue = Queue.ARAM),
        ),
    )
    private val analytics = TestAnalyticsTracker()

    private fun viewModel() = StatsViewModel(matches, FakeChampionRepository(), analytics)

    @Test
    fun statsCoverAllQueuesByDefault() = runTest {
        viewModel().uiState.test {
            val content = awaitItem() as StatsUiState.Content
            assertNull(content.queue)
            assertEquals(3, content.stats.games)
            assertEquals(listOf(Queue.RANKED_SOLO, Queue.ARAM), content.availableQueues)
            assertEquals("screen_view", analytics.events.single().name)
        }
    }

    @Test
    fun filteringByQueueRecalculates() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.selectQueue(Queue.RANKED_SOLO)
            val content = awaitItem() as StatsUiState.Content
            assertEquals(2, content.stats.games)
            assertEquals(0.5, content.stats.winRate, 1e-9)
        }
    }

    @Test
    fun deletingTheLastMatchOfTheSelectedQueueFallsBackToAll() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.selectQueue(Queue.ARAM)
            assertEquals(1, (awaitItem() as StatsUiState.Content).stats.games)

            matches.deleteMatch(3)
            val content = awaitItem() as StatsUiState.Content
            assertNull(content.queue)
            assertEquals(2, content.stats.games)
        }
    }

    @Test
    fun noMatchesMeansNoQueues() = runTest {
        StatsViewModel(FakeMatchRepository(), FakeChampionRepository(), analytics).uiState.test {
            assertTrue((awaitItem() as StatsUiState.Content).availableQueues.isEmpty())
        }
    }
}
