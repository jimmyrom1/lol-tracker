package dev.jose.loltracker.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiotRateLimiterTest {

    /** Reloj falso: "dormir" solo adelanta el tiempo, así el test tarda milisegundos. */
    private var now = 0L
    private val sleeps = mutableListOf<Long>()
    private fun limiter(windows: List<RiotRateLimiter.Window> = RiotRateLimiter.DEFAULT_WINDOWS) =
        RiotRateLimiter(windows, now = { now }, sleep = { sleeps += it; now += it })

    @Test
    fun defaultsStayBelowRiotLimits() {
        val windows = RiotRateLimiter.DEFAULT_WINDOWS
        assertTrue(windows.single { it.millis == 1_000L }.maxRequests < 20)
        assertTrue(windows.single { it.millis == 120_000L }.maxRequests < 100)
    }

    @Test
    fun burstsAreSpreadOverTheSecondWindow() {
        val limiter = limiter()
        repeat(15) { limiter.acquire("europe") }
        assertEquals(emptyList<Long>(), sleeps)

        limiter.acquire("europe") // la 16ª en el mismo milisegundo tiene que esperar
        assertEquals(listOf(1_001L), sleeps)
    }

    @Test
    fun theTwoMinuteWindowIsNeverExceeded() {
        val limiter = limiter()
        val sent = mutableListOf<Long>()
        repeat(200) {
            limiter.acquire("europe")
            sent += now
        }
        // En cualquier ventana de 2 minutos hay como mucho 90 peticiones, y en cualquier segundo 15.
        sent.forEach { start ->
            assertTrue(sent.count { it in start until start + 120_000 } <= 90)
            assertTrue(sent.count { it in start until start + 1_000 } <= 15)
        }
    }

    @Test
    fun eachRoutingValueHasItsOwnBudget() {
        val limiter = limiter(listOf(RiotRateLimiter.Window(2, 1_000)))
        limiter.acquire("europe")
        limiter.acquire("europe")
        limiter.acquire("euw1")
        limiter.acquire("euw1")
        assertEquals("europe y euw1 cuentan por separado, como en Riot", emptyList<Long>(), sleeps)
    }
}
