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
    fun riotApi(client: OkHttpClient, keys: RiotApiKeyProvider): RiotApi =
        createRiotApi(client, RIOT_EUROPE_BASE_URL, keys)
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
