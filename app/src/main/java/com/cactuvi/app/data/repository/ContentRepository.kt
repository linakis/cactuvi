package com.cactuvi.app.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.cactuvi.app.data.api.ApiClient
import com.cactuvi.app.data.api.XtreamApiService
import com.cactuvi.app.data.db.AppDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cactuvi.app.data.db.entities.*
import com.cactuvi.app.data.db.mappers.*
import com.cactuvi.app.data.models.*
import com.cactuvi.app.utils.CategoryGrouper
import com.cactuvi.app.utils.PreferencesManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.PerformanceLogger
import com.cactuvi.app.utils.VPNDetector
import com.cactuvi.app.utils.StreamingJsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.time.Duration.Companion.minutes

/**
 * ContentRepository - Data layer for IPTV content
 * 
 * ==================== ARCHITECTURE ====================
 * 
 * This repository uses REACTIVE STATE MANAGEMENT with StateFlow and SharedFlow
 * to provide a single source of truth for data loading state.
 * 
 * PATTERN: StateFlow<DataState<T>> + SharedFlow<Effect>
 * 
 * - StateFlow<DataState<Unit>>: UI state (Loading/Success/Error)
 *   - Conflation OK: UI only cares about latest state
 *   - Built-in distinctUntilChanged prevents redundant updates
 * 
 * - SharedFlow<Effect>: One-time effects (logs, analytics, silent errors)
 *   - No conflation: Every effect must execute
 *   - No replay: Effects are fire-once actions
 * 
 * ==================== RACE CONDITION PREVENTION ====================
 * 
 * Multiple concurrent calls are prevented by:
 * 1. Early exit check BEFORE mutex acquisition
 * 2. Double-check inside mutex (thread-safe)
 * 3. Mutex ensures serial execution of API fetches
 * 4. StateFlow conflation prevents redundant UI updates
 * 
 * Example: 4 loadSeries() calls → Only 1 executes, others exit early
 * 
 * ==================== USAGE PATTERN ====================
 * 
 * In Fragment/Activity:
 * ```kotlin
 * // Observe state changes
 * lifecycleScope.launch {
 *     repository.seriesState.collectLatest { state ->
 *         when (state) {
 *             is DataState.Loading -> showSpinner()
 *             is DataState.Success -> refreshUI()
 *             is DataState.Error -> showError()
 *         }
 *     }
 * }
 * 
 * // Observe effects (one-time actions)
 * lifecycleScope.launch {
 *     repository.seriesEffects.collect { effect ->
 *         when (effect) {
 *             is LoadSuccess -> Log.d("Success", ...)
 *             is LoadError -> if (!hasCachedData) showToast()
 *         }
 *     }
 * }
 * 
 * // Trigger load (safe to call multiple times)
 * repository.loadSeries(forceRefresh = false)
 * ```
 * 
 * ==================== BACKGROUND REFRESH UX ====================
 * 
 * - Initial load without cache → Show loading spinner
 * - Initial load fails without cache → Show error screen with retry
 * - Background refresh with cache → Show data + refresh indicator
 * - Background refresh fails with cache → SILENT (log only via effects)
 * 
 * This prevents annoying error toasts during background refreshes when
 * the user already has usable cached data.
 */

/**
 * Exception thrown when VPN is required but not active.
 */
class VpnRequiredException : Exception("VPN connection required but not active")

/**
 * One-time effects for Series loading.
 * Used for logging, analytics, and performance monitoring.
 */
sealed class SeriesEffect {
    /**
     * Load completed successfully.
     * @param itemCount Number of items inserted
     * @param durationMs Time taken to complete load (milliseconds)
     * @param fromCache True if served from cache, false if fetched from API
     */
    data class LoadSuccess(
        val itemCount: Int,
        val durationMs: Long = 0,
        val fromCache: Boolean = false
    ) : SeriesEffect()
    
    /**
     * Load failed.
     * @param message Error message
     * @param hasCachedData True if cached data is available
     * @param durationMs Time until failure (milliseconds)
     */
    data class LoadError(
        val message: String,
        val hasCachedData: Boolean,
        val durationMs: Long = 0
    ) : SeriesEffect()
}

/**
 * One-time effects for Movies loading.
 * Used for logging, analytics, and performance monitoring.
 */
sealed class MoviesEffect {
    data class LoadSuccess(
        val itemCount: Int,
        val durationMs: Long = 0,
        val fromCache: Boolean = false
    ) : MoviesEffect()
    
    data class LoadError(
        val message: String,
        val hasCachedData: Boolean,
        val durationMs: Long = 0
    ) : MoviesEffect()
}

/**
 * One-time effects for Live channels loading.
 * Used for logging, analytics, and performance monitoring.
 */
sealed class LiveEffect {
    data class LoadSuccess(
        val itemCount: Int,
        val durationMs: Long = 0,
        val fromCache: Boolean = false
    ) : LiveEffect()
    
    data class LoadError(
        val message: String,
        val hasCachedData: Boolean,
        val durationMs: Long = 0
    ) : LiveEffect()
}

class ContentRepository(
    private val sourceManager: SourceManager,
    private val context: Context
) {
    
    private var apiService: XtreamApiService? = null
    private val database = AppDatabase.getInstance(context)
    private val dbWriter = database.getDbWriter()
    private val gson = Gson()
    private var currentSourceId: String? = null
    
    // ========== REACTIVE STATE MANAGEMENT ==========
    
    // Series: State (what to display) + Effects (one-time actions)
    private val _seriesState = MutableStateFlow<DataState<Unit>>(DataState.Success(Unit))
    val seriesState: StateFlow<DataState<Unit>> = _seriesState.asStateFlow()
    private val _seriesEffects = MutableSharedFlow<SeriesEffect>()
    val seriesEffects: SharedFlow<SeriesEffect> = _seriesEffects.asSharedFlow()
    
    // Movies: State (what to display) + Effects (one-time actions)
    private val _moviesState = MutableStateFlow<DataState<Unit>>(DataState.Success(Unit))
    val moviesState: StateFlow<DataState<Unit>> = _moviesState.asStateFlow()
    private val _moviesEffects = MutableSharedFlow<MoviesEffect>()
    val moviesEffects: SharedFlow<MoviesEffect> = _moviesEffects.asSharedFlow()
    
    // Live: State (what to display) + Effects (one-time actions)
    private val _liveState = MutableStateFlow<DataState<Unit>>(DataState.Success(Unit))
    val liveState: StateFlow<DataState<Unit>> = _liveState.asStateFlow()
    private val _liveEffects = MutableSharedFlow<LiveEffect>()
    val liveEffects: SharedFlow<LiveEffect> = _liveEffects.asSharedFlow()
    
    // Legacy boolean flags for deprecated getSeries/getMovies/getLiveStreams methods
    private val _seriesLoading = MutableStateFlow(false)
    private val _moviesLoading = MutableStateFlow(false)
    private val _liveLoading = MutableStateFlow(false)
    
    @Deprecated("Use seriesState instead", ReplaceWith("seriesState"))
    val seriesLoading: StateFlow<Boolean> = _seriesLoading.asStateFlow()
    @Deprecated("Use moviesState instead", ReplaceWith("moviesState"))
    val moviesLoading: StateFlow<Boolean> = _moviesLoading.asStateFlow()
    @Deprecated("Use liveState instead", ReplaceWith("liveState"))
    val liveLoading: StateFlow<Boolean> = _liveLoading.asStateFlow()
    
    // Mutexes to prevent concurrent data loading and database corruption
    private val moviesMutex = Mutex()
    private val seriesMutex = Mutex()
    private val liveMutex = Mutex()
    
    companion object {
        // Cache Time-To-Live (TTL) in milliseconds
        // NOTE: TTL only used for fallback safety check (7 days) - background sync handles freshness
        private const val CACHE_TTL_FALLBACK = 7 * 24 * 60 * 60 * 1000L  // 7 days
        private const val CACHE_TTL_CATEGORIES = 7 * 24 * 60 * 60 * 1000L  // 7 days
        
        // Batch size for bulk inserts - optimized for performance
        // Phase 1: 500 (initial)
        // Phase 2: 999 (reduced transactions, stayed under SQLite variable limit)
        // Phase 3: 2500 (optimal transaction size per expert recommendations: 2k-5k)
        // Note: Multi-value INSERT uses 50-item chunks internally (unaffected by this)
        // This controls transaction boundaries and context switching frequency
        private const val BATCH_SIZE = 2500
        
        // TODO: Future optimization - implement parallel batch writes
        // With WAL mode enabled, SQLite supports concurrent writes from multiple threads
        // Could potentially achieve 2-3x speedup by processing batches in parallel queue
        // Implementation considerations:
        // - Use semaphore to limit concurrent writes (e.g., 3-4 concurrent batches)
        // - Monitor for database lock contention
        // - Test on low-end devices to ensure no memory pressure
        // - Example approach: coroutineScope { batches.chunked(3).map { async { insert(it) } }.awaitAll() }
    }
    
    /**
     * Optimized bulk insert with batching and transaction wrapping.
     * Processes items in batches to reduce memory pressure and improve performance.
     * 
     * NOTE: This is now used by streaming parsers where items arrive in batches.
     */
    private suspend fun <T> bulkInsertBatch(
        items: List<T>,
        insertBatch: suspend (List<T>) -> Unit
    ) {
        // Items are already batched by streaming parser, just insert them
        insertBatch(items)
    }
    
    private suspend fun getApiService(): XtreamApiService {
        val activeSource = sourceManager.getActiveSource()
        
        // Recreate service if source changed or doesn't exist
        if (apiService == null || currentSourceId != activeSource?.id) {
            if (activeSource != null) {
                apiService = ApiClient.createService(activeSource.server)
                currentSourceId = activeSource.id
            } else {
                throw IllegalStateException("No active source configured")
            }
        }
        return apiService!!
    }
    
    private suspend fun getActiveSourceId(): String {
        return sourceManager.getActiveSource()?.id 
            ?: throw IllegalStateException("No active source configured")
    }
    
    private suspend fun getCredentials(): Triple<String, String, String> {
        val source = sourceManager.getActiveSource()
            ?: throw IllegalStateException("No active source configured")
        return Triple(source.username, source.password, source.id)
    }
    
    /**
     * Retry helper with exponential backoff for API calls.
     * Attempts: 3 (initial + 2 retries)
     * Delays: 1s, 2s, 4s
     */
    private suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000,
        operation: suspend () -> T
    ): T {
        var currentAttempt = 0
        var currentDelay = initialDelayMs
        var lastException: Exception? = null
        
        while (currentAttempt < maxAttempts) {
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                currentAttempt++
                
                if (currentAttempt >= maxAttempts) {
                    break
                }
                
                PerformanceLogger.log("Retry attempt $currentAttempt failed: ${e.message}. Retrying in ${currentDelay}ms...")
                kotlinx.coroutines.delay(currentDelay)
                currentDelay *= 2 // Exponential backoff
            }
        }
        
        throw lastException ?: Exception("Operation failed after $maxAttempts attempts")
    }
    
    private fun isCacheValid(lastUpdated: Long, ttl: Long): Boolean {
        return (System.currentTimeMillis() - lastUpdated) < ttl
    }
    
    /**
     * Check if VPN is required and active. Throws VpnRequiredException if required but not active.
     * This blocks all API calls when VPN warning is enabled and VPN is not connected.
     */
    private suspend fun checkVpnRequirement() {
        val prefsManager = PreferencesManager.getInstance(context)
        if (prefsManager.isVpnWarningEnabled() && !VPNDetector.isVpnActive(context)) {
            throw VpnRequiredException()
        }
    }
    
    // ========== AUTHENTICATION ==========
    
    suspend fun authenticate(): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            checkVpnRequirement()
            val (username, password, _) = getCredentials()
            val response = getApiService().authenticate(username, password)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== PARALLEL LOADING (PHASE 3) ==========
    
    /**
     * Load all content types (Movies, Series, Live) in parallel.
     * 
     * This is the Phase 3 optimization that eliminates sequential parsing bottleneck.
     * All 3 content types fetch and parse concurrently (CPU parallelization).
     * Database writes remain serialized through DbWriter Mutex (no contention).
     * 
     * Expected performance:
     * - Movies: 60s → ~35s (parallel with Series/Live)
     * - Series: 30s → ~18s (parallel)
     * - Live: 40s → ~22s (parallel)
     * - Total: 124.6s → 75-85s (30-40% reduction)
     * 
     * @return Triple of results for (Movies, Series, Live)
     */
    suspend fun loadAllContentParallel(): Triple<Result<List<Movie>>, Result<List<Series>>, Result<List<LiveChannel>>> = 
        coroutineScope {
            PerformanceLogger.log("Starting parallel content load (Movies + Series + Live)")
            val startTime = PerformanceLogger.start("Parallel content load")
            
            // Launch all 3 content types concurrently on IO dispatcher
            // All use new FRP pattern (fire-and-forget, state managed via StateFlow)
            async(Dispatchers.IO) { 
                loadMovies(forceRefresh = false)  // NEW: Reactive pattern
            }
            async(Dispatchers.IO) { 
                loadSeries(forceRefresh = false)  // NEW: Reactive pattern
            }
            async(Dispatchers.IO) { 
                loadLive(forceRefresh = false)  // NEW: Reactive pattern
            }
            
            // All state managed via StateFlow - return dummy Results for backward compatibility
            val moviesResult = Result.success(emptyList<Movie>())
            val seriesResult = Result.success(emptyList<Series>())
            val liveResult = Result.success(emptyList<LiveChannel>())
            
            PerformanceLogger.end("Parallel content load", startTime, 
                "Movies: ${if (moviesResult.isSuccess) "OK" else "FAIL"}, " +
                "Series: ${if (seriesResult.isSuccess) "OK" else "FAIL"}, " +
                "Live: ${if (liveResult.isSuccess) "OK" else "FAIL"}")
            
            Triple(moviesResult, seriesResult, liveResult)
        }
    
    // ========== LIVE CHANNELS ==========
    
    /**
     * Load live channels data from API with reactive state management.
     * Emits state changes to liveState StateFlow.
     * Thread-safe: Uses mutex to prevent concurrent loads.
     * 
     * @param forceRefresh If true, bypass cache and force API fetch
     */
    suspend fun loadLive(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        // Race condition check - BEFORE mutex acquisition
        if (_liveState.value.isLoading() && !forceRefresh) {
            PerformanceLogger.log("loadLive: Already loading, skipping duplicate call")
            return@withContext
        }
        
        try {
            liveMutex.withLock {
                // Double-check inside mutex
                if (_liveState.value.isLoading() && !forceRefresh) {
                    return@withLock
                }
                
                // Emit Loading state
                _liveState.value = DataState.Loading
                
                // Check cache first (unless forcing refresh)
                if (!forceRefresh) {
                    val metadata = database.cacheMetadataDao().get("live")
                    if (metadata != null && metadata.itemCount > 0) {
                        val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                        if (!isExtremelyStale) {
                            PerformanceLogger.log("loadLive: Cache valid, skipping API fetch")
                            _liveState.value = DataState.Success(Unit)
                            return@withLock
                        }
                    }
                }
                
                // Fetch from API
                PerformanceLogger.log("loadLive: Starting API fetch")
                checkVpnRequirement()
                
                val (username, password, sourceId) = getCredentials()
                
                // Fetch from API with retry
                val responseBody = retryWithExponentialBackoff {
                    getApiService().getLiveStreams(username, password)
                }
                
                // Load categories for mapping
                val categories = getLiveCategories().getOrNull() ?: emptyList()
                val categoryMap = categories.associateBy { it.categoryId }
                
                // Clear old data for this source
                database.liveChannelDao().deleteBySourceId(sourceId)
                
                // Drop indices before batch inserts
                com.cactuvi.app.data.db.OptimizedBulkInsert.beginLiveChannelsInsert(database.getSqliteDatabase())
                
                // Accumulator for batching writes through DbWriter
                val accumulator = mutableListOf<LiveChannelEntity>()
                var totalInserted = 0
                
                try {
                    totalInserted = StreamingJsonParser.parseArrayInBatches(
                        responseBody = responseBody,
                        itemClass = LiveChannel::class.java,
                        batchSize = BATCH_SIZE,
                        processBatch = { channelBatch ->
                            val entities = channelBatch.map { channel ->
                                val categoryName = categoryMap[channel.categoryId]?.categoryName ?: ""
                                channel.categoryName = categoryName
                                channel.toEntity(sourceId, categoryName)
                            }
                            
                            // Accumulate entities
                            accumulator.addAll(entities)
                            
                            // Write in 5k chunks through DbWriter
                            if (accumulator.size >= 5000) {
                                dbWriter.writeLiveChannels(accumulator.toList())
                                accumulator.clear()
                            }
                        },
                        onProgress = { count ->
                            PerformanceLogger.log("Progress: $count live channels parsed and inserted")
                        }
                    )
                    
                    // Flush remaining items
                    if (accumulator.isNotEmpty()) {
                        dbWriter.writeLiveChannels(accumulator)
                        accumulator.clear()
                    }
                } catch (e: Exception) {
                    throw e
                } finally {
                    com.cactuvi.app.data.db.OptimizedBulkInsert.endLiveChannelsInsert(database.getSqliteDatabase())
                }
                
                // Update cache metadata
                val newMetadata = CacheMetadataEntity(
                    contentType = "live",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = totalInserted,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                
                PerformanceLogger.log("loadLive: Successfully inserted $totalInserted live channels")
                
                // Verify database count
                val dbCount = database.liveChannelDao().getCount()
                PerformanceLogger.log("loadLive: DB count verification: $dbCount")
                
                // Emit Success state
                _liveState.value = DataState.Success(Unit)
                
                // Emit success effect
                _liveEffects.emit(LiveEffect.LoadSuccess(totalInserted))
            }
        } catch (e: Exception) {
            android.util.Log.e("ContentRepository", "loadLive failed", e)
            
            // Check if we have cached data
            val cachedCount = database.cacheMetadataDao().get("live")?.itemCount ?: 0
            val hasCache = cachedCount > 0
            
            // Emit Error state
            _liveState.value = DataState.Error(
                error = e,
                cachedData = if (hasCache) Unit else null
            )
            
            // Emit error effect
            _liveEffects.emit(LiveEffect.LoadError(
                message = e.message ?: "Unknown error",
                hasCachedData = hasCache
            ))
        }
    }
    
    @Deprecated("Use loadLive() instead", ReplaceWith("loadLive(forceRefresh)"))
    suspend fun getLiveStreams(forceRefresh: Boolean = false): Result<List<LiveChannel>> = 
        withContext(Dispatchers.IO) {
            try {
                withTimeout(5.minutes.inWholeMilliseconds) {
                    liveMutex.withLock {
                        try {
                    // Check if already loading (prevent duplicate loads)
                    if (_liveLoading.value && !forceRefresh) {
                        PerformanceLogger.log("Live channels already loading, skip duplicate request")
                        return@withLock Result.success(emptyList())
                    }
                    
                    // Check cache metadata
                    val metadata = database.cacheMetadataDao().get("live")
                    
                    // Return cache immediately if exists and not forcing refresh
                    // Unless extremely stale (7+ days) - then force refresh for safety
                    if (!forceRefresh && metadata != null) {
                        val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                        if (!isExtremelyStale) {
                            val cachedChannels = database.liveChannelDao().getAll()
                            if (cachedChannels.isNotEmpty()) {
                                return@withLock Result.success(cachedChannels.map { it.toModel() })
                            }
                        }
                    }
                
                // Set loading flag
                _liveLoading.value = true
                
                // Check VPN requirement before API call
                checkVpnRequirement()
                
                // Fetch from API
                val (username, password, sourceId) = getCredentials()
                
                // Fetch from API with retry
                val responseBody = retryWithExponentialBackoff {
                    getApiService().getLiveStreams(username, password)
                }
                
                // Get categories for names
                val categories = getLiveCategories().getOrNull() ?: emptyList()
                val categoryMap = categories.associateBy { it.categoryId }
                
                // Clear old data for this source before streaming
                database.liveChannelDao().deleteBySourceId(sourceId)
                
                // Drop indices before batch inserts
                com.cactuvi.app.data.db.OptimizedBulkInsert.beginLiveChannelsInsert(database.getSqliteDatabase())
                
                // Accumulator for batching writes through DbWriter (5k chunks)
                val accumulator = mutableListOf<LiveChannelEntity>()
                var totalInserted = 0
                
                try {
                    totalInserted = StreamingJsonParser.parseArrayInBatches(
                        responseBody = responseBody,
                        itemClass = LiveChannel::class.java,
                        batchSize = BATCH_SIZE,
                        processBatch = { channelBatch ->
                            val entities = channelBatch.map { channel ->
                                val categoryName = categoryMap[channel.categoryId]?.categoryName ?: ""
                                channel.categoryName = categoryName
                                channel.toEntity(sourceId, categoryName)
                            }
                            
                            // Accumulate entities
                            accumulator.addAll(entities)
                            
                            // Write in 5k chunks through DbWriter
                            if (accumulator.size >= 5000) {
                                dbWriter.writeLiveChannels(accumulator.toList())
                                accumulator.clear()
                            }
                        },
                        onProgress = { count ->
                            PerformanceLogger.log("Progress: $count live channels parsed and inserted")
                        }
                    )
                    
                    // Flush remaining items
                    if (accumulator.isNotEmpty()) {
                        dbWriter.writeLiveChannels(accumulator)
                        accumulator.clear()
                    }
                } catch (e: Exception) {
                    throw e
                } finally {
                    com.cactuvi.app.data.db.OptimizedBulkInsert.endLiveChannelsInsert(database.getSqliteDatabase())
                }
                
                // Update cache metadata
                val newMetadata = CacheMetadataEntity(
                    contentType = "live",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = totalInserted,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                
                // Return empty list since UI uses Paging
                Result.success(emptyList())
            } catch (e: Exception) {
                // Try returning cached data on error
                val cachedChannels = database.liveChannelDao().getAll()
                if (cachedChannels.isNotEmpty()) {
                    Result.success(cachedChannels.map { it.toModel() })
                } else {
                    Result.failure(e)
                }
                } finally {
                    // Always reset loading flag
                    _liveLoading.value = false
                }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                PerformanceLogger.log("getLiveStreams timed out after 5 minutes")
                _liveLoading.value = false
                Result.failure(Exception("Live channels data load timed out. Please try again.", e))
            }
        }
    
    suspend fun getLiveCategories(forceRefresh: Boolean = false): Result<List<Category>> = 
        withContext(Dispatchers.IO) {
            try {
                val cached = database.categoryDao().getAllByType("live")
                
                if (!forceRefresh && cached.isNotEmpty() &&
                    isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)) {
                    return@withContext Result.success(cached.map { it.toModel() })
                }
                
                // Check VPN requirement before API call
                checkVpnRequirement()
                
                val (username, password, sourceId) = getCredentials()
                val categories = getApiService().getLiveCategories(username, password)
                
                val entities = categories.map { it.toEntity(sourceId, "live") }
                database.categoryDao().insertAll(entities)
                
                // Cache navigation tree
                cacheLiveNavigationTree(categories)
                
                Result.success(categories)
            } catch (e: Exception) {
                val cached = database.categoryDao().getAllByType("live")
                if (cached.isNotEmpty()) {
                    Result.success(cached.map { it.toModel() })
                } else {
                    Result.failure(e)
                }
            }
        }
    
    // ========== MOVIES ==========
    
    /**
     * Load movies data from API with reactive state management.
     * Emits state changes to moviesState StateFlow.
     * Thread-safe: Uses mutex to prevent concurrent loads.
     * 
     * @param forceRefresh If true, bypass cache and force API fetch
     */
    suspend fun loadMovies(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        // Race condition check - BEFORE mutex acquisition
        if (_moviesState.value.isLoading() && !forceRefresh) {
            PerformanceLogger.log("loadMovies: Already loading, skipping duplicate call")
            return@withContext
        }
        
        try {
            moviesMutex.withLock {
                // Double-check inside mutex
                if (_moviesState.value.isLoading() && !forceRefresh) {
                    return@withLock
                }
                
                // Emit Loading state
                _moviesState.value = DataState.Loading
                
                // Check cache first (unless forcing refresh)
                if (!forceRefresh) {
                    val metadata = database.cacheMetadataDao().get("movies")
                    if (metadata != null && metadata.itemCount > 0) {
                        val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                        if (!isExtremelyStale) {
                            PerformanceLogger.log("loadMovies: Cache valid, skipping API fetch")
                            _moviesState.value = DataState.Success(Unit)
                            return@withLock
                        }
                    }
                }
                
                // Fetch from API
                PerformanceLogger.log("loadMovies: Starting API fetch")
                checkVpnRequirement()
                
                val (username, password, sourceId) = getCredentials()
                
                // Fetch from API with retry
                val responseBody = retryWithExponentialBackoff {
                    getApiService().getVodStreams(username, password)
                }
                
                // Load categories for mapping
                val categories = getMovieCategories().getOrNull() ?: emptyList()
                val categoryMap = categories.associateBy { it.categoryId }
                
                // Clear old data for this source
                database.movieDao().deleteBySourceId(sourceId)
                
                // Drop indices before batch inserts
                com.cactuvi.app.data.db.OptimizedBulkInsert.beginMoviesInsert(database.getSqliteDatabase())
                
                // Accumulator for batching writes through DbWriter
                val accumulator = mutableListOf<MovieEntity>()
                var totalInserted = 0
                
                try {
                    totalInserted = StreamingJsonParser.parseArrayInBatches(
                        responseBody = responseBody,
                        itemClass = Movie::class.java,
                        batchSize = BATCH_SIZE,
                        processBatch = { movieBatch ->
                            val entities = movieBatch.map { movie ->
                                val categoryName = categoryMap[movie.categoryId]?.categoryName ?: ""
                                movie.categoryName = categoryName
                                movie.toEntity(sourceId, categoryName)
                            }
                            
                            // Accumulate entities
                            accumulator.addAll(entities)
                            
                            // Write in 5k chunks through DbWriter
                            if (accumulator.size >= 5000) {
                                dbWriter.writeMovies(accumulator.toList())
                                accumulator.clear()
                            }
                        },
                        onProgress = { count ->
                            PerformanceLogger.log("Progress: $count movies parsed and inserted")
                        }
                    )
                    
                    // Flush remaining items
                    if (accumulator.isNotEmpty()) {
                        dbWriter.writeMovies(accumulator)
                        accumulator.clear()
                    }
                } catch (e: Exception) {
                    throw e
                } finally {
                    com.cactuvi.app.data.db.OptimizedBulkInsert.endMoviesInsert(database.getSqliteDatabase())
                }
                
                // Update cache metadata
                val newMetadata = CacheMetadataEntity(
                    contentType = "movies",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = totalInserted,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                
                PerformanceLogger.log("loadMovies: Successfully inserted $totalInserted movies")
                
                // Verify database count
                val dbCount = database.movieDao().getCount()
                PerformanceLogger.log("loadMovies: DB count verification: $dbCount")
                
                // Emit Success state
                _moviesState.value = DataState.Success(Unit)
                
                // Emit success effect
                _moviesEffects.emit(MoviesEffect.LoadSuccess(totalInserted))
            }
        } catch (e: Exception) {
            android.util.Log.e("ContentRepository", "loadMovies failed", e)
            
            // Check if we have cached data
            val cachedCount = database.cacheMetadataDao().get("movies")?.itemCount ?: 0
            val hasCache = cachedCount > 0
            
            // Emit Error state
            _moviesState.value = DataState.Error(
                error = e,
                cachedData = if (hasCache) Unit else null
            )
            
            // Emit error effect
            _moviesEffects.emit(MoviesEffect.LoadError(
                message = e.message ?: "Unknown error",
                hasCachedData = hasCache
            ))
        }
    }
    
    @Deprecated("Use loadMovies() instead", ReplaceWith("loadMovies(forceRefresh)"))
    suspend fun getMovies(forceRefresh: Boolean = false): Result<List<Movie>> = 
        withContext(Dispatchers.IO) {
            try {
                withTimeout(5.minutes.inWholeMilliseconds) {
                    moviesMutex.withLock {
                        val startTime = PerformanceLogger.start("Repository.getMovies")
            
                        try {
                    // Check if already loading (prevent duplicate loads)
                    if (_moviesLoading.value && !forceRefresh) {
                        PerformanceLogger.log("Movies already loading, skip duplicate request")
                        return@withLock Result.success(emptyList())
                    }
                    
                    // Check cache metadata
                    PerformanceLogger.logPhase("getMovies", "Checking cache metadata")
                    val metadataCheckStart = PerformanceLogger.start("Metadata cache check")
                    val metadata = database.cacheMetadataDao().get("movies")
                    PerformanceLogger.end("Metadata cache check", metadataCheckStart, 
                        "exists=${metadata != null}, count=${metadata?.itemCount ?: 0}")
                    
                    // Return cache immediately if exists and not forcing refresh
                    // Unless extremely stale (7+ days) - then force refresh for safety
                    if (!forceRefresh && metadata != null) {
                        val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                        if (!isExtremelyStale) {
                            PerformanceLogger.logPhase("getMovies", "Loading cached data")
                            val dataLoadStart = PerformanceLogger.start("Load cached movies")
                            val cached = database.movieDao().getAll()
                            PerformanceLogger.end("Load cached movies", dataLoadStart, "count=${cached.size}")
                            
                            if (cached.isNotEmpty()) {
                                PerformanceLogger.logCacheHit("movies", "getMovies", cached.size)
                                PerformanceLogger.end("Repository.getMovies", startTime, "HIT - count=${cached.size}")
                                return@withLock Result.success(cached.map { it.toModel() })
                            }
                        }
                    }
                
                // Set loading flag
                _moviesLoading.value = true
                
                // Cache miss - fetch from API
                PerformanceLogger.logCacheMiss("movies", "getMovies", if (forceRefresh) "forceRefresh" else "expired/empty")
                PerformanceLogger.logPhase("getMovies", "Fetching from API")
                
                // Check VPN requirement before API call
                checkVpnRequirement()
                
                val apiStart = PerformanceLogger.start("API fetch")
                val (username, password, sourceId) = getCredentials()
                
                // Fetch from API with retry
                val responseBody = retryWithExponentialBackoff {
                    getApiService().getVodStreams(username, password)
                }
                PerformanceLogger.end("API fetch", apiStart, "streaming started")
                
                // Get categories first (small dataset, can load fully)
                val categories = getMovieCategories().getOrNull() ?: emptyList()
                val categoryMap = categories.associateBy { it.categoryId }
                
                // Clear old data for this source before streaming new data to avoid conflicts
                PerformanceLogger.logPhase("getMovies", "Clearing old data for source")
                val clearStart = PerformanceLogger.start("Clear movies by sourceId")
                database.movieDao().deleteBySourceId(sourceId)
                PerformanceLogger.end("Clear movies by sourceId", clearStart)
                
                // Optimized streaming parse + insert with index management
                PerformanceLogger.logPhase("getMovies", "Streaming parse + optimized DB insert")
                val dbInsertStart = PerformanceLogger.start("Stream + optimized insert")
                
                // Drop indices before batch inserts
                com.cactuvi.app.data.db.OptimizedBulkInsert.beginMoviesInsert(database.getSqliteDatabase())
                
                // Accumulator for batching writes through DbWriter (5k chunks)
                val accumulator = mutableListOf<MovieEntity>()
                var totalInserted = 0
                
                try {
                    totalInserted = StreamingJsonParser.parseArrayInBatches(
                        responseBody = responseBody,
                        itemClass = Movie::class.java,
                        batchSize = BATCH_SIZE,
                        processBatch = { movieBatch ->
                            // Map to entities with category names
                            val entities = movieBatch.map { movie ->
                                val categoryName = categoryMap[movie.categoryId]?.categoryName ?: ""
                                movie.categoryName = categoryName
                                movie.toEntity(sourceId, categoryName)
                            }
                            
                            // Accumulate entities
                            accumulator.addAll(entities)
                            
                            // Write in 5k chunks through DbWriter (serialized, optimal transaction size)
                            if (accumulator.size >= 5000) {
                                dbWriter.writeMovies(accumulator.toList())
                                accumulator.clear()
                            }
                        },
                        onProgress = { count ->
                            PerformanceLogger.log("Progress: $count movies parsed and inserted")
                        }
                    )
                    
                    // Flush remaining items
                    if (accumulator.isNotEmpty()) {
                        dbWriter.writeMovies(accumulator)
                        accumulator.clear()
                    }
                } catch (e: Exception) {
                    PerformanceLogger.log("Streaming parse error: ${e.message}")
                    throw e
                } finally {
                    // Rebuild indices after all inserts
                    com.cactuvi.app.data.db.OptimizedBulkInsert.endMoviesInsert(database.getSqliteDatabase())
                }
                
                PerformanceLogger.end("Stream + optimized insert", dbInsertStart, "inserted=$totalInserted")
                
                PerformanceLogger.end("Stream + DB insert", dbInsertStart, "inserted=$totalInserted")
                
                // Update cache metadata
                PerformanceLogger.logPhase("getMovies", "Updating cache metadata")
                val metadataUpdateStart = PerformanceLogger.start("Update metadata")
                val newMetadata = CacheMetadataEntity(
                    contentType = "movies",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = totalInserted,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                PerformanceLogger.end("Update metadata", metadataUpdateStart)
                
                PerformanceLogger.end("Repository.getMovies", startTime, "SUCCESS - count=$totalInserted")
                
                // Return empty list since UI uses Paging to load data
                // Returning the full list would defeat the purpose of streaming
                Result.success(emptyList())
            } catch (e: Exception) {
                PerformanceLogger.log("getMovies API failed: ${e.message}")
                
                // Fallback to cache
                PerformanceLogger.logPhase("getMovies", "API failed, trying cache fallback")
                val cached = database.movieDao().getAll()
                if (cached.isNotEmpty()) {
                    PerformanceLogger.logCacheHit("movies", "getMovies_fallback", cached.size)
                    PerformanceLogger.end("Repository.getMovies", startTime, "FALLBACK - count=${cached.size}")
                    Result.success(cached.map { it.toModel() })
                } else {
                    PerformanceLogger.end("Repository.getMovies", startTime, "FAILED - no fallback")
                    Result.failure(e)
                }
                } finally {
                    // Always reset loading flag
                    _moviesLoading.value = false
                }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                PerformanceLogger.log("getMovies timed out after 5 minutes")
                _moviesLoading.value = false
                Result.failure(Exception("Movies data load timed out. Please try again.", e))
            }
        }
    
    suspend fun getMovieCategories(forceRefresh: Boolean = false): Result<List<Category>> = 
        withContext(Dispatchers.IO) {
            val startTime = PerformanceLogger.start("Repository.getMovieCategories")
            
            try {
                // Check cache
                PerformanceLogger.logPhase("getMovieCategories", "Checking cache")
                val cacheCheckStart = PerformanceLogger.start("Cache check")
                val cached = database.categoryDao().getAllByType("vod")
                PerformanceLogger.end("Cache check", cacheCheckStart, "cached=${cached.size}")
                
                if (!forceRefresh && cached.isNotEmpty() &&
                    isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)) {
                    PerformanceLogger.logCacheHit("movies", "categories", cached.size)
                    PerformanceLogger.end("Repository.getMovieCategories", startTime, "HIT - count=${cached.size}")
                    return@withContext Result.success(cached.map { it.toModel() })
                }
                
                // Cache miss - fetch from API
                PerformanceLogger.logCacheMiss("movies", "categories", if (forceRefresh) "forceRefresh" else "expired/empty")
                PerformanceLogger.logPhase("getMovieCategories", "Fetching from API")
                
                // Check VPN requirement before API call
                checkVpnRequirement()
                
                val apiStart = PerformanceLogger.start("API fetch")
                val (username, password, sourceId) = getCredentials()
                val categories = getApiService().getVodCategories(username, password)
                PerformanceLogger.end("API fetch", apiStart, "count=${categories.size}")
                
                // Insert into database
                PerformanceLogger.logPhase("getMovieCategories", "Inserting into DB")
                val dbInsertStart = PerformanceLogger.start("DB insert")
                val entities = categories.map { it.toEntity(sourceId, "vod") }
                database.categoryDao().insertAll(entities)
                PerformanceLogger.end("DB insert", dbInsertStart, "inserted=${entities.size}")
                
                // Cache navigation tree
                PerformanceLogger.logPhase("getMovieCategories", "Caching navigation tree")
                val treeCacheStart = PerformanceLogger.start("Cache navigation tree")
                cacheVodNavigationTree(categories)
                PerformanceLogger.end("Cache navigation tree", treeCacheStart)
                
                PerformanceLogger.end("Repository.getMovieCategories", startTime, "SUCCESS - count=${categories.size}")
                Result.success(categories)
            } catch (e: Exception) {
                PerformanceLogger.log("getMovieCategories API failed: ${e.message}")
                
                // Fallback to cache
                PerformanceLogger.logPhase("getMovieCategories", "API failed, trying cache fallback")
                val cached = database.categoryDao().getAllByType("vod")
                if (cached.isNotEmpty()) {
                    PerformanceLogger.logCacheHit("movies", "categories_fallback", cached.size)
                    PerformanceLogger.end("Repository.getMovieCategories", startTime, "FALLBACK - count=${cached.size}")
                    Result.success(cached.map { it.toModel() })
                } else {
                    PerformanceLogger.end("Repository.getMovieCategories", startTime, "FAILED - no fallback")
                    Result.failure(e)
                }
            }
        }
    
    suspend fun getMovieInfo(vodId: Int): Result<MovieInfo> = withContext(Dispatchers.IO) {
        try {
            checkVpnRequirement()
            val (username, password, _) = getCredentials()
            val info = getApiService().getVodInfo(username, password, vodId = vodId)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== SERIES ==========
    
    /**
     * Load series data from API with reactive state management.
     * Emits state changes to seriesState StateFlow:
     * - Loading: Data is being fetched
     * - Success: Data loaded and cached
     * - Error: Load failed (with optional cached data)
     *
     * UI observes seriesState reactively. This method NEVER returns Result.
     * Thread-safe: Uses mutex to prevent concurrent loads.
     * 
     * @param forceRefresh If true, bypass cache and force API fetch
     */
    suspend fun loadSeries(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        // Race condition check - BEFORE mutex acquisition
        if (_seriesState.value.isLoading() && !forceRefresh) {
            PerformanceLogger.log("loadSeries: Already loading, skipping duplicate call")
            return@withContext
        }
        
        try {
            seriesMutex.withLock {
                // Double-check inside mutex
                if (_seriesState.value.isLoading() && !forceRefresh) {
                    return@withLock
                }
                
                // Emit Loading state
                _seriesState.value = DataState.Loading
                
                // Check cache first (unless forcing refresh)
                if (!forceRefresh) {
                    val metadata = database.cacheMetadataDao().get("series")
                    if (metadata != null && metadata.itemCount > 0) {
                        val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                        if (!isExtremelyStale) {
                            val duration = System.currentTimeMillis() - startTime
                            PerformanceLogger.log("loadSeries: Cache valid, skipping API fetch (${duration}ms)")
                            _seriesState.value = DataState.Success(Unit)
                            _seriesEffects.emit(SeriesEffect.LoadSuccess(
                                itemCount = metadata.itemCount,
                                durationMs = duration,
                                fromCache = true
                            ))
                            return@withLock
                        }
                    }
                }
                
                // Fetch from API
                PerformanceLogger.log("loadSeries: Starting API fetch")
                checkVpnRequirement()
                
                val (username, password, sourceId) = getCredentials()
                
                // Fetch from API with retry
                val responseBody = retryWithExponentialBackoff {
                    getApiService().getSeries(username, password)
                }
                
                // Load categories for mapping
                val categoriesResult = getSeriesCategories()
                val categories = categoriesResult.getOrNull() ?: emptyList()
                val categoryMap = categories.associateBy { it.categoryId }
                
                // Clear old data for this source
                database.seriesDao().deleteBySourceId(sourceId)
                
                // Drop indices before batch inserts
                com.cactuvi.app.data.db.OptimizedBulkInsert.beginSeriesInsert(database.getSqliteDatabase())
                
                // Accumulator for batching writes through DbWriter
                val accumulator = mutableListOf<SeriesEntity>()
                var totalInserted = 0
                
                try {
                    totalInserted = StreamingJsonParser.parseArrayInBatches(
                        responseBody = responseBody,
                        itemClass = Series::class.java,
                        batchSize = BATCH_SIZE,
                        processBatch = { seriesBatch ->
                            val entities = seriesBatch.map { s ->
                                val categoryName = categoryMap[s.categoryId]?.categoryName ?: ""
                                s.categoryName = categoryName
                                s.toEntity(sourceId, categoryName)
                            }
                            
                            // Accumulate entities
                            accumulator.addAll(entities)
                            
                            // Write in 5k chunks through DbWriter
                            if (accumulator.size >= 5000) {
                                dbWriter.writeSeries(accumulator.toList())
                                accumulator.clear()
                            }
                        },
                        onProgress = { count ->
                            PerformanceLogger.log("Progress: $count series parsed and inserted")
                        }
                    )
                    
                    // Flush remaining items
                    if (accumulator.isNotEmpty()) {
                        dbWriter.writeSeries(accumulator)
                        accumulator.clear()
                    }
                } catch (e: Exception) {
                    throw e
                } finally {
                    com.cactuvi.app.data.db.OptimizedBulkInsert.endSeriesInsert(database.getSqliteDatabase())
                }
                
                // Update cache metadata
                val newMetadata = CacheMetadataEntity(
                    contentType = "series",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = totalInserted,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                
                PerformanceLogger.log("loadSeries: Successfully inserted $totalInserted series")
                
                // Verify database count
                val dbCount = database.seriesDao().getCount()
                PerformanceLogger.log("loadSeries: DB count verification: $dbCount")
                
                val duration = System.currentTimeMillis() - startTime
                PerformanceLogger.log("loadSeries: API fetch completed (${duration}ms)")
                
                // Emit Success state
                _seriesState.value = DataState.Success(Unit)
                
                // Emit success effect with performance metrics
                _seriesEffects.emit(SeriesEffect.LoadSuccess(
                    itemCount = totalInserted,
                    durationMs = duration,
                    fromCache = false
                ))
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("ContentRepository", "loadSeries failed after ${duration}ms", e)
            
            // Check if we have cached data
            val cachedCount = database.cacheMetadataDao().get("series")?.itemCount ?: 0
            val hasCache = cachedCount > 0
            
            // Emit Error state
            _seriesState.value = DataState.Error(
                error = e,
                cachedData = if (hasCache) Unit else null
            )
            
            // Emit error effect with performance metrics
            _seriesEffects.emit(SeriesEffect.LoadError(
                message = e.message ?: "Unknown error",
                hasCachedData = hasCache,
                durationMs = duration
            ))
        }
    }
    
    @Deprecated("Use loadSeries() instead", ReplaceWith("loadSeries(forceRefresh)"))
    suspend fun getSeries(forceRefresh: Boolean = false): Result<List<Series>> = 
        withContext(Dispatchers.IO) {
            try {
                withTimeout(5.minutes.inWholeMilliseconds) {
                    seriesMutex.withLock {
                        try {
                    // Check if already loading (prevent duplicate loads)
                    if (_seriesLoading.value && !forceRefresh) {
                        PerformanceLogger.log("Series already loading, skip duplicate request")
                        return@withLock Result.success(emptyList())
                    }
                    
                    // Check cache metadata
                    val metadata = database.cacheMetadataDao().get("series")
                    
                    // Return cache immediately if exists and not forcing refresh
                    // Unless extremely stale (7+ days) - then force refresh for safety
                    if (!forceRefresh && metadata != null) {
                        val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                        if (!isExtremelyStale) {
                            val cached = database.seriesDao().getAll()
                            if (cached.isNotEmpty()) {
                                return@withLock Result.success(cached.map { it.toModel() })
                            }
                        }
                    }
                
                // Set loading flag
                _seriesLoading.value = true
                
                // Check VPN requirement before API call
                checkVpnRequirement()
                
                val (username, password, sourceId) = getCredentials()
                
                // Fetch from API with retry
                val responseBody = retryWithExponentialBackoff {
                    getApiService().getSeries(username, password)
                }
                
                // DEBUG: Load and validate categories
                val categoriesResult = getSeriesCategories()
                PerformanceLogger.log("[DEBUG getSeries] Categories result: success=${categoriesResult.isSuccess}")
                val categories = categoriesResult.getOrNull() ?: emptyList()
                PerformanceLogger.log("[DEBUG getSeries] Categories loaded: count=${categories.size}")
                if (categories.size > 0) {
                    PerformanceLogger.log("[DEBUG getSeries] Sample categories: ${categories.take(3).map { "${it.categoryId}:${it.categoryName}" }}")
                }
                
                val categoryMap = categories.associateBy { it.categoryId }
                PerformanceLogger.log("[DEBUG getSeries] CategoryMap size: ${categoryMap.size}")
                
                // Clear old data for this source before streaming
                database.seriesDao().deleteBySourceId(sourceId)
                
                // Drop indices before batch inserts
                com.cactuvi.app.data.db.OptimizedBulkInsert.beginSeriesInsert(database.getSqliteDatabase())
                
                // Accumulator for batching writes through DbWriter (5k chunks)
                val accumulator = mutableListOf<SeriesEntity>()
                var totalInserted = 0
                
                try {
                    totalInserted = StreamingJsonParser.parseArrayInBatches(
                        responseBody = responseBody,
                        itemClass = Series::class.java,
                        batchSize = BATCH_SIZE,
                        processBatch = { seriesBatch ->
                            // DEBUG: Log first batch to check categoryId mapping
                            if (totalInserted == 0 && seriesBatch.isNotEmpty()) {
                                val sample = seriesBatch.first()
                                val categoryName = categoryMap[sample.categoryId]?.categoryName ?: ""
                                PerformanceLogger.log("[DEBUG getSeries] First series: id=${sample.seriesId}, name=${sample.name}, categoryId=${sample.categoryId}, mapped='$categoryName'")
                            }
                            
                            val entities = seriesBatch.map { s ->
                                val categoryName = categoryMap[s.categoryId]?.categoryName ?: ""
                                // DEBUG: Log unmapped categories
                                if (categoryName.isEmpty()) {
                                    PerformanceLogger.log("[DEBUG getSeries] UNMAPPED categoryId=${s.categoryId} for series=${s.name}")
                                }
                                s.categoryName = categoryName
                                s.toEntity(sourceId, categoryName)
                            }
                            
                            // Accumulate entities
                            accumulator.addAll(entities)
                            
                            // Write in 5k chunks through DbWriter
                            if (accumulator.size >= 5000) {
                                dbWriter.writeSeries(accumulator.toList())
                                accumulator.clear()
                            }
                        },
                        onProgress = { count ->
                            PerformanceLogger.log("Progress: $count series parsed and inserted")
                        }
                    )
                    
                    // Flush remaining items
                    if (accumulator.isNotEmpty()) {
                        dbWriter.writeSeries(accumulator)
                        accumulator.clear()
                    }
                } catch (e: Exception) {
                    throw e
                } finally {
                    com.cactuvi.app.data.db.OptimizedBulkInsert.endSeriesInsert(database.getSqliteDatabase())
                }
                
                // Update cache metadata
                val newMetadata = CacheMetadataEntity(
                    contentType = "series",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = totalInserted,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                
                // DEBUG: Verify data in database
                PerformanceLogger.log("[DEBUG getSeries] Total inserted: $totalInserted")
                val dbCount = database.seriesDao().getCount()
                PerformanceLogger.log("[DEBUG getSeries] DB count verification: $dbCount")
                
                // DEBUG: Check sample category counts
                val sampleCategories = categories.take(3)
                sampleCategories.forEach { cat ->
                    val count = database.seriesDao().getCountByCategory(cat.categoryId)
                    PerformanceLogger.log("[DEBUG getSeries] Category ${cat.categoryName} (${cat.categoryId}): $count items in DB")
                }
                
                // Return empty list since UI uses Paging
                Result.success(emptyList())
            } catch (e: Exception) {
                val cached = database.seriesDao().getAll()
                if (cached.isNotEmpty()) {
                    Result.success(cached.map { it.toModel() })
                } else {
                    Result.failure(e)
                }
                } finally {
                    // Always reset loading flag
                    _seriesLoading.value = false
                }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                PerformanceLogger.log("getSeries timed out after 5 minutes")
                _seriesLoading.value = false
                Result.failure(Exception("Series data load timed out. Please try again.", e))
            }
        }
    
    suspend fun getSeriesCategories(forceRefresh: Boolean = false): Result<List<Category>> = 
        withContext(Dispatchers.IO) {
            try {
                val cached = database.categoryDao().getAllByType("series")
                PerformanceLogger.log("[DEBUG getSeriesCategories] Cached count: ${cached.size}")
                
                if (!forceRefresh && cached.isNotEmpty() &&
                    isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)) {
                    PerformanceLogger.log("[DEBUG getSeriesCategories] Returning cached categories: ${cached.size}")
                    if (cached.size > 0) {
                        PerformanceLogger.log("[DEBUG getSeriesCategories] Sample cached: ${cached.take(3).map { "${it.categoryId}:${it.categoryName}" }}")
                    }
                    return@withContext Result.success(cached.map { it.toModel() })
                }
                
                // Check VPN requirement before API call
                checkVpnRequirement()
                
                val (username, password, sourceId) = getCredentials()
                val categories = getApiService().getSeriesCategories(username, password)
                PerformanceLogger.log("[DEBUG getSeriesCategories] Fetched from API: ${categories.size}")
                if (categories.size > 0) {
                    PerformanceLogger.log("[DEBUG getSeriesCategories] Sample API: ${categories.take(3).map { "${it.categoryId}:${it.categoryName}" }}")
                }
                
                val entities = categories.map { it.toEntity(sourceId, "series") }
                database.categoryDao().insertAll(entities)
                PerformanceLogger.log("[DEBUG getSeriesCategories] Inserted ${entities.size} categories into DB")
                
                // Cache navigation tree
                cacheSeriesNavigationTree(categories)
                
                Result.success(categories)
            } catch (e: Exception) {
                val cached = database.categoryDao().getAllByType("series")
                if (cached.isNotEmpty()) {
                    Result.success(cached.map { it.toModel() })
                } else {
                    Result.failure(e)
                }
            }
        }
    
    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfo> = withContext(Dispatchers.IO) {
        try {
            checkVpnRequirement()
            val (username, password, _) = getCredentials()
            val info = getApiService().getSeriesInfo(username, password, seriesId = seriesId)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== FAVORITES ==========
    
    suspend fun addToFavorites(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        rating: String?,
        categoryName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceId = getActiveSourceId()
            val favorite = FavoriteEntity(
                sourceId = sourceId,
                id = "${contentType}_${contentId}",
                contentId = contentId,
                contentType = contentType,
                contentName = contentName,
                posterUrl = posterUrl,
                rating = rating,
                categoryName = categoryName
            )
            database.favoriteDao().insert(favorite)
            
            // Update entity favorite status
            when (contentType) {
                "movie" -> database.movieDao().updateFavorite(contentId.toInt(), true)
                "series" -> database.seriesDao().updateFavorite(contentId.toInt(), true)
                "live_channel" -> database.liveChannelDao().updateFavorite(contentId.toInt(), true)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun removeFromFavorites(contentId: String, contentType: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val id = "${contentType}_${contentId}"
                database.favoriteDao().deleteById(sourceId, id)
                
                // Update entity favorite status
                when (contentType) {
                    "movie" -> database.movieDao().updateFavorite(contentId.toInt(), false)
                    "series" -> database.seriesDao().updateFavorite(contentId.toInt(), false)
                    "live_channel" -> database.liveChannelDao().updateFavorite(contentId.toInt(), false)
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getFavorites(contentType: String? = null): Result<List<FavoriteEntity>> = 
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val favorites = if (contentType != null) {
                    database.favoriteDao().getByType(sourceId, contentType)
                } else {
                    database.favoriteDao().getAll(sourceId)
                }
                Result.success(favorites)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun isFavorite(contentId: String, contentType: String): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val id = "${contentType}_${contentId}"
                Result.success(database.favoriteDao().isFavorite(sourceId, id))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    // ========== WATCH HISTORY ==========
    
    suspend fun updateWatchProgress(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        resumePosition: Long,
        duration: Long,
        seriesId: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceId = getActiveSourceId()
            val isCompleted = (resumePosition.toDouble() / duration) > 0.9
            
            val history = WatchHistoryEntity(
                sourceId = sourceId,
                contentId = contentId,
                contentType = contentType,
                contentName = contentName,
                posterUrl = posterUrl,
                resumePosition = resumePosition,
                duration = duration,
                lastWatched = System.currentTimeMillis(),
                isCompleted = isCompleted,
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber
            )
            
            database.watchHistoryDao().insert(history)
            
            // Update movie resume position if applicable
            if (contentType == "movie") {
                database.movieDao().updateResumePosition(contentId.toInt(), resumePosition)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getWatchHistory(limit: Int = 20): Result<List<WatchHistoryEntity>> = 
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val history = database.watchHistoryDao().getIncomplete(sourceId, limit)
                Result.success(history)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun clearWatchHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.watchHistoryDao().clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteWatchHistoryItem(contentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceId = getActiveSourceId()
            val item = database.watchHistoryDao().getByContentId(sourceId, contentId, "movie") 
                ?: database.watchHistoryDao().getByContentId(sourceId, contentId, "series")
                ?: database.watchHistoryDao().getByContentId(sourceId, contentId, "live_channel")
            
            item?.let {
                database.watchHistoryDao().delete(it)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== PAGING METHODS ==========
    
    fun getLiveStreamsPaged(categoryId: String? = null): Flow<PagingData<LiveChannel>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.liveChannelDao().getByCategoryIdPaged(categoryId)
            } else {
                database.liveChannelDao().getAllPaged()
            }
        }
        
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 10
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow.flowMap { pagingData ->
            pagingData.map { entity -> entity.toModel() }
        }
    }
    
    fun getMoviesPaged(categoryId: String? = null): Flow<PagingData<Movie>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.movieDao().getByCategoryIdPaged(categoryId)
            } else {
                database.movieDao().getAllPaged()
            }
        }
        
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 10
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow.flowMap { pagingData ->
            pagingData.map { entity -> entity.toModel() }
        }
    }
    
    fun getSeriesPaged(categoryId: String? = null): Flow<PagingData<Series>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.seriesDao().getByCategoryIdPaged(categoryId)
            } else {
                database.seriesDao().getAllPaged()
            }
        }
        
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 10
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow.flowMap { pagingData ->
            pagingData.map { entity -> entity.toModel() }
        }
    }
    
    // ========== NAVIGATION TREE CACHING ==========
    
    private suspend fun cacheVodNavigationTree(categories: List<Category>, separator: String = "-") {
        val tree = CategoryGrouper.buildVodNavigationTree(categories)
        val sourceId = getActiveSourceId()
        
        val entities = tree.groups.map { group ->
            NavigationGroupEntity(
                sourceId = sourceId,
                type = "vod",
                groupName = group.name,
                categoryIdsJson = gson.toJson(group.categories.map { it.categoryId }),
                separator = separator
            )
        }
        
        database.navigationGroupDao().deleteByType("vod")
        database.navigationGroupDao().insertAll(entities)
    }
    
    private suspend fun cacheSeriesNavigationTree(categories: List<Category>, separator: String = "FIRST_WORD") {
        val tree = CategoryGrouper.buildSeriesNavigationTree(categories)
        val sourceId = getActiveSourceId()
        
        val entities = tree.groups.map { group ->
            NavigationGroupEntity(
                sourceId = sourceId,
                type = "series",
                groupName = group.name,
                categoryIdsJson = gson.toJson(group.categories.map { it.categoryId }),
                separator = separator
            )
        }
        
        database.navigationGroupDao().deleteByType("series")
        database.navigationGroupDao().insertAll(entities)
    }
    
    private suspend fun cacheLiveNavigationTree(categories: List<Category>, separator: String = "|") {
        val tree = CategoryGrouper.buildLiveNavigationTree(categories)
        val sourceId = getActiveSourceId()
        
        val entities = tree.groups.map { group ->
            NavigationGroupEntity(
                sourceId = sourceId,
                type = "live",
                groupName = group.name,
                categoryIdsJson = gson.toJson(group.categories.map { it.categoryId }),
                separator = separator
            )
        }
        
        database.navigationGroupDao().deleteByType("live")
        database.navigationGroupDao().insertAll(entities)
    }
    
    suspend fun getCachedVodNavigationTree(): CategoryGrouper.NavigationTree? = withContext(Dispatchers.IO) {
        val startTime = PerformanceLogger.start("Repository.getCachedVodNavigationTree")
        
        try {
            // Load navigation group entities
            PerformanceLogger.logPhase("getCachedVodNavigationTree", "Loading navigation groups")
            val entityLoadStart = PerformanceLogger.start("Load navigation entities")
            val entities = database.navigationGroupDao().getByType("vod")
            PerformanceLogger.end("Load navigation entities", entityLoadStart, "count=${entities.size}")
            
            if (entities.isEmpty()) {
                PerformanceLogger.logCacheMiss("movies", "navigationTree", "no entities")
                PerformanceLogger.end("Repository.getCachedVodNavigationTree", startTime, "MISS - empty")
                return@withContext null
            }
            
            // Check TTL
            val firstEntity = entities.first()
            if (!isCacheValid(firstEntity.lastUpdated, CACHE_TTL_CATEGORIES)) {
                PerformanceLogger.logCacheMiss("movies", "navigationTree", "expired TTL")
                PerformanceLogger.end("Repository.getCachedVodNavigationTree", startTime, "MISS - expired")
                return@withContext null
            }
            
            // Get all VOD categories
            PerformanceLogger.logPhase("getCachedVodNavigationTree", "Loading categories")
            val categoryLoadStart = PerformanceLogger.start("Load categories")
            val allCategories = database.categoryDao().getAllByType("vod").map { it.toModel() }
            PerformanceLogger.end("Load categories", categoryLoadStart, "count=${allCategories.size}")
            
            // Reconstruct navigation tree from cached groups
            PerformanceLogger.logPhase("getCachedVodNavigationTree", "Deserializing JSON and building tree")
            val deserializeStart = PerformanceLogger.start("Deserialize and build tree")
            val groups = entities.map { entity ->
                val type = object : TypeToken<List<String>>() {}.type
                val categoryIds: List<String> = gson.fromJson(entity.categoryIdsJson, type)
                val groupCategories = allCategories.filter { it.categoryId in categoryIds }
                CategoryGrouper.GroupNode(entity.groupName, groupCategories)
            }
            PerformanceLogger.end("Deserialize and build tree", deserializeStart, "groups=${groups.size}")
            
            val tree = CategoryGrouper.NavigationTree(groups)
            PerformanceLogger.logCacheHit("movies", "navigationTree", groups.size)
            PerformanceLogger.end("Repository.getCachedVodNavigationTree", startTime, "HIT - groups=${groups.size}")
            tree
        } catch (e: Exception) {
            PerformanceLogger.log("getCachedVodNavigationTree failed: ${e.message}")
            PerformanceLogger.end("Repository.getCachedVodNavigationTree", startTime, "ERROR")
            null
        }
    }
    
    suspend fun getCachedSeriesNavigationTree(): CategoryGrouper.NavigationTree? = withContext(Dispatchers.IO) {
        try {
            val entities = database.navigationGroupDao().getByType("series")
            if (entities.isEmpty()) return@withContext null
            
            // Check TTL
            val firstEntity = entities.first()
            if (!isCacheValid(firstEntity.lastUpdated, CACHE_TTL_CATEGORIES)) {
                return@withContext null
            }
            
            // Get all series categories
            val allCategories = database.categoryDao().getAllByType("series").map { it.toModel() }
            
            // Reconstruct navigation tree from cached groups
            val groups = entities.map { entity ->
                val type = object : TypeToken<List<String>>() {}.type
                val categoryIds: List<String> = gson.fromJson(entity.categoryIdsJson, type)
                val groupCategories = allCategories.filter { it.categoryId in categoryIds }
                CategoryGrouper.GroupNode(entity.groupName, groupCategories)
            }
            
            CategoryGrouper.NavigationTree(groups)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getCachedLiveNavigationTree(): CategoryGrouper.NavigationTree? = withContext(Dispatchers.IO) {
        try {
            val entities = database.navigationGroupDao().getByType("live")
            if (entities.isEmpty()) return@withContext null
            
            // Check TTL
            val firstEntity = entities.first()
            if (!isCacheValid(firstEntity.lastUpdated, CACHE_TTL_CATEGORIES)) {
                return@withContext null
            }
            
            // Get all live categories
            val allCategories = database.categoryDao().getAllByType("live").map { it.toModel() }
            
            // Reconstruct navigation tree from cached groups
            val groups = entities.map { entity ->
                val type = object : TypeToken<List<String>>() {}.type
                val categoryIds: List<String> = gson.fromJson(entity.categoryIdsJson, type)
                val groupCategories = allCategories.filter { it.categoryId in categoryIds }
                CategoryGrouper.GroupNode(entity.groupName, groupCategories)
            }
            
            CategoryGrouper.NavigationTree(groups)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun invalidateVodNavigationTree() = withContext(Dispatchers.IO) {
        database.navigationGroupDao().deleteByType("vod")
    }
    
    suspend fun invalidateSeriesNavigationTree() = withContext(Dispatchers.IO) {
        database.navigationGroupDao().deleteByType("series")
    }
    
    suspend fun invalidateLiveNavigationTree() = withContext(Dispatchers.IO) {
        database.navigationGroupDao().deleteByType("live")
    }
    
    suspend fun invalidateAllNavigationTrees() = withContext(Dispatchers.IO) {
        database.navigationGroupDao().clear()
    }
    
    // Aliases for consistency
    suspend fun invalidateMovieNavigationCache() = invalidateVodNavigationTree()
    suspend fun invalidateSeriesNavigationCache() = invalidateSeriesNavigationTree()
    suspend fun invalidateLiveNavigationCache() = invalidateLiveNavigationTree()
    
    // ========== CACHE MANAGEMENT ==========
    
    suspend fun clearAllCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.liveChannelDao().clearAll()
            database.movieDao().clearAll()
            database.seriesDao().clearAll()
            database.categoryDao().clearAll()
            invalidateAllNavigationTrees()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clear all cached data for a specific source
     */
    suspend fun clearSourceCache(sourceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Clear all content for this source using sourceId-specific deletes
            database.liveChannelDao().deleteBySourceId(sourceId)
            database.movieDao().deleteBySourceId(sourceId)
            database.seriesDao().deleteBySourceId(sourceId)
            database.categoryDao().deleteBySourceId(sourceId)
            database.navigationGroupDao().clear()
            database.cacheMetadataDao().deleteAll()
            
            // Clear favorites and watch history for this source
            database.favoriteDao().clearBySource(sourceId)
            database.watchHistoryDao().clearBySource(sourceId)
            
            // Force recreate API service for new source
            apiService = null
            currentSourceId = null
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
