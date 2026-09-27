package dev.jose.loltracker.feature.matches

import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.RiotId
import dev.jose.loltracker.core.testing.FakeRiotImportRepository
import dev.jose.loltracker.core.testing.FakeRiotSettings
import dev.jose.loltracker.core.testing.MainDispatcherRule
import dev.jose.loltracker.core.testing.TestAnalyticsTracker
import dev.jose.loltracker.feature.matches.riot.RiotImportViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RiotImportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRiotImportRepository()
    private val settings = FakeRiotSettings()
    private val analytics = TestAnalyticsTracker()

    private fun viewModel() = RiotImportViewModel(repository, settings, analytics)

    @Test
    fun remembersTheLastRiotIdAndDetectsABuiltInKey() {
        settings.riotId = "jimmyrom#uarra"
        settings.builtInKey = "RGAPI-local"

        val state = viewModel().uiState.value

        assertEquals("jimmyrom#uarra", state.riotId)
        assertEquals("", state.apiKey)
        assertTrue(state.hasBuiltInKey)
    }

    @Test
    fun invalidRiotIdIsRejectedWithoutCallingRiot() {
        val vm = viewModel()
        vm.onRiotIdChange("jimmyrom")
        vm.import()

        assertTrue(vm.uiState.value.riotIdError)
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun importSavesTheTypedKeyAndShowsTheResult() {
        repository.result = ImportResult.Success(imported = 18, alreadyImported = 0, skipped = 2)
        val vm = viewModel()
        vm.onRiotIdChange("jimmyrom#uarra")
        vm.onApiKeyChange("  RGAPI-nueva  ")
        vm.import()

        assertEquals(listOf(RiotId("jimmyrom", "uarra")), repository.requests)
        assertEquals("RGAPI-nueva", settings.userApiKey)
        assertFalse(vm.uiState.value.isImporting)
        assertEquals(repository.result, vm.uiState.value.result)
        assertEquals("riot_import", analytics.events.single().name)
    }

    @Test
    fun anEmptyKeyFieldDoesNotEraseTheSavedOne() {
        settings.userApiKey = "RGAPI-guardada"
        val vm = viewModel()
        vm.onApiKeyChange("")
        vm.onRiotIdChange("jimmyrom#uarra")
        vm.import()

        assertEquals("RGAPI-guardada", settings.userApiKey)
    }

    @Test
    fun failuresAreExposedAndTracked() {
        repository.result = ImportResult.Failure(RiotError.INVALID_API_KEY)
        val vm = viewModel()
        vm.onRiotIdChange("jimmyrom#uarra")
        vm.import()

        assertEquals(ImportResult.Failure(RiotError.INVALID_API_KEY), vm.uiState.value.result)
        assertEquals(mapOf("error" to "INVALID_API_KEY"), analytics.events.single().params)
    }
}
