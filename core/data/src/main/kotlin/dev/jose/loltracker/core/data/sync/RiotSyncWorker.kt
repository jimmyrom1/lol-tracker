package dev.jose.loltracker.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.RiotError
import dev.jose.loltracker.core.data.riot.RiotImportRepository
import java.util.concurrent.TimeUnit

/**
 * Descarga las partidas nuevas en segundo plano. Cuesta 1 petición (la lista de ids) más una por
 * partida nueva: unas pocas cada 6 horas, lejísimos del límite de la key.
 */
@HiltWorker
class RiotSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importer: RiotImportRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = importer.syncSavedIfStale()) {
        null, is ImportResult.Success -> Result.success()
        is ImportResult.Failure -> when (result.error) {
            // Reintentar no arregla una key inválida ni una cuenta que no existe: insistir solo
            // gastaría peticiones rechazadas, que es justo lo que Riot vigila.
            RiotError.INVALID_API_KEY, RiotError.MISSING_API_KEY,
            RiotError.ACCOUNT_NOT_FOUND, RiotError.NOT_CONFIGURED -> Result.failure()
            RiotError.RATE_LIMITED, RiotError.NETWORK -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "riot-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RiotSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            // KEEP: si ya está programada no se reinicia el contador cada vez que se abre la app.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
