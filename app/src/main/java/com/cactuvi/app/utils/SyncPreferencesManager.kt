package com.cactuvi.app.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages background sync preferences and tracks sync status.
 * Singleton pattern for app-wide access.
 */
class SyncPreferencesManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "sync_preferences"
        
        // Preference keys
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_LAST_SYNC_MOVIES = "last_sync_movies"
        private const val KEY_LAST_SYNC_SERIES = "last_sync_series"
        private const val KEY_LAST_SYNC_LIVE = "last_sync_live"
        
        // Default values
        const val DEFAULT_SYNC_INTERVAL_HOURS = 6L
        
        @Volatile
        private var INSTANCE: SyncPreferencesManager? = null
        
        fun getInstance(context: Context): SyncPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncPreferencesManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    // ========== SYNC SETTINGS ==========
    
    var isSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_ENABLED, value).apply()
    
    var syncIntervalHours: Long
        get() = prefs.getLong(KEY_SYNC_INTERVAL_HOURS, DEFAULT_SYNC_INTERVAL_HOURS)
        set(value) = prefs.edit().putLong(KEY_SYNC_INTERVAL_HOURS, value).apply()
    
    var isWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    
    // ========== LAST SYNC TIMESTAMPS ==========
    
    var lastSyncMovies: Long
        get() = prefs.getLong(KEY_LAST_SYNC_MOVIES, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_MOVIES, value).apply()
    
    var lastSyncSeries: Long
        get() = prefs.getLong(KEY_LAST_SYNC_SERIES, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_SERIES, value).apply()
    
    var lastSyncLive: Long
        get() = prefs.getLong(KEY_LAST_SYNC_LIVE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_LIVE, value).apply()
    
    // ========== HELPER METHODS ==========
    
    /**
     * Update last sync timestamp for a specific content type
     */
    fun updateLastSync(contentType: String, timestamp: Long = System.currentTimeMillis()) {
        when (contentType) {
            "movies" -> lastSyncMovies = timestamp
            "series" -> lastSyncSeries = timestamp
            "live" -> lastSyncLive = timestamp
        }
    }
    
    /**
     * Get last sync timestamp for a specific content type
     */
    fun getLastSync(contentType: String): Long {
        return when (contentType) {
            "movies" -> lastSyncMovies
            "series" -> lastSyncSeries
            "live" -> lastSyncLive
            else -> 0L
        }
    }
    
    /**
     * Get time since last sync in milliseconds
     */
    fun getTimeSinceLastSync(contentType: String): Long {
        val lastSync = getLastSync(contentType)
        return if (lastSync > 0) {
            System.currentTimeMillis() - lastSync
        } else {
            Long.MAX_VALUE
        }
    }
    
    /**
     * Check if sync is needed based on interval
     */
    fun isSyncNeeded(contentType: String): Boolean {
        if (!isSyncEnabled) return false
        
        val timeSinceLastSync = getTimeSinceLastSync(contentType)
        val intervalMillis = syncIntervalHours * 60 * 60 * 1000
        
        return timeSinceLastSync >= intervalMillis
    }
}
