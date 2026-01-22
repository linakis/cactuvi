package com.iptv.app

import android.app.Application
import com.iptv.app.data.db.AppDatabase
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.services.BackgroundSyncWorker
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.PreferencesManager
import com.iptv.app.utils.SourceManager
import com.iptv.app.utils.VPNDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IPTVApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        /**
         * Flag indicating whether VPN warning needs to be shown.
         * Activities can check this flag and show VPNWarningDialog.
         */
        @Volatile
        var needsVpnWarning: Boolean = false
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Check VPN status on app start
        checkVpnStatus()
        
        // Migrate existing credentials to source if needed
        applicationScope.launch {
            migrateToMultiSource()
        }
        
        // Schedule background sync (periodic work)
        BackgroundSyncWorker.schedule(this)
        
        // Trigger immediate sync if cache exists (stale-while-revalidate pattern)
        applicationScope.launch {
            triggerInitialSync()
        }
    }
    
    /**
     * Check VPN status and set flag for activities to show warning.
     * Does not show dialog directly - lets first visible activity handle it.
     */
    private fun checkVpnStatus() {
        val prefsManager = PreferencesManager.getInstance(this)
        
        if (prefsManager.isVpnWarningEnabled() && !VPNDetector.isVpnActive(this)) {
            needsVpnWarning = true
        }
    }
    
    /**
     * Migrate existing single-source credentials to multi-source system on first run.
     * Creates a "default" source from CredentialsManager if no sources exist.
     */
    private suspend fun migrateToMultiSource() {
        try {
            val sourceManager = SourceManager.getInstance(this)
            sourceManager.migrateCurrentCredentialsToSource()
        } catch (e: Exception) {
            // Silently fail - user can manually add source later
        }
    }
    
    /**
     * Trigger immediate background sync if cache exists.
     * For first launch (no cache), LoadingActivity handles initial data fetch.
     */
    private suspend fun triggerInitialSync() {
        try {
            val database = AppDatabase.getInstance(this)
            
            // Check if any cache exists
            val hasMovies = database.cacheMetadataDao().get("movies")?.itemCount ?: 0 > 0
            val hasSeries = database.cacheMetadataDao().get("series")?.itemCount ?: 0 > 0
            val hasLive = database.cacheMetadataDao().get("live")?.itemCount ?: 0 > 0
            
            // If cache exists, trigger immediate one-time sync (background refresh)
            if (hasMovies || hasSeries || hasLive) {
                BackgroundSyncWorker.syncNow(this)
            }
        } catch (e: Exception) {
            // Silently fail - periodic sync will retry
        }
    }
}
