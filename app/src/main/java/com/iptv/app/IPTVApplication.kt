package com.iptv.app

import android.app.Application
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.utils.CredentialsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IPTVApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        
        // Warm up navigation tree caches in background
        applicationScope.launch {
            warmupCaches()
        }
    }
    
    private suspend fun warmupCaches() {
        try {
            val repository = ContentRepository(
                CredentialsManager.getInstance(this),
                this
            )
            
            // Check each content type
            listOf("live", "vod", "series").forEach { type ->
                try {
                    val cachedTree = when (type) {
                        "live" -> repository.getCachedLiveNavigationTree()
                        "vod" -> repository.getCachedVodNavigationTree()
                        "series" -> repository.getCachedSeriesNavigationTree()
                        else -> null
                    }
                    
                    // If cache empty or expired, refresh categories
                    // This will auto-cache the navigation tree
                    if (cachedTree == null) {
                        when (type) {
                            "live" -> repository.getLiveCategories(forceRefresh = true)
                            "vod" -> repository.getMovieCategories(forceRefresh = true)
                            "series" -> repository.getSeriesCategories(forceRefresh = true)
                        }
                    }
                } catch (e: Exception) {
                    // Silently fail - user will load on demand
                }
            }
        } catch (e: Exception) {
            // Silently fail - user will load on demand
        }
    }
}
