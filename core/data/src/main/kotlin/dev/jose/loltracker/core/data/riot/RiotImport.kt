package dev.jose.loltracker.core.data.riot

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jose.loltracker.core.data.BuildConfig
import dev.jose.loltracker.core.data.ChampionRepository
import dev.jose.loltracker.core.database.MatchDao
import dev.jose.loltracker.core.database.toEntity
import dev.jose.loltracker.core.network.MissingRiotApiKeyException
import dev.jose.loltracker.core.network.RiotApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/** "jimmyrom#uarra" → gameName "jimmyrom", tagLine "uarra". */
data class RiotId(val gameName: String, val tagLine: String) {
    override fun toString() = "$gameName#$tagLine"

    companion object {
        fun parse(text: String): RiotId? {
            val parts = text.trim().split('#')
            if (parts.size != 2) return null
            val (name, tag) = parts.map { it.trim() }
            // Límites de Riot: nombre de 3 a 16 caracteres y etiqueta de 3 a 5.
            if (name.length !in 3..16 || tag.length !in 3..5) return null
            return RiotId(name, tag)
        }
    }
}

sealed interface ImportResult {
    data class Success(val imported: Int, val alreadyImported: Int, val skipped: Int) : ImportResult
    data class Failure(val error: ImportError) : ImportResult
}

enum class ImportError { MISSING_API_KEY, INVALID_API_KEY, ACCOUNT_NOT_FOUND, RATE_LIMITED, NETWORK }

/** Riot ID y API key que el usuario escribe en la app. */
interface RiotSettings {
    var riotId: String?
    var userApiKey: String?

    /** La que se escribe en la app tiene prioridad sobre la de local.properties. */
    fun apiKey(): String? = userApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.RIOT_API_KEY.takeIf { it.isNotBlank() }
}

@Singleton
internal class SharedPreferencesRiotSettings @Inject constructor(
    @ApplicationContext context: Context,
) : RiotSettings {
    // Almacenamiento privado de la app. Las keys de desarrollo caducan a las 24 h, así que no
    // compensa cifrarlas; una app pública necesitaría un backend propio que guarde la key.
    private val prefs: SharedPreferences = context.getSharedPreferences("riot", Context.MODE_PRIVATE)

    override var riotId: String?
        get() = prefs.getString("riot_id", null)
        set(value) = prefs.edit { putString("riot_id", value) }

    override var userApiKey: String?
        get() = prefs.getString("api_key", null)
        set(value) = prefs.edit { putString("api_key", value?.trim()) }
}

interface RiotImportRepository {
    suspend fun import(riotId: RiotId, count: Int = DEFAULT_COUNT): ImportResult

    companion object {
        const val DEFAULT_COUNT = 20
    }
}

internal class DefaultRiotImportRepository @Inject constructor(
    private val api: RiotApi,
    private val dao: MatchDao,
    private val champions: ChampionRepository,
    private val settings: RiotSettings,
) : RiotImportRepository {

    override suspend fun import(riotId: RiotId, count: Int): ImportResult {
        if (settings.apiKey() == null) return ImportResult.Failure(ImportError.MISSING_API_KEY)
        return try {
            val puuid = api.accountByRiotId(riotId.gameName, riotId.tagLine).puuid
            val ids = api.matchIds(puuid, count = count)
            // Solo se descargan las partidas nuevas: cada una es una petición contra el límite de la key.
            val known = dao.existingRiotMatchIds(ids).toSet()
            val catalog = champions.observeChampions().first().associateBy { it.id.lowercase() }

            val newIds = ids.filterNot { it in known }
            val matches = newIds.mapNotNull { id -> RiotMatchMapper.toMatch(api.match(id), puuid, catalog) }
            val inserted = dao.insertAll(matches.map { it.toEntity() }).count { it != -1L }

            settings.riotId = riotId.toString()
            ImportResult.Success(imported = inserted, alreadyImported = known.size, skipped = newIds.size - matches.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            ImportResult.Failure(
                when (e.code()) {
                    401, 403 -> ImportError.INVALID_API_KEY
                    404 -> ImportError.ACCOUNT_NOT_FOUND
                    429 -> ImportError.RATE_LIMITED
                    else -> ImportError.NETWORK
                },
            )
        } catch (e: MissingRiotApiKeyException) {
            ImportResult.Failure(ImportError.MISSING_API_KEY)
        } catch (e: IOException) {
            ImportResult.Failure(ImportError.NETWORK)
        }
    }
}
