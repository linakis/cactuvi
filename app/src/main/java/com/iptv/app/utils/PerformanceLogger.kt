package com.iptv.app.utils

import android.util.Log
import com.iptv.app.BuildConfig

/**
 * Centralized performance logging utility for debugging cache loading and data pipeline performance.
 * Only logs in DEBUG builds to minimize overhead in production.
 */
object PerformanceLogger {
    
    private const val TAG = "IPTV_PERF"
    private val isEnabled = BuildConfig.DEBUG
    
    /**
     * Start timing an operation.
     * @param operation Name of the operation being timed
     * @return Current timestamp in milliseconds
     */
    fun start(operation: String): Long {
        val startTime = System.currentTimeMillis()
        if (isEnabled) {
            Log.d(TAG, "[$operation] START")
        }
        return startTime
    }
    
    /**
     * End timing an operation and log the duration.
     * @param operation Name of the operation being timed
     * @param startTime Start timestamp from start()
     * @param metadata Optional additional information to log
     */
    fun end(operation: String, startTime: Long, metadata: String = "") {
        if (!isEnabled) return
        
        val duration = System.currentTimeMillis() - startTime
        val metadataStr = if (metadata.isNotEmpty()) " | $metadata" else ""
        Log.d(TAG, "[$operation] END | ${duration}ms$metadataStr")
    }
    
    /**
     * Log a general performance-related message.
     * @param message Message to log
     */
    fun log(message: String) {
        if (isEnabled) {
            Log.d(TAG, message)
        }
    }
    
    /**
     * Log a cache hit event.
     * @param contentType Type of content (movies, series, live)
     * @param operation Specific operation (e.g., "getAll", "getById")
     * @param count Number of items retrieved from cache
     */
    fun logCacheHit(contentType: String, operation: String, count: Int) {
        if (isEnabled) {
            Log.d(TAG, "[CACHE HIT] $contentType.$operation | count=$count")
        }
    }
    
    /**
     * Log a cache miss event.
     * @param contentType Type of content (movies, series, live)
     * @param operation Specific operation (e.g., "getAll", "getById")
     * @param reason Optional reason for the miss
     */
    fun logCacheMiss(contentType: String, operation: String, reason: String = "") {
        if (isEnabled) {
            val reasonStr = if (reason.isNotEmpty()) " | reason=$reason" else ""
            Log.d(TAG, "[CACHE MISS] $contentType.$operation$reasonStr")
        }
    }
    
    /**
     * Measure and log the execution time of a code block.
     * Usage: measureTime("operation name") { /* code to measure */ }
     */
    inline fun <T> measureTime(operation: String, metadata: String = "", block: () -> T): T {
        val startTime = start(operation)
        try {
            return block()
        } finally {
            end(operation, startTime, metadata)
        }
    }
    
    /**
     * Log a phase transition in a multi-step operation.
     * Useful for tracking progress through complex data pipelines.
     */
    fun logPhase(operation: String, phase: String, metadata: String = "") {
        if (isEnabled) {
            val metadataStr = if (metadata.isNotEmpty()) " | $metadata" else ""
            Log.d(TAG, "[$operation] PHASE: $phase$metadataStr")
        }
    }
}
