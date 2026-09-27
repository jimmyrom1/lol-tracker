package dev.jose.loltracker.feature.matches

import app.cash.turbine.test
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.testing.FakeChampionRepository
import dev.jose.loltracker.core.testing.FakeMatchRepository
import dev.jose.loltracker.core.testing.MainDispatcherRule
import dev.jose.loltracker.core.testing.TestAnalyticsTracker
import dev.jose.loltracker.core.testing.TestData
import dev.jose.loltracker.feature.matches.list.MatchListUiState
import dev.jose.loltracker.feature.matches.list.MatchListViewModel
import dev.jose.loltracker.feature.matches.list.ResultFilter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MatchListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Madrid: una partida a las 23:30 UTC ya es "del día siguiente" en hora local.
    private val clock = Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneId.of("Europe/Madrid"))
    private val analytics = TestAnalyticsTracker()

    private val matches = FakeMatchRepository(
        listOf(
            TestData.match(id = 1, playedAt = Instant.parse("2026-09-25T18:00:00Z")),
            TestData.match(id = 2, champion = TestData.jinx, result = MatchResult.LOSS, playedAt = Instant.parse("2026-09-25T23:30:00Z")),
            TestData.match(id = 3, playedAt = Instant.parse("2026-09-26T20:00:00Z")),
        ),
    )

    private fun viewModel() = MatchListViewModel(matches, FakeChampionRepository(), analytics, clock)

    @Test
    fun groupsMatchesByLocalDayNewestFirst() = runTest {
        viewModel().uiState.test {
            val content = awaitItem() as MatchListUiState.Content
            assertEquals(
                listOf(LocalDate.parse("2026-09-26"), LocalDate.parse("2026-09-25")),
                content.sections.map { it.date },
            )
            // La partida de las 23:30 UTC cae el día 26 en Madrid.
            assertEquals(listOf(3L, 2L), content.sections.first().matches.map { it.id })
            assertEquals(3, content.totalMatches)
        }
    }

    @Test
    fun filterKeepsTotalButHidesOtherResults() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.setFilter(ResultFilter.LOSSES)
            val content = awaitItem() as MatchListUiState.Content
            assertEquals(listOf(2L), content.sections.flatMap { it.matches }.map { it.id })
            assertEquals(3, content.totalMatches)
            assertEquals(ResultFilter.LOSSES, content.filter)
        }
    }

    @Test
    fun deleteAndUndoRestoreTheSameMatch() = runTest {
        val vm = viewModel()
        val jinx = matches.current.first { it.id == 2L }

        vm.delete(jinx)
        assertEquals(listOf(1L, 3L), matches.current.map { it.id }.sorted())

        vm.undoDelete()
        assertEquals(jinx, matches.current.first { it.id == 2L })
        assertEquals(
            listOf("screen_view", "match_deleted", "match_delete_undone"),
            analytics.events.map { it.name },
        )
    }

    @Test
    fun undoTwiceOnlyRestoresOnce() = runTest {
        val vm = viewModel()
        vm.delete(matches.current.first())
        vm.undoDelete()
        vm.undoDelete()
        assertEquals(1, analytics.events.count { it.name == "match_delete_undone" })
    }
}
