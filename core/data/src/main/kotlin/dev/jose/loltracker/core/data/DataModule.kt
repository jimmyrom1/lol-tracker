package dev.jose.loltracker.core.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

    companion object {
        /** El reloj se inyecta para que los ViewModels sean testeables con fechas fijas. */
        @Provides
        fun clock(): Clock = Clock.systemDefaultZone()
    }
}
