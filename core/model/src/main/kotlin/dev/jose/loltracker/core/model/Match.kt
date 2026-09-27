package dev.jose.loltracker.core.model

import java.time.Duration
import java.time.Instant

enum class Role { TOP, JUNGLE, MID, ADC, SUPPORT }

enum class Queue { RANKED_SOLO, RANKED_FLEX, NORMAL, ARAM, OTHER }

enum class MatchResult { WIN, LOSS }

data class Match(
    val id: Long = 0,
    val championId: String,
    val championName: String,
    val role: Role,
    val queue: Queue,
    val result: MatchResult,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val creepScore: Int,
    val duration: Duration,
    val playedAt: Instant,
    val notes: String = "",
) {
    /** KDA clásico: (K + A) / D. Sin muertes se divide entre 1 ("perfect KDA"). */
    val kda: Double get() = (kills + assists).toDouble() / deaths.coerceAtLeast(1)

    val isPerfectKda: Boolean get() = deaths == 0

    val csPerMinute: Double
        get() = if (duration.isZero) 0.0 else creepScore / (duration.seconds / 60.0)
}

data class Champion(
    val id: String,
    val name: String,
    val title: String,
    val iconUrl: String,
    val tags: List<String>,
)
