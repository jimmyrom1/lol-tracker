package dev.jose.loltracker

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import dev.jose.loltracker.core.data.MatchRepository
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.MatchResult.LOSS
import dev.jose.loltracker.core.model.MatchResult.WIN
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * `adb shell am start -n dev.jose.loltracker/.DemoDataActivity`
 *
 * Solo existe en el build de debug, así que nunca llega a la versión publicada.
 */
@AndroidEntryPoint
class DemoDataActivity : ComponentActivity() {

    @Inject lateinit var matches: MatchRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
            demoMatches(now).forEach { matches.saveMatch(it) }
            Toast.makeText(this@DemoDataActivity, "Partidas de ejemplo cargadas", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun demoMatches(now: Instant): List<Match> {
        var hoursAgo = 1L
        fun m(id: String, name: String, role: Role, result: MatchResult, k: Int, d: Int, a: Int, cs: Int, min: Long, queue: Queue = Queue.RANKED_SOLO, notes: String = "") =
            Match(
                championId = id, championName = name, role = role, queue = queue, result = result,
                kills = k, deaths = d, assists = a, creepScore = cs, duration = Duration.ofMinutes(min),
                playedAt = now.minus(Duration.ofHours(hoursAgo)).also { hoursAgo += 7 }, notes = notes,
            )
        return listOf(
            m("Ahri", "Ahri", Role.MID, WIN, 9, 2, 11, 231, 31, notes = "Roams a bot tras el nivel 6"),
            m("Jinx", "Jinx", Role.ADC, WIN, 12, 3, 7, 268, 33),
            m("Ahri", "Ahri", Role.MID, WIN, 6, 4, 8, 204, 28),
            m("LeeSin", "Lee Sin", Role.JUNGLE, LOSS, 4, 7, 6, 142, 35, notes = "Invadí sin visión"),
            m("Thresh", "Thresh", Role.SUPPORT, WIN, 1, 3, 19, 38, 30, Queue.RANKED_FLEX),
            m("Jinx", "Jinx", Role.ADC, LOSS, 5, 6, 4, 221, 29),
            m("Ahri", "Ahri", Role.MID, WIN, 11, 1, 6, 247, 30),
            m("Garen", "Garen", Role.TOP, LOSS, 3, 5, 2, 176, 26),
            m("Lux", "Lux", Role.MID, WIN, 7, 5, 14, 60, 21, Queue.ARAM),
            m("Jinx", "Jinx", Role.ADC, WIN, 8, 2, 9, 255, 32),
            m("LeeSin", "Lee Sin", Role.JUNGLE, WIN, 7, 3, 10, 168, 27),
            m("Ahri", "Ahri", Role.MID, LOSS, 4, 6, 5, 198, 34),
        )
    }
}
