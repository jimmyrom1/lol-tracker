package dev.jose.loltracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.jose.loltracker.core.data.sync.RiotSyncWorker
import javax.inject.Inject

@HiltAndroidApp
class LolTrackerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // WorkManager se inicializa aquí (y no con el proveedor por defecto) para que los workers
    // reciban sus dependencias de Hilt.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        RiotSyncWorker.schedule(this)
    }
}
