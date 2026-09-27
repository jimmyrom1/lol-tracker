package dev.jose.loltracker.core.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchValidatorTest {

    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private val valid = MatchDraft(
        championId = "Ahri",
        kills = "7", deaths = "2", assists = "11",
        creepScore = "210", durationMinutes = "31",
        playedAt = now.minusSeconds(3600),
    )

    @Test
    fun `a complete draft is valid`() {
        assertTrue(MatchValidator.validate(valid, now).isEmpty())
        assertEquals(Duration.ofMinutes(31), MatchValidator.durationOf(valid))
    }

    @Test
    fun `champion is required`() {
        assertEquals(
            mapOf(MatchField.CHAMPION to ValidationError.REQUIRED),
            MatchValidator.validate(valid.copy(championId = null), now),
        )
    }

    @Test
    fun `numbers must be present, numeric and in range`() {
        val errors = MatchValidator.validate(
            valid.copy(kills = "", deaths = "dos", assists = "-1", creepScore = "5000", durationMinutes = "1"),
            now,
        )
        assertEquals(ValidationError.REQUIRED, errors[MatchField.KILLS])
        assertEquals(ValidationError.NOT_A_NUMBER, errors[MatchField.DEATHS])
        assertEquals(ValidationError.OUT_OF_RANGE, errors[MatchField.ASSISTS])
        assertEquals(ValidationError.OUT_OF_RANGE, errors[MatchField.CREEP_SCORE])
        assertEquals(ValidationError.OUT_OF_RANGE, errors[MatchField.DURATION])
    }

    @Test
    fun `whitespace around numbers is ignored`() {
        assertTrue(MatchValidator.validate(valid.copy(kills = " 7 "), now).isEmpty())
    }

    @Test
    fun `a match cannot be played in the future`() {
        assertEquals(
            ValidationError.IN_THE_FUTURE,
            MatchValidator.validate(valid.copy(playedAt = now.plusSeconds(60)), now)[MatchField.PLAYED_AT],
        )
    }
}
