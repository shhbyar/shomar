package app.shomar.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Runs the sync in the background, only while the phone has a connection.
 * WorkManager keeps the request across app restarts and retries failures with a growing delay.
 */
class SyncScheduler(context: Context) : SyncTrigger {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** One sync as soon as there is a connection. Asking again replaces a waiting request. */
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(needsNetwork)
            .build()
        workManager.enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** A safety net: sync every hour even if nothing changed locally (pulls other devices' changes). */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val ONE_TIME_NAME = "sync-now"
        const val PERIODIC_NAME = "sync-periodic"
    }
}