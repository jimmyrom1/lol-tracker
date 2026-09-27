package dev.jose.loltracker.feature.draft

import app.cash.turbine.test
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotResult
import dev.jose.loltracker.core.domain.Record
import dev.jose.loltracker.core.model.LiveGame
import dev.jose.loltracker.core.model.LivePlayer
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.core.testing.FakeChampionRepository
import dev.jose.loltracker.core.testing.FakeMatchDetailRepository
import dev.jose.loltracker.core.testing.FakeRiotProfileRepository
import dev.jose.loltracker.core.testing.MainDispatcherRule
import dev.jose.loltracker.core.testing.TestAnalyticsTracker
import dev.jose.loltracker.core.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DraftViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Jinx 2-0 contra Lee Sin; Ahri 0-1 contra Lee Sin (como ADC para el ejemplo).
    private val details = FakeMatchDetailRepository(
        listOf(
            TestData.detail("EUW1_1", "Jinx", win = true, enemies = listOf("LeeSin")),
            TestData.detail("EUW1_2", "Jinx", win = true, enemies = listOf("LeeSin")),
            TestData.detail("EUW1_3", "Ahri", win = false, enemies = listOf("LeeSin")),
        ),
    )
    private val profiles = FakeRiotProfileRepository()
    private fun viewModel() = DraftViewModel(details, FakeChampionRepository(), profiles, TestAnalyticsTracker())

    @Test
    fun suggestionsFollowTheDraft() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(3, awaitItem().historySize)
            vm.setRole(Role.ADC)
            awaitItem()
            vm.addEnemy("LeeSin")

            val state = awaitItem()
            assertEquals(listOf("Jinx", "Ahri"), state.advice.picks.map { it.championId })
            assertEquals(mapOf("LeeSin" to Record(2, 2)), state.advice.picks.first().vsEnemies)
            assertEquals(Record(2, 3), state.advice.enemies.single().record)
        }
    }

    @Test
    fun atMostFiveEnemies() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            listOf("A", "B", "C", "D", "E", "F").forEach { vm.addEnemy(it) }
            assertEquals(5, expectMostRecentItem().query.enemies.size)
        }
    }

    @Test
    fun theLiveGameFillsTheDraft() = runTest {
        profiles.liveGameResult = RiotResult.Success(
            LiveGame(
                gameId = 1, queueId = 420, startedAt = null,
                players = listOf(
                    LivePlayer("me", "jimmyrom#uarra", 100, "222", null, null, isMe = true),
                    LivePlayer("a", "Aliado#EUW", 100, "103", null, null, isMe = false),
                    LivePlayer("r", "Rival#EUW", 200, "64", null, null, isMe = false),
                ),
            ),
        )
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.findLiveGame()
            val state = expectMostRecentItem()

            assertTrue(state.live is LiveGameState.Found)
            assertEquals(setOf("LeeSin"), state.query.enemies)
            assertEquals(setOf("Ahri"), state.query.allies)
            assertEquals(mapOf("LeeSin" to Record(2, 3)), state.liveRecords)
        }
    }

    @Test
    fun notInGameAndErrorsAreShown() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.findLiveGame()
            assertEquals(LiveGameState.NotInGame, expectMostRecentItem().live)

            profiles.liveGameResult = RiotResult.Failure(RiotError.INVALID_API_KEY)
            vm.findLiveGame()
            assertEquals(LiveGameState.Failed(RiotError.INVALID_API_KEY), expectMostRecentItem().live)
        }
    }
}
