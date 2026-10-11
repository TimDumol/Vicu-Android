package com.rendyhd.vicu.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendyhd.vicu.auth.TokenRefreshRun

/**
 * Periodic WorkManager job that refreshes the JWT token.
 * Keeps the refresh token alive even when the app process is dead,
 * preventing "refresh token expired between app opens" logouts.
 * The guards and the refresh itself are [TokenRefreshRun], shared with iOS.
 */
class TokenRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val tokenRefreshRun: TokenRefreshRun,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TokenRefreshWorker"
    }

    override suspend fun doWork(): Result {
        val outcome = tokenRefreshRun.run("WorkManager periodic (attempt=$runAttemptCount)")
        Log.d(TAG, "Periodic token refresh: $outcome")
        return when (outcome) {
            // Terminal failures do not churn WorkManager retries.
            TokenRefreshRun.Outcome.SKIPPED,
            TokenRefreshRun.Outcome.REFRESHED,
            TokenRefreshRun.Outcome.FAILED_FINAL -> Result.success()
            TokenRefreshRun.Outcome.FAILED_RETRY -> Result.retry()
        }
    }
}
