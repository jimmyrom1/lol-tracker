package dev.jose.loltracker.core.analytics

import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marcaje de eventos independiente del proveedor. En producción aquí iría una
 * implementación para Piano, Tealium o Firebase; las features no cambiarían.
 */
data class AnalyticsEvent(val name: String, val params: Map<String, String> = emptyMap()) {
    companion object {
        fun screenView(screen: String) = AnalyticsEvent("screen_view", mapOf("screen_name" to screen))
    }
}

interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}

internal class LogcatAnalyticsTracker @Inject constructor() : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) {
        Log.d("Analytics", "${event.name} ${event.params}")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AnalyticsModule {
    @Binds
    @Singleton
    abstract fun tracker(impl: LogcatAnalyticsTracker): AnalyticsTracker
}
