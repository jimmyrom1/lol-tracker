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
 * API oficial de Riot con enrutado **regional** ("europe" para EUW): cuentas (account-v1) e
 * historial de partidas (match-v5).
 */
interface RiotApi {

    @GET("riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}")
    suspend fun accountByRiotId(@Path("gameName") gameName: String, @Path("tagLine") tagLine: String): AccountDto

    /** Ids de las últimas partidas, de la más reciente a la más antigua (máximo 100 por página). */
    @GET("lol/match/v5/matches/by-puuid/{puuid}/ids")
    suspend fun matchIds(
        @Path("puuid") puuid: String,
        @Query("start") start: Int = 0,
        @Query("count") count: Int = 20,
    ): List<String>

    @GET("lol/match/v5/matches/{matchId}")
    suspend fun match(@Path("matchId") matchId: String): RiotMatchDto

    /** Estado de la partida minuto a minuto. Pesa bastante: solo se pide al abrir el detalle. */
    @GET("lol/match/v5/matches/{matchId}/timeline")
    suspend fun timeline(@Path("matchId") matchId: String): TimelineDto
}

/** API de Riot con enrutado de **plataforma** ("euw1"): rango, maestría, invocador y partida en curso. */
interface RiotPlatformApi {

    @GET("lol/summoner/v4/summoners/by-puuid/{puuid}")
    suspend fun summoner(@Path("puuid") puuid: String): SummonerDto

    @GET("lol/league/v4/entries/by-puuid/{puuid}")
    suspend fun leagueEntries(@Path("puuid") puuid: String): List<LeagueEntryDto>

    @GET("lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}/top")
    suspend fun topMasteries(@Path("puuid") puuid: String, @Query("count") count: Int = 5): List<MasteryDto>

    @GET("lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}/by-champion/{championId}")
    suspend fun mastery(@Path("puuid") puuid: String, @Path("championId") championId: Long): MasteryDto

    /** 404 si el jugador no está en partida. */
    @GET("lol/spectator/v5/active-games/by-summoner/{puuid}")
    suspend fun activeGame(@Path("puuid") puuid: String): ActiveGameDto
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
    val gameVersion: String = "",
    val queueId: Int,
    val participants: List<ParticipantDto>,
    val teams: List<TeamDto> = emptyList(),
)

@Serializable
data class ParticipantDto(
    val puuid: String,
    val participantId: Int = 0,
    val teamId: Int = 0,
    val riotIdGameName: String = "",
    val riotIdTagline: String = "",
    /** Es el id de Data Dragon ("LeeSin", "MonkeyKing"), no el nombre traducido. */
    val championName: String,
    val champLevel: Int = 0,
    val teamPosition: String = "",
    val individualPosition: String = "",
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val totalMinionsKilled: Int,
    val neutralMinionsKilled: Int = 0,
    val goldEarned: Int = 0,
    val totalDamageDealtToChampions: Int = 0,
    val totalDamageTaken: Int = 0,
    val visionScore: Int = 0,
    val wardsPlaced: Int = 0,
    val item0: Int = 0,
    val item1: Int = 0,
    val item2: Int = 0,
    val item3: Int = 0,
    val item4: Int = 0,
    val item5: Int = 0,
    val item6: Int = 0,
    val summoner1Id: Int = 0,
    val summoner2Id: Int = 0,
    val win: Boolean,
    val gameEndedInEarlySurrender: Boolean = false,
    val challenges: ChallengesDto? = null,
) {
    val items: List<Int> get() = listOf(item0, item1, item2, item3, item4, item5, item6)
}

@Serializable
data class ChallengesDto(val killParticipation: Double? = null)

@Serializable
data class TeamDto(val teamId: Int, val win: Boolean, val objectives: ObjectivesDto? = null)

@Serializable
data class ObjectivesDto(
    val baron: ObjectiveDto = ObjectiveDto(),
    val dragon: ObjectiveDto = ObjectiveDto(),
    val tower: ObjectiveDto = ObjectiveDto(),
    val riftHerald: ObjectiveDto = ObjectiveDto(),
)

@Serializable
data class ObjectiveDto(val kills: Int = 0)

@Serializable
data class TimelineDto(val info: TimelineInfoDto)

@Serializable
data class TimelineInfoDto(val frames: List<FrameDto>)

/** Una foto por minuto. Las claves de participantFrames son "1".."10" (participantId). */
@Serializable
data class FrameDto(val timestamp: Long, val participantFrames: Map<String, ParticipantFrameDto> = emptyMap())

@Serializable
data class ParticipantFrameDto(
    val participantId: Int,
    val totalGold: Int = 0,
    val minionsKilled: Int = 0,
    val jungleMinionsKilled: Int = 0,
    val xp: Int = 0,
)

@Serializable
data class SummonerDto(val profileIconId: Int, val summonerLevel: Long)

@Serializable
data class LeagueEntryDto(
    val queueType: String,
    val tier: String,
    val rank: String,
    val leaguePoints: Int,
    val wins: Int,
    val losses: Int,
    val hotStreak: Boolean = false,
)

@Serializable
data class MasteryDto(val championId: Long, val championLevel: Int, val championPoints: Int)

@Serializable
data class ActiveGameDto(
    val gameId: Long,
    val gameQueueConfigId: Int = 0,
    val gameStartTime: Long = 0,
    val gameLength: Long = 0,
    val participants: List<ActiveParticipantDto>,
)

@Serializable
data class ActiveParticipantDto(
    val puuid: String? = null,
    val teamId: Int,
    /** Id numérico (el "key" de Data Dragon), no el nombre. */
    val championId: Long,
    val riotId: String = "",
    val spell1Id: Int = 0,
    val spell2Id: Int = 0,
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
 * Red de seguridad por si aun así llega un 429 (la key compartida con otro programa, por ejemplo):
 * se espera lo que indique Retry-After (hasta [maxWaitSeconds]) y se reintenta una sola vez.
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
const val RIOT_EUW1_BASE_URL = "https://euw1.api.riotgames.com/"

private val riotJson = Json { ignoreUnknownKeys = true }

/**
 * Cliente con la key, el limitador y el reintento. Las dos APIs deben compartir el mismo
 * [limiter] para que las cuentas cuadren aunque las peticiones salgan de sitios distintos.
 */
fun riotClient(
    client: OkHttpClient,
    keys: RiotApiKeyProvider,
    limiter: RiotRateLimiter,
    sleep: (Long) -> Unit = { Thread.sleep(it) },
): OkHttpClient = client.newBuilder()
    .addInterceptor(RiotAuthInterceptor(keys))
    // El limitador va antes del reintento: el reintento tras un 429 también cuenta.
    .addInterceptor(RateLimitInterceptor(sleep = sleep))
    .addNetworkInterceptor(limiter.interceptor())
    .build()

inline fun <reified T> createRiotService(riotClient: OkHttpClient, baseUrl: String): T = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(riotClient)
    .addConverterFactory(riotJsonConverter())
    .build()
    .create(T::class.java)

@PublishedApi
internal fun riotJsonConverter() = riotJson.asConverterFactory("application/json".toMediaType())

/** Atajo usado por los tests y por quien solo necesita la API regional. */
fun createRiotApi(
    client: OkHttpClient,
    baseUrl: String,
    keys: RiotApiKeyProvider,
    sleep: (Long) -> Unit = { Thread.sleep(it) },
    limiter: RiotRateLimiter = RiotRateLimiter(),
): RiotApi = createRiotService(riotClient(client, keys, limiter, sleep), baseUrl)
