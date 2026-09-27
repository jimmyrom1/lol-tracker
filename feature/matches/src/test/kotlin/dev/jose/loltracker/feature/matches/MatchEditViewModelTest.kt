package dev.jose.loltracker.feature.matches

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.jose.loltracker.core.domain.MatchField
import dev.jose.loltracker.core.domain.ValidationError
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.core.testing.FakeChampionRepository
import dev.jose.loltracker.core.testing.FakeMatchRepository
import dev.jose.loltracker.core.testing.MainDispatcherRule
import dev.jose.loltracker.core.testing.TestAnalyticsTracker
import dev.jose.loltracker.core.testing.TestData
import dev.jose.loltracker.feature.matches.edit.MatchEditEvent
import dev.jose.loltracker.feature.matches.edit.MatchEditViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MatchEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-09-27T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val matches = FakeMatchRepository()
    private val analytics = TestAnalyticsTracker()

    private fun viewModel(matchId: Long = 0L) = MatchEditViewModel(
        SavedStateHandle(mapOf(MatchEditViewModel.MATCH_ID_ARG to matchId)),
        matches,
        FakeChampionRepository(),
        analytics,
        clock,
    )

    private fun MatchEditViewModel.fillValidForm() {
        onChampionSelected(TestData.jinx)
        onRoleSelected(Role.ADC)
        onQueueSelected(Queue.RANKED_FLEX)
        onResultSelected(MatchResult.LOSS)
        onKillsChange("8")
        onDeathsChange("4")
        onAssistsChange("6")
        onCreepScoreChange("245")
        onDurationChange("32")
        onNotesChange("  Buen farmeo, mal posicionamiento en teamfights  ")
    }

    @Test
    fun newMatchStartsEmptyWithoutErrors() {
        val state = viewModel().uiState.value
        assertTrue(state.isNew)
        assertFalse(state.isLoading)
        assertEquals(now, state.form.playedAt)
        assertTrue(state.errors.isEmpty())
    }

    @Test
    fun savingAnEmptyFormShowsErrorsAndSavesNothing() {
        val vm = viewModel()
        vm.save()

        val errors = vm.uiState.value.errors
        assertEquals(ValidationError.REQUIRED, errors[MatchField.CHAMPION])
        assertEquals(ValidationError.REQUIRED, errors[MatchField.KILLS])
        assertTrue(matches.current.isEmpty())
        assertEquals("match_form_invalid", analytics.events.last().name)
    }

    @Test
    fun errorsAreRecalculatedLiveAfterTheFirstAttempt() {
        val vm = viewModel()
        vm.onKillsChange("7")
        assertTrue("antes de guardar no se muestran errores", vm.uiState.value.errors.isEmpty())

        vm.save()
        assertTrue(MatchField.DEATHS in vm.uiState.value.errors)

        vm.onDeathsChange("3")
        assertFalse(MatchField.DEATHS in vm.uiState.value.errors)
    }

    @Test
    fun onlyDigitsAreAccepted() {
        val vm = viewModel()
        vm.onKillsChange("1a2-")
        assertEquals("12", vm.uiState.value.form.kills)
    }

    @Test
    fun validFormIsSavedAndEmitsSaved() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.fillValidForm()
            vm.save()
            assertEquals(MatchEditEvent.Saved, awaitItem())
        }

        val saved = matches.current.single()
        assertEquals("Jinx", saved.championId)
        assertEquals(Role.ADC, saved.role)
        assertEquals(Queue.RANKED_FLEX, saved.queue)
        assertEquals(MatchResult.LOSS, saved.result)
        assertEquals(245, saved.creepScore)
        assertEquals(Duration.ofMinutes(32), saved.duration)
        assertEquals("Buen farmeo, mal posicionamiento en teamfights", saved.notes)
        assertEquals("match_created", analytics.events.last().name)
    }

    @Test
    fun editingLoadsTheMatchAndKeepsItsId() = runTest {
        val id = matches.saveMatch(TestData.match(champion = TestData.leeSin, kills = 3))
        val vm = viewModel(id)

        val form = vm.uiState.value.form
        assertFalse(vm.uiState.value.isNew)
        assertEquals("LeeSin", form.champion?.id)
        assertEquals("3", form.kills)

        vm.events.test {
            vm.onKillsChange("11")
            vm.save()
            assertEquals(MatchEditEvent.Saved, awaitItem())
        }
        assertEquals(11, matches.current.single { it.id == id }.kills)
        assertEquals("match_updated", analytics.events.last().name)
    }

    @Test
    fun missingMatchEmitsNotFound() = runTest {
        val vm = viewModel(matchId = 99)
        vm.events.test { assertEquals(MatchEditEvent.NotFound, awaitItem()) }
    }

    @Test
    fun pickingADateKeepsTheTimeButNeverGoesIntoTheFuture() {
        val vm = viewModel()
        vm.onDateSelected(LocalDate.parse("2026-09-20"))
        assertEquals(Instant.parse("2026-09-20T15:00:00Z"), vm.uiState.value.form.playedAt)

        vm.onDateSelected(LocalDate.parse("2026-09-30"))
        assertEquals(now, vm.uiState.value.form.playedAt)
    }
}
