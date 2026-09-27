package dev.jose.loltracker.core.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jose.loltracker.core.data.riot.DefaultRiotImportRepository
import dev.jose.loltracker.core.data.riot.RiotImportRepository
import dev.jose.loltracker.core.data.riot.RiotSettings
import dev.jose.loltracker.core.data.riot.SharedPreferencesRiotSettings
import dev.jose.loltracker.core.network.RiotApiKeyProvider
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun matchRepository(impl: OfflineFirstMatchRepository): MatchRepository

    @Binds
    @Singleton
    abstract fun championRepository(impl: DataDragonChampionRepository): ChampionRepository

    @Binds
    abstract fun riotImportRepository(impl: DefaultRiotImportRepository): RiotImportRepository

    @Binds
    abstract fun riotSettings(impl: SharedPreferencesRiotSettings): RiotSettings


    companion object {
        /** El reloj se inyecta para que los ViewModels sean testeables con fechas fijas. */
        @Provides
        fun clock(): Clock = Clock.systemDefaultZone()

        /** El cliente de red lee la key en cada petición, así que una key nueva vale al momento. */
        @Provides
        fun riotApiKeyProvider(settings: RiotSettings): RiotApiKeyProvider = RiotApiKeyProvider(settings::apiKey)
    }
}
