package com.iptv.app

import android.app.Application
import com.iptv.app.data.db.AppDatabase
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.services.BackgroundSyncWorker
import com.iptv.app.utils.CredentialsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IPTVApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        
        // Schedule background sync (periodic work)
        BackgroundSyncWorker.schedule(this)
        
        // Trigger immediate sync if cache exists (stale-while-revalidate pattern)
        applicationScope.launch {
            triggerInitialSync()
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
