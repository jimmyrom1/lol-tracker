package dev.jose.loltracker.core.data.riot

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jose.loltracker.core.data.BuildConfig
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Riot ID, API key y lo que se recuerda entre sincronizaciones. */
interface RiotSettings {
    var riotId: String?
    var userApiKey: String?

    /** PUUID de [riotId]: no cambia nunca, así que se guarda y se ahorra una petición por sincronización. */
    var puuid: String?
    var lastSyncAt: Instant?

    /** La que se escribe en la app tiene prioridad sobre la de local.properties. */
    fun apiKey(): String? = userApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.RIOT_API_KEY.takeIf { it.isNotBlank() }
}

@Singleton
internal class SharedPreferencesRiotSettings @Inject constructor(
    @ApplicationContext context: Context,
) : RiotSettings {
    // Almacenamiento privado de la app. Una app pública necesitaría un backend propio que
    // guarde la key: cualquier cosa que esté en el dispositivo se puede extraer.
    private val prefs: SharedPreferences = context.getSharedPreferences("riot", Context.MODE_PRIVATE)

    override var riotId: String?
        get() = prefs.getString("riot_id", null)
        set(value) = prefs.edit {
            // El PUUID pertenece a la cuenta: si cambia el Riot ID, deja de valer.
            if (value != prefs.getString("riot_id", null)) remove("puuid")
            putString("riot_id", value)
        }

    override var userApiKey: String?
        get() = prefs.getString("api_key", null)
        set(value) = prefs.edit { putString("api_key", value?.trim()) }

    override var puuid: String?
        get() = prefs.getString("puuid", null)
        set(value) = prefs.edit { putString("puuid", value) }

    override var lastSyncAt: Instant?
        get() = prefs.getLong("last_sync", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit { putLong("last_sync", value?.toEpochMilli() ?: 0) }
}
