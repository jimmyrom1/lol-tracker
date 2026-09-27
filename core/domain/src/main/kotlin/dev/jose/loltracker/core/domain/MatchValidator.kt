package dev.jose.loltracker.core.domain

import java.time.Duration
import java.time.Instant

enum class MatchField { CHAMPION, KILLS, DEATHS, ASSISTS, CREEP_SCORE, DURATION, PLAYED_AT }

enum class ValidationError { REQUIRED, NOT_A_NUMBER, OUT_OF_RANGE, IN_THE_FUTURE }

/** Datos tal cual los escribe el usuario en el formulario (texto), antes de convertirlos. */
data class MatchDraft(
    val championId: String?,
    val kills: String,
    val deaths: String,
    val assists: String,
    val creepScore: String,
    val durationMinutes: String,
    val playedAt: Instant,
)

/**
 * Reglas del formulario. Los límites son generosos pero evitan erratas evidentes
 * (una partida de 300 minutos o 5000 de farmeo casi seguro es un error al teclear).
 */
object MatchValidator {

    val STAT_RANGE = 0..99
    val CREEP_SCORE_RANGE = 0..1500
    val DURATION_MINUTES_RANGE = 3..120

    fun validate(draft: MatchDraft, now: Instant): Map<MatchField, ValidationError> = buildMap {
        if (draft.championId.isNullOrBlank()) put(MatchField.CHAMPION, ValidationError.REQUIRED)
        checkNumber(MatchField.KILLS, draft.kills, STAT_RANGE)
        checkNumber(MatchField.DEATHS, draft.deaths, STAT_RANGE)
        checkNumber(MatchField.ASSISTS, draft.assists, STAT_RANGE)
        checkNumber(MatchField.CREEP_SCORE, draft.creepScore, CREEP_SCORE_RANGE)
        checkNumber(MatchField.DURATION, draft.durationMinutes, DURATION_MINUTES_RANGE)
        if (draft.playedAt.isAfter(now)) put(MatchField.PLAYED_AT, ValidationError.IN_THE_FUTURE)
    }

    fun durationOf(draft: MatchDraft): Duration = Duration.ofMinutes(draft.durationMinutes.trim().toLong())

    private fun MutableMap<MatchField, ValidationError>.checkNumber(field: MatchField, raw: String, range: IntRange) {
        val text = raw.trim()
        when {
            text.isEmpty() -> put(field, ValidationError.REQUIRED)
            text.toIntOrNull() == null -> put(field, ValidationError.NOT_A_NUMBER)
            text.toInt() !in range -> put(field, ValidationError.OUT_OF_RANGE)
        }
    }
}
