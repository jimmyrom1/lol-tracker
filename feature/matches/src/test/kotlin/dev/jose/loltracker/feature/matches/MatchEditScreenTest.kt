package dev.jose.loltracker.feature.matches

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.jose.loltracker.core.designsystem.theme.LolTheme
import dev.jose.loltracker.core.domain.MatchField
import dev.jose.loltracker.core.domain.ValidationError
import dev.jose.loltracker.core.testing.TestData
import dev.jose.loltracker.feature.matches.edit.MatchEditActions
import dev.jose.loltracker.feature.matches.edit.MatchEditScreen
import dev.jose.loltracker.feature.matches.edit.MatchEditUiState
import dev.jose.loltracker.feature.matches.edit.MatchForm
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Test de UI en la JVM con Robolectric: sin emulador, corre en la CI junto al resto. */
@RunWith(RobolectricTestRunner::class)
class MatchEditScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val form = MatchForm(playedAt = Instant.parse("2026-09-27T15:00:00Z"))

    @Test
    fun showsValidationErrorsWithTheAllowedRange() {
        compose.setContent {
            LolTheme {
                MatchEditScreen(
                    state = MatchEditUiState(
                        isNew = true,
                        isLoading = false,
                        form = form.copy(kills = "150"),
                        errors = mapOf(
                            MatchField.CHAMPION to ValidationError.REQUIRED,
                            MatchField.KILLS to ValidationError.OUT_OF_RANGE,
                        ),
                    ),
                    champions = TestData.champions,
                    actions = MatchEditActions(),
                    zone = ZoneOffset.UTC,
                )
            }
        }

        compose.onNodeWithText("Nueva partida").assertIsDisplayed()
        compose.onNodeWithText("Elige un campeón").assertIsDisplayed()
        compose.onNodeWithText("Entre 0 y 99").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saveButtonCallsTheAction() {
        var saves = 0
        compose.setContent {
            LolTheme {
                MatchEditScreen(
                    state = MatchEditUiState(isNew = false, isLoading = false, form = form.copy(champion = TestData.ahri)),
                    champions = TestData.champions,
                    actions = MatchEditActions(onSave = { saves++ }),
                    zone = ZoneOffset.UTC,
                )
            }
        }

        compose.onNodeWithText("Editar partida").assertIsDisplayed()
        compose.onNodeWithTag("save_button").performScrollTo().performClick()
        assertEquals(1, saves)
    }
}
