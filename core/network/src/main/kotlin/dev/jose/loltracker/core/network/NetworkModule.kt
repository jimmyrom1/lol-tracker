package dev.jose.loltracker.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

const val DATA_DRAGON_BASE_URL = "https://ddragon.leagueoflegends.com/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Named("dataDragonBaseUrl")
    fun dataDragonBaseUrl(): String = DATA_DRAGON_BASE_URL

    @Provides
    @Singleton
    fun dataDragonApi(client: OkHttpClient, @Named("dataDragonBaseUrl") baseUrl: String): DataDragonApi =
        createDataDragonApi(client, baseUrl)

    @Provides
    @Singleton
    fun riotEndpoints(): RiotEndpoints =
        BuildConfig.LOL_API_URL.takeIf { it.isNotBlank() }?.let { RiotEndpoints.server(it, BuildConfig.LOL_API_TOKEN) }
            ?: RiotEndpoints.DIRECT

    /** Uno solo para toda la app: los límites de Riot son por key, no por pantalla. */
    @Provides
    @Singleton
    fun riotRateLimiter(endpoints: RiotEndpoints): RiotRateLimiter =
        if (endpoints.viaServer) RiotRateLimiter(RiotEndpoints.SERVER_WINDOWS) else RiotRateLimiter()

    @Provides
    @Singleton
    @Named("riot")
    fun riotOkHttp(client: OkHttpClient, keys: RiotApiKeyProvider, limiter: RiotRateLimiter, endpoints: RiotEndpoints): OkHttpClient =
        riotClient(client, keys, limiter, endpoints = endpoints)

    @Provides
    @Singleton
    fun riotApi(@Named("riot") client: OkHttpClient, endpoints: RiotEndpoints): RiotApi =
        createRiotService(client, endpoints.regionalBaseUrl)

    @Provides
    @Singleton
    fun riotPlatformApi(@Named("riot") client: OkHttpClient, endpoints: RiotEndpoints): RiotPlatformApi =
        createRiotService(client, endpoints.platformBaseUrl)
}

/** Fuera del módulo de Hilt para poder usarlo en los tests contra un MockWebServer. */
fun createDataDragonApi(client: OkHttpClient, baseUrl: String): DataDragonApi {
    // Data Dragon devuelve muchos campos que no usamos: se ignoran en vez de fallar.
    val json = Json { ignoreUnknownKeys = true }
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(DataDragonApi::class.java)
}
