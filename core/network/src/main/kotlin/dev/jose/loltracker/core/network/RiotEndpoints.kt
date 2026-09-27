package dev.jose.loltracker.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * A dónde se mandan las peticiones de Riot: directamente a Riot (con la key en el móvil) o a
 * lol-tracker-api, que guarda la key en el servidor y comparte la caché entre usuarios.
 * Las rutas son idénticas en los dos casos; solo cambia la base.
 */
data class RiotEndpoints(
    val regionalBaseUrl: String,
    val platformBaseUrl: String,
    /** true = lol-tracker-api. El móvil no necesita key de Riot. */
    val viaServer: Boolean,
    /** Token de la app para el servidor (cabecera X-App-Token), si el servidor lo pide. */
    val appToken: String? = null,
) {
    companion object {
        val DIRECT = RiotEndpoints(RIOT_EUROPE_BASE_URL, RIOT_EUW1_BASE_URL, viaServer = false)

        /** "http://10.0.2.2:3000" → "http://10.0.2.2:3000/riot/europe/" y ".../riot/euw1/". */
        fun server(url: String, appToken: String? = null): RiotEndpoints {
            val base = url.trimEnd('/')
            return RiotEndpoints("$base/riot/europe/", "$base/riot/euw1/", viaServer = true, appToken = appToken?.takeIf { it.isNotBlank() })
        }

        /** Con el servidor, el propio servidor reparte la cuota de Riot: el móvil solo respeta el límite por cliente (120/min). */
        val SERVER_WINDOWS = listOf(RiotRateLimiter.Window(maxRequests = 15, millis = 1_000), RiotRateLimiter.Window(maxRequests = 110, millis = 60_000))
    }
}

/** Con el servidor: se manda el token de la app, nunca una key de Riot. */
internal class AppTokenInterceptor(private val token: String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().apply { token?.let { header("X-App-Token", it) } }.build()
        return chain.proceed(request)
    }
}
