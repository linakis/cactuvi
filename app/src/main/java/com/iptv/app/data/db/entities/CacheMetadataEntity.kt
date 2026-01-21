package com.iptv.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata for tracking cache validity without loading full datasets.
 * Enables fast cache validation (~50ms vs ~8000ms for full data load).
 * Also tracks background sync status for stale-while-revalidate pattern.
 */
@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    /**
     * Content type identifier: "movies", "series", or "live"
     */
    @PrimaryKey
    val contentType: String,
    
    /**
     * Timestamp when cache was last updated (milliseconds since epoch)
     */
    val lastUpdated: Long,
    
    /**
     * Number of items in cache (movies, series, or channels)
     */
    val itemCount: Int,
    
    /**
     * Number of categories associated with this content type
     */
    val categoryCount: Int,
    
    /**
     * Timestamp of last background sync attempt (milliseconds since epoch)
     */
    val lastSyncAttempt: Long = 0L,
    
    /**
     * Timestamp of last successful background sync (milliseconds since epoch)
     */
    val lastSyncSuccess: Long = 0L,
    
    /**
     * Current sync status: "IDLE", "IN_PROGRESS", "SUCCESS", or "FAILED"
     */
    val syncStatus: String = "IDLE",
    
    /**
     * Error message from last failed sync (null if no error)
     */
    val lastSyncError: String? = null
)
