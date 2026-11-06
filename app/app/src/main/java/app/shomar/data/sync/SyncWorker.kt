package app.shomar.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.shomar.ShomarApp
import app.shomar.data.remote.ApiException
import java.io.IOException

/** Runs one sync in the background. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ShomarApp).container
        if (container.tokens.token == null) return Result.success() // not logged in: nothing to sync

        return try {
            container.sync.sync()
            Result.success()
        } catch (e: IOException) {
            Result.retry() // no connection or the server is down: try again later
        } catch (e: ApiException) {
            // 401: the login is no longer valid, so retrying would not help until the user logs in again.
            if (e.code == 401) Result.failure() else Result.retry()
        }
    }
}