package dev.jose.loltracker.core.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API oficial de Riot: cuentas (account-v1) e historial de partidas (match-v5).
 * Ambas usan el enrutado regional; las cuentas de EUW están en "europe".
 */
interface RiotApi {

    @GET("riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}")
    suspend fun accountByRiotId(@Path("gameName") gameName: String, @Path("tagLine") tagLine: String): AccountDto

    /** Ids de las últimas partidas, de la más reciente a la más antigua. */
    @GET("lol/match/v5/matches/by-puuid/{puuid}/ids")
    suspend fun matchIds(
        @Path("puuid") puuid: String,
        @Query("start") start: Int = 0,
        @Query("count") count: Int = 20,
    ): List<String>

    @GET("lol/match/v5/matches/{matchId}")
    suspend fun match(@Path("matchId") matchId: String): RiotMatchDto
}

@Serializable
data class AccountDto(val puuid: String, val gameName: String? = null, val tagLine: String? = null)

@Serializable
data class RiotMatchDto(val metadata: MetadataDto, val info: InfoDto)

@Serializable
data class MetadataDto(val matchId: String)

@Serializable
data class InfoDto(
    val gameCreation: Long,
    /** En segundos (desde el parche 11.20). */
    val gameDuration: Long,
    val gameEndTimestamp: Long? = null,
    val queueId: Int,
    val participants: List<ParticipantDto>,
)

@Serializable
data class ParticipantDto(
    val puuid: String,
    /** Es el id de Data Dragon ("LeeSin", "MonkeyKing"), no el nombre traducido. */
    val championName: String,
    val teamPosition: String = "",
    val individualPosition: String = "",
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val totalMinionsKilled: Int,
    val neutralMinionsKilled: Int = 0,
    val win: Boolean,
    val gameEndedInEarlySurrender: Boolean = false,
)

/** De dónde sale la API key en cada petición (se puede cambiar desde la app sin reiniciarla). */
fun interface RiotApiKeyProvider {
    fun apiKey(): String?
}

/** Es IOException a propósito: OkHttp solo entrega esas al llamante; otras harían caer la app. */
class MissingRiotApiKeyException : IOException("No hay API key de Riot configurada")

/** Añade la cabecera X-Riot-Token; sin key, falla antes de gastar una petición. */
internal class RiotAuthInterceptor(private val keys: RiotApiKeyProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val key = keys.apiKey()?.trim().orEmpty()
        if (key.isEmpty()) throw MissingRiotApiKeyException()
        return chain.proceed(chain.request().newBuilder().header("X-Riot-Token", key).build())
    }
}

/**
 * Las keys de desarrollo admiten 20 peticiones por segundo y 100 cada 2 minutos. Si Riot responde
 * 429, se espera lo que indique Retry-After (hasta [maxWaitSeconds]) y se reintenta una vez.
 */
internal class RateLimitInterceptor(
    private val maxWaitSeconds: Long = 10,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 429) return response
        val wait = response.header("Retry-After")?.toLongOrNull() ?: 1
        if (wait > maxWaitSeconds) return response
        response.close()
        sleep(TimeUnit.SECONDS.toMillis(wait))
        return chain.proceed(chain.request())
    }
}

const val RIOT_EUROPE_BASE_URL = "https://europe.api.riotgames.com/"

fun createRiotApi(
    client: OkHttpClient,
    baseUrl: String,
    keys: RiotApiKeyProvider,
    sleep: (Long) -> Unit = { Thread.sleep(it) },
): RiotApi {
    val json = Json { ignoreUnknownKeys = true }
    val riotClient = client.newBuilder()
        .addInterceptor(RiotAuthInterceptor(keys))
        .addInterceptor(RateLimitInterceptor(sleep = sleep))
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(riotClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RiotApi::class.java)
}
