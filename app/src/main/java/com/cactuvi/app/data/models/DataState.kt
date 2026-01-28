package com.cactuvi.app.data.models

/**
 * Sealed class representing the explicit state of data synchronization.
 *
 * Models the complete lifecycle of a sync operation with explicit phases:
 * ```
 * Idle -> Fetching -> Parsing -> Persisting -> Indexing -> Success
 *                                                       -> Error (from any phase)
 * ```
 *
 * Each phase is explicitly modeled for:
 * - Clear UI state mapping (no sentinel values like empty lists)
 * - Unit testability without implicit state inference
 * - Progress tracking at appropriate phases
 *
 * Used with StateFlow to provide a single source of truth for sync state.
 */
sealed class DataState<out T> {

    /**
     * Idle state - no operation in progress. This is the initial state before any sync has started,
     * or after a successful sync when data is available in cache.
     *
     * @param hasCachedData True if valid cached data exists in database
     */
    data class Idle(val hasCachedData: Boolean = false) : DataState<Nothing>()

    /**
     * Fetching state - downloading data from API. Network request is in progress.
     *
     * @param bytesDownloaded Bytes downloaded so far (for large responses)
     * @param totalBytes Total expected bytes, null if unknown (chunked transfer)
     */
    data class Fetching(
        val bytesDownloaded: Long = 0,
        val totalBytes: Long? = null,
    ) : DataState<Nothing>() {
        /** Progress percentage (0-100) if total is known */
        val progress: Int?
            get() = totalBytes?.let { ((bytesDownloaded * 100) / it).toInt().coerceIn(0, 100) }
    }

    /**
     * Parsing state - parsing JSON response. Streaming parser is processing the response body.
     *
     * @param itemsParsed Number of items parsed so far
     * @param totalItems Total expected items, null if unknown
     */
    data class Parsing(
        val itemsParsed: Int = 0,
        val totalItems: Int? = null,
    ) : DataState<Nothing>() {
        val progress: Int?
            get() = totalItems?.let { ((itemsParsed * 100) / it).toInt().coerceIn(0, 100) }
    }

    /**
     * Persisting state - writing data to database. Batch inserts are in progress.
     *
     * @param itemsWritten Number of items written to database
     * @param totalItems Total items to write
     */
    data class Persisting(
        val itemsWritten: Int = 0,
        val totalItems: Int,
    ) : DataState<Nothing>() {
        val progress: Int
            get() = ((itemsWritten * 100) / totalItems).coerceIn(0, 100)
    }

    /**
     * Indexing state - rebuilding database indices. This is a blocking operation that occurs after
     * bulk inserts.
     */
    data object Indexing : DataState<Nothing>()

    /**
     * Success state - sync completed successfully. Data is available in database and ready to
     * display.
     *
     * @param itemCount Number of items synced
     * @param durationMs Total sync duration in milliseconds
     */
    data class Success(
        val itemCount: Int,
        val durationMs: Long = 0,
    ) : DataState<Nothing>()

    /**
     * Error state - sync failed.
     *
     * @param error The error that occurred
     * @param phase The phase where the error occurred
     * @param hasCachedData True if stale cached data is available as fallback
     */
    data class Error(
        val error: Throwable,
        val phase: SyncPhase,
        val hasCachedData: Boolean = false,
    ) : DataState<Nothing>()

    // ========== State Queries ==========

    /** Check if this state is idle (not actively syncing) */
    fun isIdle(): Boolean = this is Idle

    /** Check if this state represents active syncing (any in-progress phase) */
    fun isSyncing(): Boolean =
        this is Fetching || this is Parsing || this is Persisting || this is Indexing

    /** Check if this state is success */
    fun isSuccess(): Boolean = this is Success

    /** Check if this state is error */
    fun isError(): Boolean = this is Error

    /** Check if cached data is available (for fallback display during sync/error) */
    fun hasCachedData(): Boolean =
        when (this) {
            is Idle -> hasCachedData
            is Error -> hasCachedData
            is Success -> true
            else -> false
        }

    /**
     * Get overall progress percentage (0-100) across all phases. Weights: Fetching 10%, Parsing
     * 30%, Persisting 50%, Indexing 10%
     */
    fun getOverallProgress(): Int? =
        when (this) {
            is Idle -> null
            is Fetching -> progress?.let { it / 10 } ?: 0 // 0-10%
            is Parsing -> 10 + (progress?.let { it * 30 / 100 } ?: 0) // 10-40%
            is Persisting -> 40 + (progress * 50 / 100) // 40-90%
            is Indexing -> 90 // 90-100%
            is Success -> 100
            is Error -> null
        }
}

/** Enum representing sync phases for error reporting. */
enum class SyncPhase {
    FETCHING,
    PARSING,
    PERSISTING,
    INDEXING,
}
