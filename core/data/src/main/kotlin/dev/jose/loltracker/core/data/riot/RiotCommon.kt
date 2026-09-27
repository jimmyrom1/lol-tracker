package dev.jose.loltracker.core.data.riot

import dev.jose.loltracker.core.network.MissingRiotApiKeyException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
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

/** Vive en core:model para que el design system pueda traducir los mensajes sin depender de la capa de datos. */
typealias RiotError = dev.jose.loltracker.core.model.RiotError

sealed interface RiotResult<out T> {
    data class Success<T>(val value: T) : RiotResult<T>
    data class Failure(val error: RiotError) : RiotResult<Nothing>
}

/** Traduce los fallos de red y los códigos HTTP de Riot a un [RiotError]. */
internal suspend fun <T> riotCall(block: suspend () -> T): RiotResult<T> = try {
    RiotResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    RiotResult.Failure(e.toRiotError())
} catch (e: MissingRiotApiKeyException) {
    RiotResult.Failure(RiotError.MISSING_API_KEY)
} catch (e: IOException) {
    RiotResult.Failure(RiotError.NETWORK)
}

internal fun HttpException.toRiotError() = when (code()) {
    401, 403 -> RiotError.INVALID_API_KEY
    404 -> RiotError.ACCOUNT_NOT_FOUND
    // 429: límite de Riot (directo) o del servidor por cliente; 503: el servidor se ha quedado sin cuota de Riot.
    429, 503 -> RiotError.RATE_LIMITED
    else -> RiotError.NETWORK
}

internal fun HttpException.isNotFound() = code() == 404
