package dev.jose.loltracker.core.network

import okhttp3.Interceptor

/**
 * Respeta los límites de la key de Riot **antes** de enviar cada petición, en vez de esperar a
 * recibir un 429. Riot los aplica por key y por valor de enrutado (europe, euw1...), así que se
 * lleva una ventana deslizante por host.
 *
 * Los límites reales son 20/s y 100/2 min; se usa un margen por debajo porque la misma key puede
 * estar usándose a la vez desde otro sitio (otro dispositivo, un script) y porque Riot puede
 * suspender las keys que reciben 429 a menudo.
 */
class RiotRateLimiter(
    private val windows: List<Window> = DEFAULT_WINDOWS,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    data class Window(val maxRequests: Int, val millis: Long)

    private val sentByHost = mutableMapOf<String, ArrayDeque<Long>>()

    /** Bloquea el hilo hasta que la petición cabe en todas las ventanas y la registra. */
    fun acquire(host: String) {
        while (true) {
            val wait = synchronized(this) {
                val sent = sentByHost.getOrPut(host) { ArrayDeque() }
                val t = now()
                val longest = windows.maxOf { it.millis }
                while (sent.isNotEmpty() && sent.first() <= t - longest) sent.removeFirst()

                val needed = windows.maxOf { w ->
                    val inWindow = sent.count { it > t - w.millis }
                    if (inWindow < w.maxRequests) {
                        0L
                    } else {
                        // Hay que esperar a que salga de la ventana la más antigua que sobra.
                        val oldestToExpire = sent.filter { it > t - w.millis }[inWindow - w.maxRequests]
                        oldestToExpire + w.millis - t + 1
                    }
                }
                if (needed == 0L) sent.addLast(t)
                needed
            }
            if (wait == 0L) return
            sleep(wait)
        }
    }

    internal fun interceptor() = Interceptor { chain ->
        acquire(chain.request().url.host)
        chain.proceed(chain.request())
    }

    companion object {
        val DEFAULT_WINDOWS = listOf(Window(maxRequests = 15, millis = 1_000), Window(maxRequests = 90, millis = 120_000))
    }
}
