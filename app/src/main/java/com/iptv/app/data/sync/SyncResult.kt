package com.iptv.app.data.sync

/**
 * Result of background sync operation.
 */
sealed class SyncResult {
    /**
     * Sync completed (partial success allowed - see individual flags)
     */
    data class Success(
        val totalDiffs: Int,
        val moviesSuccess: Boolean,
        val seriesSuccess: Boolean,
        val liveSuccess: Boolean
    ) : SyncResult()
    
    /**
     * Sync failed completely
     */
    data class Failure(val error: Throwable?) : SyncResult()
}
