package dev.jose.loltracker.core.network

import dev.jose.loltracker.core.model.Champion
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Data Dragon: los datos estáticos públicos de League of Legends (campeones, iconos...).
 * No necesita API key, a diferencia de la API de partidas de Riot.
 */
interface DataDragonApi {

    /** Versiones de parche, de la más reciente a la más antigua. */
    @GET("api/versions.json")
    suspend fun versions(): List<String>

    @GET("cdn/{version}/data/{locale}/champion.json")
    suspend fun champions(@Path("version") version: String, @Path("locale") locale: String): ChampionsResponse
}

@Serializable
data class ChampionsResponse(val data: Map<String, ChampionDto>)

@Serializable
data class ChampionDto(
    val id: String,
    val name: String,
    val title: String,
    val tags: List<String> = emptyList(),
    val image: ImageDto,
)

@Serializable
data class ImageDto(val full: String)

fun ChampionDto.toModel(baseUrl: String, version: String) = Champion(
    id = id,
    name = name,
    title = title,
    iconUrl = "${baseUrl.trimEnd('/')}/cdn/$version/img/champion/${image.full}",
    tags = tags,
)
