package dev.jose.loltracker.core.data

import dev.jose.loltracker.core.database.ChampionDao
import dev.jose.loltracker.core.database.ChampionEntity
import dev.jose.loltracker.core.database.toModel
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.network.DataDragonApi
import dev.jose.loltracker.core.network.toModel
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DataDragonChampionRepository @Inject constructor(
    private val api: DataDragonApi,
    private val dao: ChampionDao,
    @Named("dataDragonBaseUrl") private val baseUrl: String,
) : ChampionRepository {

    override fun observeChampions(): Flow<List<Champion>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun refresh(): Result<Unit> = try {
        val latest = api.versions().first()
        // Solo se descarga el catálogo (~170 campeones) cuando sale un parche nuevo.
        if (latest != dao.cachedPatchVersion()) {
            val champions = api.champions(latest, LOCALE).data.values.map { dto ->
                val model = dto.toModel(baseUrl, latest)
                ChampionEntity(model.id, model.name, model.title, model.iconUrl, model.tags.joinToString(","), latest)
            }
            dao.replaceAll(champions, latest)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e // nunca tragarse la cancelación de una corrutina
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val LOCALE = "es_ES"
    }
}
