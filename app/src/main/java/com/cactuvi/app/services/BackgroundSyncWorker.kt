package com.cactuvi.app.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cactuvi.app.data.sync.SyncCoordinator
import com.cactuvi.app.utils.PreferencesManager
import com.cactuvi.app.utils.SyncPreferencesManager
import com.cactuvi.app.utils.VPNDetector
import java.util.concurrent.TimeUnit

/**
 * Background worker for periodic content synchronization. Runs every N hours (configurable),
 * respects network constraints (WiFi-only by default). Delegates actual sync logic to
 * SyncCoordinator.
 */
class BackgroundSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "background_sync_periodic"

        /**
         * Schedule periodic background sync based on user preferences. Call this on app startup and
         * when sync settings change.
         */
        fun schedule(context: Context) {
            val syncPrefs = SyncPreferencesManager.getInstance(context)

            // Don't schedule if sync is disabled
            if (!syncPrefs.isSyncEnabled) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }

            // Build constraints
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (syncPrefs.isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    )
                    .setRequiresBatteryNotLow(true)
                    .build()

            // Build periodic work request
            val syncRequest =
                PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                        syncPrefs.syncIntervalHours,
                        TimeUnit.HOURS,
                    )
                    .setConstraints(constraints)
                    .build()

            // Schedule with REPLACE policy (updates interval if changed)
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    syncRequest,
                )
        }

        /** Trigger immediate one-time sync (e.g., when user taps "Sync Now" button) */
        fun syncNow(context: Context) {
            val syncPrefs = SyncPreferencesManager.getInstance(context)
            if (!syncPrefs.isSyncEnabled) return

            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val oneTimeSync =
                androidx.work
                    .OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueue(oneTimeSync)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // Check VPN requirement - silently skip if VPN required but not active
            // This is background work so we don't warn the user
            val prefsManager = PreferencesManager.getInstance(applicationContext)
            if (
                prefsManager.isVpnWarningEnabled() && !VPNDetector.isVpnActive(applicationContext)
            ) {
                // Skip sync silently - will retry on next schedule when VPN may be active
                return Result.success()
            }

            // Delegate to SyncCoordinator
            val syncCoordinator = SyncCoordinator(applicationContext)
            val syncResult = syncCoordinator.syncAll()

            // SyncCoordinator returns SyncResult which includes success/failure status
            when (syncResult) {
                is com.cactuvi.app.data.sync.SyncResult.Success -> {
                    Result.success()
                }
                is com.cactuvi.app.data.sync.SyncResult.Failure -> {
                    // Retry on failure
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            // Unexpected error - retry
            Result.retry()
        }
    }
}
