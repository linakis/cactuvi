package com.cactuvi.app

import android.app.Application
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.services.BackgroundSyncWorker
import com.cactuvi.app.utils.PreferencesManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.VPNDetector
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class Cactuvi : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /**
         * Flag indicating whether VPN warning needs to be shown. Activities can check this flag and
         * show VPNWarningDialog. Activities should reset this to false after showing the dialog.
         */
        @Volatile var needsVpnWarning: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize mock components if in mock flavor
        if (BuildConfig.FLAVOR == "mock") {
            initializeMockMode()
        }

        // Check VPN status on app start
        checkVpnStatus()

        // Schedule background sync (periodic work)
        BackgroundSyncWorker.schedule(this)

        // Trigger immediate sync if cache exists (stale-while-revalidate pattern)
        applicationScope.launch { triggerInitialSync() }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Cleanup mock components if in mock flavor
        if (BuildConfig.FLAVOR == "mock") {
            shutdownMockMode()
        }
    }

    /**
     * Initialize mock server and notification (mock flavor only). Uses reflection to avoid
     * compile-time dependency on mock classes.
     */
    private fun initializeMockMode() {
        try {
            // Start MockWebServer
            val mockServerClass = Class.forName("com.cactuvi.app.mock.MockServerManager")
            val getInstance = mockServerClass.getMethod("getInstance")
            val mockServerInstance = getInstance.invoke(null)
            val startMethod =
                mockServerClass.getMethod("start", android.content.Context::class.java)
            startMethod.invoke(mockServerInstance, this)
            android.util.Log.i("Cactuvi", "MockWebServer started (MOCK FLAVOR)")

            // Initialize notification
            val notificationClass =
                Class.forName("com.cactuvi.app.mock.MockModeNotificationManager")
            val initMethod =
                notificationClass.getMethod("initialize", android.content.Context::class.java)
            initMethod.invoke(null, this)
            android.util.Log.i("Cactuvi", "Mock mode notification initialized")
        } catch (e: Exception) {
            android.util.Log.e("Cactuvi", "Failed to initialize mock mode", e)
        }
    }

    /** Shutdown mock server and notification (mock flavor only). */
    private fun shutdownMockMode() {
        try {
            // Shutdown notification
            val notificationClass =
                Class.forName("com.cactuvi.app.mock.MockModeNotificationManager")
            val shutdownNotifMethod = notificationClass.getMethod("shutdown")
            shutdownNotifMethod.invoke(null)

            // Shutdown MockWebServer
            val mockServerClass = Class.forName("com.cactuvi.app.mock.MockServerManager")
            val getInstance = mockServerClass.getMethod("getInstance")
            val mockServerInstance = getInstance.invoke(null)
            val shutdownMethod = mockServerClass.getMethod("shutdown")
            shutdownMethod.invoke(mockServerInstance)
        } catch (e: Exception) {
            android.util.Log.e("Cactuvi", "Failed to shutdown mock mode", e)
        }
    }

    /**
     * Check VPN status and set flag for activities to show warning. Does not show dialog directly -
     * lets first visible activity handle it.
     */
    private fun checkVpnStatus() {
        val prefsManager = PreferencesManager.getInstance(this)

        if (prefsManager.isVpnWarningEnabled() && !VPNDetector.isVpnActive(this)) {
            needsVpnWarning = true
        }
    }

    /**
     * Trigger immediate background sync if cache exists. For first launch (no cache),
     * LoadingActivity handles initial data fetch.
     */
    private suspend fun triggerInitialSync() {
        try {
            val database = AppDatabase.getInstance(this)

            // Check if any cache exists
            val hasMovies = (database.cacheMetadataDao().get("movies")?.itemCount ?: 0) > 0
            val hasSeries = (database.cacheMetadataDao().get("series")?.itemCount ?: 0) > 0
            val hasLive = (database.cacheMetadataDao().get("live")?.itemCount ?: 0) > 0

            // If cache exists, trigger immediate one-time sync (background refresh)
            if (hasMovies || hasSeries || hasLive) {
                BackgroundSyncWorker.syncNow(this)
            }
            // If no cache exists, trigger background pre-fetch (Phase 3 optimization)
            else {
                triggerBackgroundPrefetch()
            }
        } catch (e: Exception) {
            // Silently fail - periodic sync will retry
        }
    }

    /**
     * Phase 3 optimization: Background pre-fetch of all content on app launch.
     *
     * This provides perceived instant load times when user navigates to content screens. Runs
     * parallel loading (Movies + Series + Live) in background after brief delay.
     *
     * Prerequisites:
     * - Source must be configured
     * - No existing cache (cold start scenario)
     * - VPN requirement respected (if enabled)
     *
     * Expected result:
     * - Content available in 75-90s (parallel load)
     * - User navigation shows instant cached results
     */
    private suspend fun triggerBackgroundPrefetch() {
        try {
            // Brief delay to let app initialization complete
            kotlinx.coroutines.delay(1000)

            val sourceManager = SourceManager.getInstance(this)
            val activeSource = sourceManager.getActiveSource()

            // Only pre-fetch if source is configured
            if (activeSource != null) {
                // Check VPN requirement
                val prefsManager = PreferencesManager.getInstance(this)
                if (prefsManager.isVpnWarningEnabled() && !VPNDetector.isVpnActive(this)) {
                    // Skip pre-fetch if VPN required but not active
                    // User will see VPN warning dialog and can manually refresh
                    return
                }

                // Trigger parallel background load
                val repository = ContentRepository.getInstance(this)
                val (moviesResult, seriesResult, liveResult) = repository.loadAllContentParallel()

                // Log results (success/failure)
                val moviesStatus =
                    if (moviesResult.isSuccess) {
                        "OK (${moviesResult.getOrNull()?.size ?: 0} items)"
                    } else {
                        "FAILED"
                    }
                val seriesStatus =
                    if (seriesResult.isSuccess) {
                        "OK (${seriesResult.getOrNull()?.size ?: 0} items)"
                    } else {
                        "FAILED"
                    }
                val liveStatus =
                    if (liveResult.isSuccess) {
                        "OK (${liveResult.getOrNull()?.size ?: 0} items)"
                    } else {
                        "FAILED"
                    }

                android.util.Log.d(
                    "IPTVApplication",
                    "Background pre-fetch completed - Movies: $moviesStatus, Series: $seriesStatus, Live: $liveStatus",
                )
            }
        } catch (e: Exception) {
            // Silently fail - user can trigger manual refresh
            android.util.Log.e("IPTVApplication", "Background pre-fetch failed", e)
        }
    }
}
