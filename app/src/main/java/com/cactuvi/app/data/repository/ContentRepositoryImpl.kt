package com.cactuvi.app.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.cactuvi.app.data.api.ApiClient
import com.cactuvi.app.data.api.XtreamApiService
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.entities.*
import com.cactuvi.app.data.db.mappers.*
import com.cactuvi.app.data.mappers.toDomain
import com.cactuvi.app.data.models.*
import com.cactuvi.app.utils.CategoryGrouper
import com.cactuvi.app.utils.CategoryTreeBuilder
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.PerformanceLogger
import com.cactuvi.app.utils.PreferencesManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.StreamingJsonParser
import com.cactuvi.app.utils.SyncPreferencesManager
import com.cactuvi.app.utils.VPNDetector
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * ContentRepository - Data layer for IPTV content
 *
 * ==================== ARCHITECTURE ====================
 *
 * This repository uses REACTIVE STATE MANAGEMENT with StateFlow and SharedFlow to provide a single
 * source of truth for data loading state.
 *
 * PATTERN: StateFlow<DataState<T>> + SharedFlow<Effect>
 * - StateFlow<DataState<Unit>>: UI state (Loading/Success/Error)
 *     - Conflation OK: UI only cares about latest state
 *     - Built-in distinctUntilChanged prevents redundant updates
 * - SharedFlow<Effect>: One-time effects (logs, analytics, silent errors)
 *     - No conflation: Every effect must execute
 *     - No replay: Effects are fire-once actions
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
 * - Initial load without cache → Show loading spinner
 * - Initial load fails without cache → Show error screen with retry
 * - Background refresh with cache → Show data + refresh indicator
 * - Background refresh fails with cache → SILENT (log only via effects)
 *
 * This prevents annoying error toasts during background refreshes when the user already has usable
 * cached data.
 */

/** Exception thrown when VPN is required but not active. */
class VpnRequiredException : Exception("VPN connection required but not active")

/** One-time effects for Series loading. Used for logging, analytics, and performance monitoring. */
sealed class SeriesEffect {
    /**
     * Load completed successfully.
     *
     * @param itemCount Number of items inserted
     * @param durationMs Time taken to complete load (milliseconds)
     * @param fromCache True if served from cache, false if fetched from API
     */
    data class LoadSuccess(
        val itemCount: Int,
        val durationMs: Long = 0,
        val fromCache: Boolean = false,
    ) : SeriesEffect()

    /**
     * Load partially succeeded - some batches written, some failed.
     *
     * @param successCount Number of items successfully written
     * @param failedCount Number of items that failed to write
     * @param message Error message from failures
     */
    data class LoadPartialSuccess(
        val successCount: Int,
        val failedCount: Int,
        val message: String,
    ) : SeriesEffect()

    /**
     * Load failed.
     *
     * @param message Error message
     * @param hasCachedData True if cached data is available
     * @param durationMs Time until failure (milliseconds)
     */
    data class LoadError(
        val message: String,
        val hasCachedData: Boolean,
        val durationMs: Long = 0,
    ) : SeriesEffect()
}

/** One-time effects for Movies loading. Used for logging, analytics, and performance monitoring. */
sealed class MoviesEffect {
    data class LoadSuccess(
        val itemCount: Int,
        val durationMs: Long = 0,
        val fromCache: Boolean = false,
    ) : MoviesEffect()

    /**
     * Load partially succeeded - some batches written, some failed.
     *
     * @param successCount Number of items successfully written
     * @param failedCount Number of items that failed to write
     * @param message Error message from failures
     */
    data class LoadPartialSuccess(
        val successCount: Int,
        val failedCount: Int,
        val message: String,
    ) : MoviesEffect()

    data class LoadError(
        val message: String,
        val hasCachedData: Boolean,
        val durationMs: Long = 0,
    ) : MoviesEffect()
}

/**
 * One-time effects for Live channels loading. Used for logging, analytics, and performance
 * monitoring.
 */
sealed class LiveEffect {
    data class LoadSuccess(
        val itemCount: Int,
        val durationMs: Long = 0,
        val fromCache: Boolean = false,
    ) : LiveEffect()

    /**
     * Load partially succeeded - some batches written, some failed.
     *
     * @param successCount Number of items successfully written
     * @param failedCount Number of items that failed to write
     * @param message Error message from failures
     */
    data class LoadPartialSuccess(
        val successCount: Int,
        val failedCount: Int,
        val message: String,
    ) : LiveEffect()

    data class LoadError(
        val message: String,
        val hasCachedData: Boolean,
        val durationMs: Long = 0,
    ) : LiveEffect()
}

@Singleton
class ContentRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val sourceManager: SourceManager,
    private val preferencesManager: PreferencesManager,
    private val credentialsManager: CredentialsManager,
    private val syncPreferencesManager: SyncPreferencesManager
) : com.cactuvi.app.domain.repository.ContentRepository {

    private var apiService: XtreamApiService? = null
    private val dbWriter = database.getDbWriter()
    private val gson = Gson()
    private var currentSourceId: String? = null

    companion object {

        // Cache Time-To-Live (TTL) in milliseconds
        // NOTE: TTL only used for fallback safety check (7 days) - background sync handles
        // freshness
        private const val CACHE_TTL_FALLBACK = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val CACHE_TTL_CATEGORIES = 7 * 24 * 60 * 60 * 1000L // 7 days

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
        // - Example approach: coroutineScope { batches.chunked(3).map { async { insert(it) }
        // }.awaitAll() }
    }

    // ========== REACTIVE STATE MANAGEMENT ==========

    // Series: State (what to display) + Effects (one-time actions)
    private val _seriesState = MutableStateFlow<DataState<Unit>>(DataState.Success(Unit))
    override val seriesState: StateFlow<DataState<Unit>> = _seriesState.asStateFlow()
    private val _seriesEffects = MutableSharedFlow<SeriesEffect>()
    val seriesEffects: SharedFlow<SeriesEffect> = _seriesEffects.asSharedFlow()

    // Movies: State (what to display) + Effects (one-time actions)
    private val _moviesState = MutableStateFlow<DataState<Unit>>(DataState.Success(Unit))
    override val moviesState: StateFlow<DataState<Unit>> = _moviesState.asStateFlow()
    private val _moviesEffects = MutableSharedFlow<MoviesEffect>()
    val moviesEffects: SharedFlow<MoviesEffect> = _moviesEffects.asSharedFlow()

    // Live: State (what to display) + Effects (one-time actions)
    private val _liveState = MutableStateFlow<DataState<Unit>>(DataState.Success(Unit))
    override val liveState: StateFlow<DataState<Unit>> = _liveState.asStateFlow()
    private val _liveEffects = MutableSharedFlow<LiveEffect>()
    val liveEffects: SharedFlow<LiveEffect> = _liveEffects.asSharedFlow()

    // Mutexes to prevent concurrent data loading and database corruption
    private val moviesMutex = Mutex()
    private val seriesMutex = Mutex()
    private val liveMutex = Mutex()

    // ========== NEW REACTIVE API (Domain Layer Interface) ==========

    private val movieRefreshTrigger = MutableSharedFlow<Unit>(replay = 1)
    private val seriesRefreshTrigger = MutableSharedFlow<Unit>(replay = 1)
    private val liveRefreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    /**
     * Observe movies navigation tree reactively. Automatically fetches on first subscription, then
     * responds to manual refreshes.
     */
    override fun observeMovies():
        Flow<com.cactuvi.app.domain.model.Resource<com.cactuvi.app.domain.model.NavigationTree>> =
        movieRefreshTrigger
            .onStart { emit(Unit) } // Auto-trigger on subscribe
            .flatMapLatest {
                flow {
                    // Emit loading with cached data
                    val cached = getCachedVodNavigationTree()
                    if (cached != null) {
                        emit(
                            com.cactuvi.app.domain.model.Resource.Loading(
                                data =
                                    com.cactuvi.app.domain.model.NavigationTree(
                                        groups =
                                            cached.groups.map { group ->
                                                com.cactuvi.app.domain.model.GroupNode(
                                                    name = group.name,
                                                    categories =
                                                        group.categories.map { it.toDomain() },
                                                )
                                            },
                                    ),
                            ),
                        )
                    } else {
                        emit(
                            com.cactuvi.app.domain.model.Resource.Loading(),
                        )
                    }

                    try {
                        // Trigger legacy load (reuses existing implementation)
                        loadMovies(forceRefresh = false)

                        // Build navigation tree
                        val tree = getCachedVodNavigationTree()
                        if (tree != null) {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Success(
                                    data =
                                        com.cactuvi.app.domain.model.NavigationTree(
                                            groups =
                                                tree.groups.map { group ->
                                                    com.cactuvi.app.domain.model.GroupNode(
                                                        name = group.name,
                                                        categories =
                                                            group.categories.map { it.toDomain() },
                                                    )
                                                },
                                        ),
                                    source = com.cactuvi.app.domain.model.DataSource.NETWORK,
                                ),
                            )
                        } else {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(
                                    error = Exception("Failed to build navigation tree"),
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        val cachedTree = getCachedVodNavigationTree()
                        if (cachedTree != null) {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(
                                    error = e,
                                    data =
                                        com.cactuvi.app.domain.model.NavigationTree(
                                            groups =
                                                cachedTree.groups.map { group ->
                                                    com.cactuvi.app.domain.model.GroupNode(
                                                        name = group.name,
                                                        categories =
                                                            group.categories.map { it.toDomain() },
                                                    )
                                                },
                                        ),
                                ),
                            )
                        } else {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(error = e),
                            )
                        }
                    }
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun refreshMovies() {
        movieRefreshTrigger.emit(Unit)
    }

    /** Observe series navigation tree reactively. */
    override fun observeSeries():
        Flow<com.cactuvi.app.domain.model.Resource<com.cactuvi.app.domain.model.NavigationTree>> =
        seriesRefreshTrigger
            .onStart { emit(Unit) }
            .flatMapLatest {
                flow {
                    val cached = getCachedSeriesNavigationTree()
                    if (cached != null) {
                        emit(
                            com.cactuvi.app.domain.model.Resource.Loading(
                                data =
                                    com.cactuvi.app.domain.model.NavigationTree(
                                        groups =
                                            cached.groups.map { group ->
                                                com.cactuvi.app.domain.model.GroupNode(
                                                    name = group.name,
                                                    categories =
                                                        group.categories.map { it.toDomain() },
                                                )
                                            },
                                    ),
                            ),
                        )
                    } else {
                        emit(
                            com.cactuvi.app.domain.model.Resource.Loading(),
                        )
                    }

                    try {
                        loadSeries(forceRefresh = false)

                        val tree = getCachedSeriesNavigationTree()
                        if (tree != null) {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Success(
                                    data =
                                        com.cactuvi.app.domain.model.NavigationTree(
                                            groups =
                                                tree.groups.map { group ->
                                                    com.cactuvi.app.domain.model.GroupNode(
                                                        name = group.name,
                                                        categories =
                                                            group.categories.map { it.toDomain() },
                                                    )
                                                },
                                        ),
                                    source = com.cactuvi.app.domain.model.DataSource.NETWORK,
                                ),
                            )
                        } else {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(
                                    error = Exception("Failed to build navigation tree"),
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        val cachedTree = getCachedSeriesNavigationTree()
                        if (cachedTree != null) {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(
                                    error = e,
                                    data =
                                        com.cactuvi.app.domain.model.NavigationTree(
                                            groups =
                                                cachedTree.groups.map { group ->
                                                    com.cactuvi.app.domain.model.GroupNode(
                                                        name = group.name,
                                                        categories =
                                                            group.categories.map { it.toDomain() },
                                                    )
                                                },
                                        ),
                                ),
                            )
                        } else {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(error = e),
                            )
                        }
                    }
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun refreshSeries() {
        seriesRefreshTrigger.emit(Unit)
    }

    /** Observe live categories reactively. */
    override fun observeLive():
        Flow<com.cactuvi.app.domain.model.Resource<com.cactuvi.app.domain.model.NavigationTree>> =
        liveRefreshTrigger
            .onStart { emit(Unit) } // Auto-trigger on subscribe
            .flatMapLatest {
                flow {
                    // Emit loading with cached data
                    val cached = getCachedLiveNavigationTree()
                    if (cached != null) {
                        emit(
                            com.cactuvi.app.domain.model.Resource.Loading(
                                data =
                                    com.cactuvi.app.domain.model.NavigationTree(
                                        groups =
                                            cached.groups.map { group ->
                                                com.cactuvi.app.domain.model.GroupNode(
                                                    name = group.name,
                                                    categories =
                                                        group.categories.map { it.toDomain() },
                                                )
                                            },
                                    ),
                            ),
                        )
                    } else {
                        emit(
                            com.cactuvi.app.domain.model.Resource.Loading(),
                        )
                    }

                    try {
                        // Trigger legacy load (reuses existing implementation)
                        loadLive(forceRefresh = false)

                        // Build navigation tree
                        val tree = getCachedLiveNavigationTree()
                        if (tree != null) {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Success(
                                    data =
                                        com.cactuvi.app.domain.model.NavigationTree(
                                            groups =
                                                tree.groups.map { group ->
                                                    com.cactuvi.app.domain.model.GroupNode(
                                                        name = group.name,
                                                        categories =
                                                            group.categories.map { it.toDomain() },
                                                    )
                                                },
                                        ),
                                    source = com.cactuvi.app.domain.model.DataSource.NETWORK,
                                ),
                            )
                        } else {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(
                                    error = Exception("Failed to build navigation tree"),
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        val cachedTree = getCachedLiveNavigationTree()
                        if (cachedTree != null) {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(
                                    error = e,
                                    data =
                                        com.cactuvi.app.domain.model.NavigationTree(
                                            groups =
                                                cachedTree.groups.map { group ->
                                                    com.cactuvi.app.domain.model.GroupNode(
                                                        name = group.name,
                                                        categories =
                                                            group.categories.map { it.toDomain() },
                                                    )
                                                },
                                        ),
                                ),
                            )
                        } else {
                            emit(
                                com.cactuvi.app.domain.model.Resource.Error(error = e),
                            )
                        }
                    }
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun refreshLive() {
        liveRefreshTrigger.emit(Unit)
    }

    // ========== END NEW REACTIVE API ==========

    /**
     * Optimized bulk insert with batching and transaction wrapping. Processes items in batches to
     * reduce memory pressure and improve performance.
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
        val source =
            sourceManager.getActiveSource()
                ?: throw IllegalStateException("No active source configured")
        return Triple(source.username, source.password, source.id)
    }

    /**
     * Retry helper with exponential backoff for API calls. Attempts: 3 (initial + 2 retries)
     * Delays: 1s, 2s, 4s
     */
    private suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000,
        operation: suspend () -> T,
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

                PerformanceLogger.log(
                    "Retry attempt $currentAttempt failed: ${e.message}. Retrying in ${currentDelay}ms...",
                )
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

        if (preferencesManager.isVpnWarningEnabled() && !VPNDetector.isVpnActive(context)) {
            throw VpnRequiredException()
        }
    }

    // ========== AUTHENTICATION ==========

    override suspend fun authenticate(): Result<LoginResponse> =
        withContext(Dispatchers.IO) {
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
     * This is the Phase 3 optimization that eliminates sequential parsing bottleneck. All 3 content
     * types fetch and parse concurrently (CPU parallelization). Database writes remain serialized
     * through DbWriter Mutex (no contention).
     *
     * Expected performance:
     * - Movies: 60s → ~35s (parallel with Series/Live)
     * - Series: 30s → ~18s (parallel)
     * - Live: 40s → ~22s (parallel)
     * - Total: 124.6s → 75-85s (30-40% reduction)
     *
     * @return Triple of results for (Movies, Series, Live)
     */
    override suspend fun loadAllContentParallel():
        Triple<Result<List<Movie>>, Result<List<Series>>, Result<List<LiveChannel>>> =
        coroutineScope {
            PerformanceLogger.log("Starting parallel content load (Movies + Series + Live)")
            val startTime = PerformanceLogger.start("Parallel content load")

            // Launch all 3 content types concurrently on IO dispatcher
            // All use new FRP pattern (fire-and-forget, state managed via StateFlow)
            async(Dispatchers.IO) {
                loadMovies(forceRefresh = false) // NEW: Reactive pattern
            }
            async(Dispatchers.IO) {
                loadSeries(forceRefresh = false) // NEW: Reactive pattern
            }
            async(Dispatchers.IO) {
                loadLive(forceRefresh = false) // NEW: Reactive pattern
            }

            // All state managed via StateFlow - return dummy Results for backward compatibility
            val moviesResult = Result.success(emptyList<Movie>())
            val seriesResult = Result.success(emptyList<Series>())
            val liveResult = Result.success(emptyList<LiveChannel>())

            PerformanceLogger.end(
                "Parallel content load",
                startTime,
                "Movies: ${if (moviesResult.isSuccess) "OK" else "FAIL"}, " +
                    "Series: ${if (seriesResult.isSuccess) "OK" else "FAIL"}, " +
                    "Live: ${if (liveResult.isSuccess) "OK" else "FAIL"}",
            )

            Triple(moviesResult, seriesResult, liveResult)
        }

    // ========== LIVE CHANNELS ==========

    /**
     * Load live channels data from API with reactive state management. Emits state changes to
     * liveState StateFlow. Thread-safe: Uses mutex to prevent concurrent loads.
     *
     * @param forceRefresh If true, bypass cache and force API fetch
     */
    override suspend fun loadLive(forceRefresh: Boolean) =
        withContext(Dispatchers.IO) {
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
                    _liveState.value = DataState.Loading(progress = null)

                    // Check cache first (unless forcing refresh)
                    if (!forceRefresh) {
                        val metadata = database.cacheMetadataDao().get("live")
                        if (
                            metadata != null &&
                                metadata.itemCount > 0 &&
                                metadata.loadStatus == "SUCCESS"
                        ) {
                            val isExtremelyStale =
                                !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
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
                    com.cactuvi.app.data.db.OptimizedBulkInsert.beginLiveChannelsInsert(
                        database.getSqliteDatabase()
                    )

                    // Accumulator for batching writes through DbWriter
                    val accumulator = mutableListOf<LiveChannelEntity>()
                    var totalInserted = 0

                    try {
                        totalInserted =
                            StreamingJsonParser.parseArrayInBatches(
                                responseBody = responseBody,
                                itemClass = LiveChannel::class.java,
                                batchSize = BATCH_SIZE,
                                processBatch = { channelBatch ->
                                    val entities =
                                        channelBatch.map { channel ->
                                            val categoryName =
                                                categoryMap[channel.categoryId]?.categoryName ?: ""
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
                                    PerformanceLogger.log(
                                        "Progress: $count live channels parsed and inserted"
                                    )
                                },
                            )

                        // Flush remaining items
                        if (accumulator.isNotEmpty()) {
                            dbWriter.writeLiveChannels(accumulator)
                            accumulator.clear()
                        }
                    } catch (e: Exception) {
                        throw e
                    } finally {
                        com.cactuvi.app.data.db.OptimizedBulkInsert.endLiveChannelsInsert(
                            database.getSqliteDatabase()
                        )
                    }

                    // Verify database count
                    val dbCount = database.liveChannelDao().getCount()
                    PerformanceLogger.log(
                        "loadLive: Verification - expected: $totalInserted, actualDB: $dbCount"
                    )
                    if (dbCount == 0 || kotlin.math.abs(dbCount - totalInserted) > 100) {
                        val errorMsg =
                            "Database verification failed: reported $totalInserted, actual $dbCount"
                        android.util.Log.e("ContentRepository", "loadLive: $errorMsg")
                        throw Exception(errorMsg)
                    }

                    // Update cache metadata
                    val newMetadata =
                        CacheMetadataEntity(
                            contentType = "live",
                            lastUpdated = System.currentTimeMillis(),
                            itemCount = totalInserted,
                            categoryCount = categories.size,
                            loadStatus = "SUCCESS",
                        )
                    database.cacheMetadataDao().insert(newMetadata)

                    PerformanceLogger.log(
                        "loadLive: Successfully inserted $totalInserted live channels"
                    )

                    // Emit Success state
                    _liveState.value = DataState.Success(Unit)

                    // Emit success effect
                    _liveEffects.emit(LiveEffect.LoadSuccess(totalInserted))

                    // Update category childrenCount after live channels are loaded
                    try {
                        val liveCategories = getLiveCategories().getOrNull() ?: emptyList()
                        if (liveCategories.isNotEmpty()) {
                            PerformanceLogger.log("loadLive: Updating category childrenCount")
                            computeChildrenCounts(ContentType.LIVE, liveCategories)
                        }
                    } catch (countError: Exception) {
                        android.util.Log.w(
                            "ContentRepository",
                            "Failed to update live category counts: ${countError.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ContentRepository", "loadLive failed", e)

                // Check if we have cached data
                val cachedCount = database.cacheMetadataDao().get("live")?.itemCount ?: 0
                val hasCache = cachedCount > 0

                // Emit Error state
                _liveState.value =
                    DataState.Error(
                        error = e,
                        cachedData = if (hasCache) Unit else null,
                    )

                // Emit error effect
                _liveEffects.emit(
                    LiveEffect.LoadError(
                        message = e.message ?: "Unknown error",
                        hasCachedData = hasCache,
                    ),
                )
            }
        }

    @Deprecated("Use loadLive() instead", ReplaceWith("loadLive(forceRefresh)"))
    override suspend fun getLiveStreams(forceRefresh: Boolean): Result<List<LiveChannel>> =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(5.minutes.inWholeMilliseconds) {
                    liveMutex.withLock {
                        try {
                            // Check cache metadata
                            val metadata = database.cacheMetadataDao().get("live")

                            // Return cache immediately if exists and not forcing refresh
                            // Unless extremely stale (7+ days) - then force refresh for safety
                            if (!forceRefresh && metadata != null) {
                                val isExtremelyStale =
                                    !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                                if (!isExtremelyStale) {
                                    val cachedChannels = database.liveChannelDao().getAll()
                                    if (cachedChannels.isNotEmpty()) {
                                        return@withLock Result.success(
                                            cachedChannels.map { it.toModel() }
                                        )
                                    }
                                }
                            }

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
                            com.cactuvi.app.data.db.OptimizedBulkInsert.beginLiveChannelsInsert(
                                database.getSqliteDatabase()
                            )

                            // Accumulator for batching writes through DbWriter (5k chunks)
                            val accumulator = mutableListOf<LiveChannelEntity>()
                            var totalInserted = 0

                            try {
                                totalInserted =
                                    StreamingJsonParser.parseArrayInBatches(
                                        responseBody = responseBody,
                                        itemClass = LiveChannel::class.java,
                                        batchSize = BATCH_SIZE,
                                        processBatch = { channelBatch ->
                                            val entities =
                                                channelBatch.map { channel ->
                                                    val categoryName =
                                                        categoryMap[channel.categoryId]
                                                            ?.categoryName ?: ""
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
                                            PerformanceLogger.log(
                                                "Progress: $count live channels parsed and inserted"
                                            )
                                        },
                                    )

                                // Flush remaining items
                                if (accumulator.isNotEmpty()) {
                                    dbWriter.writeLiveChannels(accumulator)
                                    accumulator.clear()
                                }
                            } catch (e: Exception) {
                                throw e
                            } finally {
                                com.cactuvi.app.data.db.OptimizedBulkInsert.endLiveChannelsInsert(
                                    database.getSqliteDatabase()
                                )
                            }

                            // Update cache metadata
                            val newMetadata =
                                CacheMetadataEntity(
                                    contentType = "live",
                                    lastUpdated = System.currentTimeMillis(),
                                    itemCount = totalInserted,
                                    categoryCount = categories.size,
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
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                PerformanceLogger.log("getLiveStreams timed out after 5 minutes")
                Result.failure(Exception("Live channels data load timed out. Please try again.", e))
            }
        }

    override suspend fun getLiveCategories(forceRefresh: Boolean): Result<List<Category>> =
        withContext(Dispatchers.IO) {
            try {
                val cached = database.categoryDao().getAllByType("live")

                if (
                    !forceRefresh &&
                        cached.isNotEmpty() &&
                        isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)
                ) {
                    return@withContext Result.success(cached.map { it.toModel() })
                }

                // Check VPN requirement before API call
                checkVpnRequirement()

                val (username, password, sourceId) = getCredentials()
                val categories = getApiService().getLiveCategories(username, password)

                val entities = categories.map { it.toEntity(sourceId, "live") }
                database.categoryDao().insertAll(entities)

                // Compute children counts for navigation
                computeChildrenCounts(ContentType.LIVE, categories)

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
     * Result of async write operations. Tracks success/failure counts for partial success handling.
     */
    private data class AsyncWriteResult(
        val successCount: Int,
        val failedCount: Int,
        val errors: List<Exception>,
    )

    /**
     * Load movies data from API with async write queue for network timeout prevention. Emits state
     * changes to moviesState StateFlow with progress updates. Thread-safe: Uses mutex to prevent
     * concurrent loads.
     *
     * **Async Write Pattern**:
     * - Parses JSON and sends batches to background write queue
     * - HTTP connection closes quickly (~10-20 seconds)
     * - Database writes continue asynchronously without blocking network
     * - Progress updates emitted every 10% (0%, 10%, 20%, ..., 100%)
     * - Partial success preserved if some batches fail
     *
     * **Cache Merge Strategy**:
     * - Checks loadStatus in cache metadata
     * - If status="PARTIAL", merges new data with existing instead of deleting
     * - Room's REPLACE strategy handles deduplication automatically
     *
     * @param forceRefresh If true, bypass cache and force API fetch
     */
    override suspend fun loadMovies(forceRefresh: Boolean) =
        withContext(Dispatchers.IO) {
            // Race condition check - BEFORE mutex acquisition
            val currentState = _moviesState.value
            if (currentState is DataState.Loading && !forceRefresh) {
                PerformanceLogger.log("loadMovies: Already loading, skipping duplicate call")
                return@withContext
            }

            try {
                moviesMutex.withLock {
                    // Double-check inside mutex
                    val stateInsideMutex = _moviesState.value
                    if (stateInsideMutex is DataState.Loading && !forceRefresh) {
                        return@withLock
                    }

                    // Emit Loading state with indeterminate progress
                    _moviesState.value = DataState.Loading(progress = null)
                    android.util.Log.d(
                        "IPTV_DEBUG",
                        "ContentRepository.loadMovies: Emitted Loading state"
                    )

                    // Check cache first (unless forcing refresh)
                    if (!forceRefresh) {
                        val metadata = database.cacheMetadataDao().get("movies")
                        if (
                            metadata != null &&
                                metadata.itemCount > 0 &&
                                metadata.loadStatus == "SUCCESS"
                        ) {
                            val isExtremelyStale =
                                !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                            if (!isExtremelyStale) {
                                PerformanceLogger.log("loadMovies: Cache valid, skipping API fetch")
                                _moviesState.value = DataState.Success(Unit)
                                android.util.Log.d(
                                    "IPTV_DEBUG",
                                    "ContentRepository.loadMovies: Emitted Success (cache valid)",
                                )
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

                    // Check for partial cache - merge if present
                    val existingMetadata = database.cacheMetadataDao().get("movies")
                    val shouldMerge =
                        existingMetadata != null && existingMetadata.loadStatus == "PARTIAL"

                    if (shouldMerge) {
                        PerformanceLogger.log(
                            "loadMovies: Merging with partial cache (${existingMetadata?.itemCount} existing items)",
                        )
                        android.util.Log.d(
                            "IPTV_DEBUG",
                            "ContentRepository.loadMovies: Detected PARTIAL cache, will merge"
                        )
                    } else {
                        // Fresh load - clear old data
                        database.movieDao().deleteBySourceId(sourceId)
                    }

                    // Drop indices before batch inserts
                    com.cactuvi.app.data.db.OptimizedBulkInsert.beginMoviesInsert(
                        database.getSqliteDatabase()
                    )

                    // Create write channel with capacity 10 (~100MB max memory)
                    val writeChannel = Channel<List<MovieEntity>>(capacity = 10)
                    val accumulator = mutableListOf<MovieEntity>()
                    var totalParsed = 0
                    var parseException: Exception? = null

                    // Launch background writer coroutine
                    val writerJob =
                        async(Dispatchers.IO) {
                            var successCount = 0
                            var failedCount = 0
                            var lastProgressPercent = 0
                            val errors = mutableListOf<Exception>()

                            try {
                                for (batch in writeChannel) {
                                    try {
                                        // Write batch to database
                                        dbWriter.writeMovies(batch)
                                        successCount += batch.size

                                        // Emit progress every 10%
                                        if (totalParsed > 0) {
                                            val progressPercent = (successCount * 100) / totalParsed
                                            if (
                                                progressPercent >= lastProgressPercent + 10 &&
                                                    progressPercent <= 100
                                            ) {
                                                lastProgressPercent =
                                                    (progressPercent / 10) * 10 // Round to 10%
                                                _moviesState.value =
                                                    DataState.Loading(
                                                        progress = lastProgressPercent
                                                    )
                                                PerformanceLogger.log(
                                                    "loadMovies: Progress $lastProgressPercent% ($successCount/$totalParsed)",
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        PerformanceLogger.log(
                                            "loadMovies: Batch write failed - ${e.message}"
                                        )
                                        android.util.Log.e(
                                            "ContentRepository",
                                            "Batch write failed",
                                            e
                                        )
                                        failedCount += batch.size
                                        errors.add(e)
                                        // Continue processing remaining batches
                                    }
                                }
                            } catch (e: Exception) {
                                PerformanceLogger.log(
                                    "loadMovies: Writer coroutine failed - ${e.message}"
                                )
                                android.util.Log.e(
                                    "ContentRepository",
                                    "Writer coroutine failed",
                                    e
                                )
                                errors.add(e)
                            }

                            AsyncWriteResult(successCount, failedCount, errors)
                        }

                    // Parse JSON and enqueue batches (fast, non-blocking)
                    try {
                        totalParsed =
                            StreamingJsonParser.parseArrayInBatches(
                                responseBody = responseBody,
                                itemClass = Movie::class.java,
                                batchSize = BATCH_SIZE,
                                processBatch = { movieBatch ->
                                    val entities =
                                        movieBatch.map { movie ->
                                            val categoryName =
                                                categoryMap[movie.categoryId]?.categoryName ?: ""
                                            movie.categoryName = categoryName
                                            movie.toEntity(sourceId, categoryName)
                                        }
                                    accumulator.addAll(entities)

                                    if (accumulator.size >= 5000) {
                                        // Non-blocking send with backpressure
                                        runBlocking { writeChannel.send(accumulator.toList()) }
                                        accumulator.clear()
                                    }
                                },
                                onProgress = { count ->
                                    PerformanceLogger.log("Progress: $count movies parsed")
                                },
                            )

                        // Flush remaining
                        if (accumulator.isNotEmpty()) {
                            writeChannel.send(accumulator.toList())
                        }

                        PerformanceLogger.log("loadMovies: Parsing complete - $totalParsed items")
                        PerformanceLogger.log("loadMovies: HTTP connection closed")
                    } catch (e: Exception) {
                        parseException = e
                        PerformanceLogger.log("loadMovies: Parsing failed - ${e.message}")
                        android.util.Log.e("ContentRepository", "Parsing failed", e)
                    } finally {
                        writeChannel.close() // Signal writer to stop
                    }

                    // Wait for background writes to complete
                    val writeResult = writerJob.await()

                    // Rebuild indices
                    com.cactuvi.app.data.db.OptimizedBulkInsert.endMoviesInsert(
                        database.getSqliteDatabase()
                    )

                    // Verify actual database count matches write result
                    val actualDbCount = database.movieDao().getCount()
                    PerformanceLogger.log(
                        "loadMovies: Verification - writeResult: ${writeResult.successCount}, actualDB: $actualDbCount"
                    )
                    if (
                        actualDbCount == 0 ||
                            kotlin.math.abs(actualDbCount - writeResult.successCount) > 100
                    ) {
                        val errorMsg =
                            "Database verification failed: reported ${writeResult.successCount}, actual $actualDbCount"
                        android.util.Log.e("ContentRepository", "loadMovies: $errorMsg")
                        throw Exception(errorMsg)
                    }

                    // Handle results based on parse + write status
                    when {
                        // Parse failed - entire operation failed
                        parseException != null -> {
                            throw parseException
                        }

                        // No items written - complete failure
                        writeResult.successCount == 0 && writeResult.failedCount > 0 -> {
                            throw writeResult.errors.first()
                        }

                        // Partial success - some batches failed
                        writeResult.failedCount > 0 -> {
                            android.util.Log.w(
                                "ContentRepository",
                                "loadMovies: Partial success - ${writeResult.successCount} succeeded, ${writeResult.failedCount} failed",
                            )

                            // Mark cache as partial
                            database
                                .cacheMetadataDao()
                                .insert(
                                    CacheMetadataEntity(
                                        contentType = "movies",
                                        lastUpdated = System.currentTimeMillis(),
                                        itemCount = writeResult.successCount,
                                        categoryCount = categories.size,
                                        loadStatus = "PARTIAL",
                                    ),
                                )

                            // Emit PartialSuccess state (silent to user, logged)
                            _moviesState.value =
                                DataState.PartialSuccess(
                                    data = Unit,
                                    successCount = writeResult.successCount,
                                    failedCount = writeResult.failedCount,
                                    error = writeResult.errors.first(),
                                )

                            _moviesEffects.emit(
                                MoviesEffect.LoadPartialSuccess(
                                    successCount = writeResult.successCount,
                                    failedCount = writeResult.failedCount,
                                    message = writeResult.errors.first().message ?: "Unknown error",
                                ),
                            )
                        }

                        // Complete success
                        else -> {
                            database
                                .cacheMetadataDao()
                                .insert(
                                    CacheMetadataEntity(
                                        contentType = "movies",
                                        lastUpdated = System.currentTimeMillis(),
                                        itemCount = writeResult.successCount,
                                        categoryCount = categories.size,
                                        loadStatus = "SUCCESS",
                                    ),
                                )

                            _moviesState.value = DataState.Success(Unit)
                            _moviesEffects.emit(MoviesEffect.LoadSuccess(writeResult.successCount))

                            PerformanceLogger.log(
                                "loadMovies: Complete success - ${writeResult.successCount} items"
                            )
                            android.util.Log.d(
                                "IPTV_DEBUG",
                                "ContentRepository.loadMovies: Emitted Success (${writeResult.successCount} items)",
                            )

                            // Update category childrenCount after movies are loaded
                            try {
                                val categories = getMovieCategories().getOrNull() ?: emptyList()
                                if (categories.isNotEmpty()) {
                                    PerformanceLogger.log(
                                        "loadMovies: Updating category childrenCount"
                                    )
                                    computeChildrenCounts(ContentType.MOVIES, categories)
                                }
                            } catch (countError: Exception) {
                                android.util.Log.w(
                                    "ContentRepository",
                                    "Failed to update movie category counts: ${countError.message}"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ContentRepository", "loadMovies failed", e)

                // Check if we have cached data
                val cachedCount = database.cacheMetadataDao().get("movies")?.itemCount ?: 0
                val hasCache = cachedCount > 0

                // Emit Error state
                _moviesState.value =
                    DataState.Error(
                        error = e,
                        cachedData = if (hasCache) Unit else null,
                    )
                android.util.Log.d(
                    "IPTV_DEBUG",
                    "ContentRepository.loadMovies: Emitted Error state (hasCache=$hasCache)"
                )

                // Emit error effect
                _moviesEffects.emit(
                    MoviesEffect.LoadError(
                        message = e.message ?: "Unknown error",
                        hasCachedData = hasCache,
                    ),
                )
            }
        }

    @Deprecated("Use loadMovies() instead", ReplaceWith("loadMovies(forceRefresh)"))
    override suspend fun getMovies(forceRefresh: Boolean): Result<List<Movie>> =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(5.minutes.inWholeMilliseconds) {
                    moviesMutex.withLock {
                        val startTime = PerformanceLogger.start("Repository.getMovies")

                        try {
                            // Check cache metadata
                            PerformanceLogger.logPhase("getMovies", "Checking cache metadata")
                            val metadataCheckStart = PerformanceLogger.start("Metadata cache check")
                            val metadata = database.cacheMetadataDao().get("movies")
                            PerformanceLogger.end(
                                "Metadata cache check",
                                metadataCheckStart,
                                "exists=${metadata != null}, count=${metadata?.itemCount ?: 0}",
                            )

                            // Return cache immediately if exists and not forcing refresh
                            // Unless extremely stale (7+ days) - then force refresh for safety
                            if (!forceRefresh && metadata != null) {
                                val isExtremelyStale =
                                    !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                                if (!isExtremelyStale) {
                                    PerformanceLogger.logPhase("getMovies", "Loading cached data")
                                    val dataLoadStart =
                                        PerformanceLogger.start("Load cached movies")
                                    val cached = database.movieDao().getAll()
                                    PerformanceLogger.end(
                                        "Load cached movies",
                                        dataLoadStart,
                                        "count=${cached.size}"
                                    )

                                    if (cached.isNotEmpty()) {
                                        PerformanceLogger.logCacheHit(
                                            "movies",
                                            "getMovies",
                                            cached.size
                                        )
                                        PerformanceLogger.end(
                                            "Repository.getMovies",
                                            startTime,
                                            "HIT - count=${cached.size}",
                                        )
                                        return@withLock Result.success(cached.map { it.toModel() })
                                    }
                                }
                            }

                            // Cache miss - fetch from API
                            PerformanceLogger.logCacheMiss(
                                "movies",
                                "getMovies",
                                if (forceRefresh) "forceRefresh" else "expired/empty",
                            )
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

                            // Clear old data for this source before streaming new data to avoid
                            // conflicts
                            PerformanceLogger.logPhase("getMovies", "Clearing old data for source")
                            val clearStart = PerformanceLogger.start("Clear movies by sourceId")
                            database.movieDao().deleteBySourceId(sourceId)
                            PerformanceLogger.end("Clear movies by sourceId", clearStart)

                            // Optimized streaming parse + insert with index management
                            PerformanceLogger.logPhase(
                                "getMovies",
                                "Streaming parse + optimized DB insert"
                            )
                            val dbInsertStart = PerformanceLogger.start("Stream + optimized insert")

                            // Drop indices before batch inserts
                            com.cactuvi.app.data.db.OptimizedBulkInsert.beginMoviesInsert(
                                database.getSqliteDatabase()
                            )

                            // Accumulator for batching writes through DbWriter (5k chunks)
                            val accumulator = mutableListOf<MovieEntity>()
                            var totalInserted = 0

                            try {
                                totalInserted =
                                    StreamingJsonParser.parseArrayInBatches(
                                        responseBody = responseBody,
                                        itemClass = Movie::class.java,
                                        batchSize = BATCH_SIZE,
                                        processBatch = { movieBatch ->
                                            // Map to entities with category names
                                            val entities =
                                                movieBatch.map { movie ->
                                                    val categoryName =
                                                        categoryMap[movie.categoryId]?.categoryName
                                                            ?: ""
                                                    movie.categoryName = categoryName
                                                    movie.toEntity(sourceId, categoryName)
                                                }

                                            // Accumulate entities
                                            accumulator.addAll(entities)

                                            // Write in 5k chunks through DbWriter (serialized,
                                            // optimal transaction size)
                                            if (accumulator.size >= 5000) {
                                                dbWriter.writeMovies(accumulator.toList())
                                                accumulator.clear()
                                            }
                                        },
                                        onProgress = { count ->
                                            PerformanceLogger.log(
                                                "Progress: $count movies parsed and inserted"
                                            )
                                        },
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
                                com.cactuvi.app.data.db.OptimizedBulkInsert.endMoviesInsert(
                                    database.getSqliteDatabase()
                                )
                            }

                            PerformanceLogger.end(
                                "Stream + optimized insert",
                                dbInsertStart,
                                "inserted=$totalInserted"
                            )

                            PerformanceLogger.end(
                                "Stream + DB insert",
                                dbInsertStart,
                                "inserted=$totalInserted"
                            )

                            // Update cache metadata
                            PerformanceLogger.logPhase("getMovies", "Updating cache metadata")
                            val metadataUpdateStart = PerformanceLogger.start("Update metadata")
                            val newMetadata =
                                CacheMetadataEntity(
                                    contentType = "movies",
                                    lastUpdated = System.currentTimeMillis(),
                                    itemCount = totalInserted,
                                    categoryCount = categories.size,
                                )
                            database.cacheMetadataDao().insert(newMetadata)
                            PerformanceLogger.end("Update metadata", metadataUpdateStart)

                            PerformanceLogger.end(
                                "Repository.getMovies",
                                startTime,
                                "SUCCESS - count=$totalInserted"
                            )

                            // Return empty list since UI uses Paging to load data
                            // Returning the full list would defeat the purpose of streaming
                            Result.success(emptyList())
                        } catch (e: Exception) {
                            PerformanceLogger.log("getMovies API failed: ${e.message}")

                            // Fallback to cache
                            PerformanceLogger.logPhase(
                                "getMovies",
                                "API failed, trying cache fallback"
                            )
                            val cached = database.movieDao().getAll()
                            if (cached.isNotEmpty()) {
                                PerformanceLogger.logCacheHit(
                                    "movies",
                                    "getMovies_fallback",
                                    cached.size
                                )
                                PerformanceLogger.end(
                                    "Repository.getMovies",
                                    startTime,
                                    "FALLBACK - count=${cached.size}"
                                )
                                Result.success(cached.map { it.toModel() })
                            } else {
                                PerformanceLogger.end(
                                    "Repository.getMovies",
                                    startTime,
                                    "FAILED - no fallback"
                                )
                                Result.failure(e)
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                PerformanceLogger.log("getMovies timed out after 5 minutes")
                Result.failure(Exception("Movies data load timed out. Please try again.", e))
            }
        }

    override suspend fun getMovieCategories(forceRefresh: Boolean): Result<List<Category>> =
        withContext(Dispatchers.IO) {
            val startTime = PerformanceLogger.start("Repository.getMovieCategories")

            try {
                // Check cache
                PerformanceLogger.logPhase("getMovieCategories", "Checking cache")
                val cacheCheckStart = PerformanceLogger.start("Cache check")
                val cached = database.categoryDao().getAllByType("vod")
                PerformanceLogger.end("Cache check", cacheCheckStart, "cached=${cached.size}")

                if (
                    !forceRefresh &&
                        cached.isNotEmpty() &&
                        isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)
                ) {
                    PerformanceLogger.logCacheHit("movies", "categories", cached.size)
                    PerformanceLogger.end(
                        "Repository.getMovieCategories",
                        startTime,
                        "HIT - count=${cached.size}"
                    )
                    return@withContext Result.success(cached.map { it.toModel() })
                }

                // Cache miss - fetch from API
                PerformanceLogger.logCacheMiss(
                    "movies",
                    "categories",
                    if (forceRefresh) "forceRefresh" else "expired/empty",
                )
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

                // Compute children counts for navigation
                PerformanceLogger.logPhase("getMovieCategories", "Computing children counts")
                val countsStart = PerformanceLogger.start("Compute children counts")
                computeChildrenCounts(ContentType.MOVIES, categories)
                PerformanceLogger.end("Compute children counts", countsStart)

                PerformanceLogger.end(
                    "Repository.getMovieCategories",
                    startTime,
                    "SUCCESS - count=${categories.size}"
                )
                Result.success(categories)
            } catch (e: Exception) {
                PerformanceLogger.log("getMovieCategories API failed: ${e.message}")

                // Fallback to cache
                PerformanceLogger.logPhase(
                    "getMovieCategories",
                    "API failed, trying cache fallback"
                )
                val cached = database.categoryDao().getAllByType("vod")
                if (cached.isNotEmpty()) {
                    PerformanceLogger.logCacheHit("movies", "categories_fallback", cached.size)
                    PerformanceLogger.end(
                        "Repository.getMovieCategories",
                        startTime,
                        "FALLBACK - count=${cached.size}"
                    )
                    Result.success(cached.map { it.toModel() })
                } else {
                    PerformanceLogger.end(
                        "Repository.getMovieCategories",
                        startTime,
                        "FAILED - no fallback"
                    )
                    Result.failure(e)
                }
            }
        }

    override suspend fun getMovieInfo(vodId: Int): Result<MovieInfo> =
        withContext(Dispatchers.IO) {
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
     * Load series data from API with reactive state management. Emits state changes to seriesState
     * StateFlow:
     * - Loading: Data is being fetched
     * - Success: Data loaded and cached
     * - Error: Load failed (with optional cached data)
     *
     * UI observes seriesState reactively. This method NEVER returns Result. Thread-safe: Uses mutex
     * to prevent concurrent loads.
     *
     * @param forceRefresh If true, bypass cache and force API fetch
     */
    override suspend fun loadSeries(forceRefresh: Boolean) =
        withContext(Dispatchers.IO) {
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
                    _seriesState.value = DataState.Loading(progress = null)

                    // Check cache first (unless forcing refresh)
                    if (!forceRefresh) {
                        val metadata = database.cacheMetadataDao().get("series")
                        if (
                            metadata != null &&
                                metadata.itemCount > 0 &&
                                metadata.loadStatus == "SUCCESS"
                        ) {
                            val isExtremelyStale =
                                !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                            if (!isExtremelyStale) {
                                val duration = System.currentTimeMillis() - startTime
                                PerformanceLogger.log(
                                    "loadSeries: Cache valid, skipping API fetch (${duration}ms)"
                                )
                                _seriesState.value = DataState.Success(Unit)
                                _seriesEffects.emit(
                                    SeriesEffect.LoadSuccess(
                                        itemCount = metadata.itemCount,
                                        durationMs = duration,
                                        fromCache = true,
                                    ),
                                )
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
                    com.cactuvi.app.data.db.OptimizedBulkInsert.beginSeriesInsert(
                        database.getSqliteDatabase()
                    )

                    // Accumulator for batching writes through DbWriter
                    val accumulator = mutableListOf<SeriesEntity>()
                    var totalInserted = 0

                    try {
                        totalInserted =
                            StreamingJsonParser.parseArrayInBatches(
                                responseBody = responseBody,
                                itemClass = Series::class.java,
                                batchSize = BATCH_SIZE,
                                processBatch = { seriesBatch ->
                                    val entities =
                                        seriesBatch.map { s ->
                                            val categoryName =
                                                categoryMap[s.categoryId]?.categoryName ?: ""
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
                                    PerformanceLogger.log(
                                        "Progress: $count series parsed and inserted"
                                    )
                                },
                            )

                        // Flush remaining items
                        if (accumulator.isNotEmpty()) {
                            dbWriter.writeSeries(accumulator)
                            accumulator.clear()
                        }
                    } catch (e: Exception) {
                        throw e
                    } finally {
                        com.cactuvi.app.data.db.OptimizedBulkInsert.endSeriesInsert(
                            database.getSqliteDatabase()
                        )
                    }

                    // Verify database count
                    val dbCount = database.seriesDao().getCount()
                    PerformanceLogger.log(
                        "loadSeries: Verification - expected: $totalInserted, actualDB: $dbCount"
                    )
                    if (dbCount == 0 || kotlin.math.abs(dbCount - totalInserted) > 100) {
                        val errorMsg =
                            "Database verification failed: reported $totalInserted, actual $dbCount"
                        android.util.Log.e("ContentRepository", "loadSeries: $errorMsg")
                        throw Exception(errorMsg)
                    }

                    // Update cache metadata
                    val newMetadata =
                        CacheMetadataEntity(
                            contentType = "series",
                            lastUpdated = System.currentTimeMillis(),
                            itemCount = totalInserted,
                            categoryCount = categories.size,
                            loadStatus = "SUCCESS",
                        )
                    database.cacheMetadataDao().insert(newMetadata)

                    PerformanceLogger.log("loadSeries: Successfully inserted $totalInserted series")

                    val duration = System.currentTimeMillis() - startTime
                    PerformanceLogger.log("loadSeries: API fetch completed (${duration}ms)")

                    // Emit Success state
                    _seriesState.value = DataState.Success(Unit)

                    // Emit success effect with performance metrics
                    _seriesEffects.emit(
                        SeriesEffect.LoadSuccess(
                            itemCount = totalInserted,
                            durationMs = duration,
                            fromCache = false,
                        ),
                    )

                    // Update category childrenCount after series are loaded
                    try {
                        val seriesCategories = getSeriesCategories().getOrNull() ?: emptyList()
                        if (seriesCategories.isNotEmpty()) {
                            PerformanceLogger.log("loadSeries: Updating category childrenCount")
                            computeChildrenCounts(ContentType.SERIES, seriesCategories)
                        }
                    } catch (countError: Exception) {
                        android.util.Log.w(
                            "ContentRepository",
                            "Failed to update series category counts: ${countError.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                android.util.Log.e("ContentRepository", "loadSeries failed after ${duration}ms", e)

                // Check if we have cached data
                val cachedCount = database.cacheMetadataDao().get("series")?.itemCount ?: 0
                val hasCache = cachedCount > 0

                // Emit Error state
                _seriesState.value =
                    DataState.Error(
                        error = e,
                        cachedData = if (hasCache) Unit else null,
                    )

                // Emit error effect with performance metrics
                _seriesEffects.emit(
                    SeriesEffect.LoadError(
                        message = e.message ?: "Unknown error",
                        hasCachedData = hasCache,
                        durationMs = duration,
                    ),
                )
            }
        }

    @Deprecated("Use loadSeries() instead", ReplaceWith("loadSeries(forceRefresh)"))
    override suspend fun getSeries(forceRefresh: Boolean): Result<List<Series>> =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(5.minutes.inWholeMilliseconds) {
                    seriesMutex.withLock {
                        try {
                            // Check cache metadata
                            val metadata = database.cacheMetadataDao().get("series")

                            // Return cache immediately if exists and not forcing refresh
                            // Unless extremely stale (7+ days) - then force refresh for safety
                            if (!forceRefresh && metadata != null) {
                                val isExtremelyStale =
                                    !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                                if (!isExtremelyStale) {
                                    val cached = database.seriesDao().getAll()
                                    if (cached.isNotEmpty()) {
                                        return@withLock Result.success(cached.map { it.toModel() })
                                    }
                                }
                            }

                            // Check VPN requirement before API call
                            checkVpnRequirement()

                            val (username, password, sourceId) = getCredentials()

                            // Fetch from API with retry
                            val responseBody = retryWithExponentialBackoff {
                                getApiService().getSeries(username, password)
                            }

                            // DEBUG: Load and validate categories
                            val categoriesResult = getSeriesCategories()
                            PerformanceLogger.log(
                                "[DEBUG getSeries] Categories result: success=${categoriesResult.isSuccess}",
                            )
                            val categories = categoriesResult.getOrNull() ?: emptyList()
                            PerformanceLogger.log(
                                "[DEBUG getSeries] Categories loaded: count=${categories.size}"
                            )
                            if (categories.size > 0) {
                                PerformanceLogger.log(
                                    "[DEBUG getSeries] Sample categories: ${categories.take(
                                    3,
                                ).map { "${it.categoryId}:${it.categoryName}" }}",
                                )
                            }

                            val categoryMap = categories.associateBy { it.categoryId }
                            PerformanceLogger.log(
                                "[DEBUG getSeries] CategoryMap size: ${categoryMap.size}"
                            )

                            // Clear old data for this source before streaming
                            database.seriesDao().deleteBySourceId(sourceId)

                            // Drop indices before batch inserts
                            com.cactuvi.app.data.db.OptimizedBulkInsert.beginSeriesInsert(
                                database.getSqliteDatabase()
                            )

                            // Accumulator for batching writes through DbWriter (5k chunks)
                            val accumulator = mutableListOf<SeriesEntity>()
                            var totalInserted = 0

                            try {
                                totalInserted =
                                    StreamingJsonParser.parseArrayInBatches(
                                        responseBody = responseBody,
                                        itemClass = Series::class.java,
                                        batchSize = BATCH_SIZE,
                                        processBatch = { seriesBatch ->
                                            // DEBUG: Log first batch to check categoryId mapping
                                            if (totalInserted == 0 && seriesBatch.isNotEmpty()) {
                                                val sample = seriesBatch.first()
                                                val categoryName =
                                                    categoryMap[sample.categoryId]?.categoryName
                                                        ?: ""
                                                PerformanceLogger.log(
                                                    "[DEBUG getSeries] First series: id=${sample.seriesId}, name=${sample.name}, categoryId=${sample.categoryId}, mapped='$categoryName'"
                                                )
                                            }

                                            val entities =
                                                seriesBatch.map { s ->
                                                    val categoryName =
                                                        categoryMap[s.categoryId]?.categoryName
                                                            ?: ""
                                                    // DEBUG: Log unmapped categories
                                                    if (categoryName.isEmpty()) {
                                                        PerformanceLogger.log(
                                                            "[DEBUG getSeries] UNMAPPED categoryId=${s.categoryId} for series=${s.name}"
                                                        )
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
                                            PerformanceLogger.log(
                                                "Progress: $count series parsed and inserted"
                                            )
                                        },
                                    )

                                // Flush remaining items
                                if (accumulator.isNotEmpty()) {
                                    dbWriter.writeSeries(accumulator)
                                    accumulator.clear()
                                }
                            } catch (e: Exception) {
                                throw e
                            } finally {
                                com.cactuvi.app.data.db.OptimizedBulkInsert.endSeriesInsert(
                                    database.getSqliteDatabase()
                                )
                            }

                            // Update cache metadata
                            val newMetadata =
                                CacheMetadataEntity(
                                    contentType = "series",
                                    lastUpdated = System.currentTimeMillis(),
                                    itemCount = totalInserted,
                                    categoryCount = categories.size,
                                )
                            database.cacheMetadataDao().insert(newMetadata)

                            // DEBUG: Verify data in database
                            PerformanceLogger.log(
                                "[DEBUG getSeries] Total inserted: $totalInserted"
                            )
                            val dbCount = database.seriesDao().getCount()
                            PerformanceLogger.log(
                                "[DEBUG getSeries] DB count verification: $dbCount"
                            )

                            // DEBUG: Check sample category counts
                            val sampleCategories = categories.take(3)
                            sampleCategories.forEach { cat ->
                                val count = database.seriesDao().getCountByCategory(cat.categoryId)
                                PerformanceLogger.log(
                                    "[DEBUG getSeries] Category ${cat.categoryName} (${cat.categoryId}): $count items in DB",
                                )
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
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                PerformanceLogger.log("getSeries timed out after 5 minutes")
                Result.failure(Exception("Series data load timed out. Please try again.", e))
            }
        }

    override suspend fun getSeriesCategories(forceRefresh: Boolean): Result<List<Category>> =
        withContext(Dispatchers.IO) {
            try {
                val cached = database.categoryDao().getAllByType("series")
                PerformanceLogger.log("[DEBUG getSeriesCategories] Cached count: ${cached.size}")

                if (
                    !forceRefresh &&
                        cached.isNotEmpty() &&
                        isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)
                ) {
                    PerformanceLogger.log(
                        "[DEBUG getSeriesCategories] Returning cached categories: ${cached.size}"
                    )
                    if (cached.size > 0) {
                        PerformanceLogger.log(
                            "[DEBUG getSeriesCategories] Sample cached: ${cached.take(
                                3,
                            ).map { "${it.categoryId}:${it.categoryName}" }}",
                        )
                    }
                    return@withContext Result.success(cached.map { it.toModel() })
                }

                // Check VPN requirement before API call
                checkVpnRequirement()

                val (username, password, sourceId) = getCredentials()
                val categories = getApiService().getSeriesCategories(username, password)
                PerformanceLogger.log(
                    "[DEBUG getSeriesCategories] Fetched from API: ${categories.size}"
                )
                if (categories.size > 0) {
                    PerformanceLogger.log(
                        "[DEBUG getSeriesCategories] Sample API: ${categories.take(
                            3,
                        ).map { "${it.categoryId}:${it.categoryName}" }}",
                    )
                }

                val entities = categories.map { it.toEntity(sourceId, "series") }
                database.categoryDao().insertAll(entities)
                PerformanceLogger.log(
                    "[DEBUG getSeriesCategories] Inserted ${entities.size} categories into DB"
                )

                // Compute children counts for navigation
                computeChildrenCounts(ContentType.SERIES, categories)

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

    override suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfo> =
        withContext(Dispatchers.IO) {
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

    override suspend fun addToFavorites(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        rating: String?,
        categoryName: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val favorite =
                    FavoriteEntity(
                        sourceId = sourceId,
                        id = "${contentType}_$contentId",
                        contentId = contentId,
                        contentType = contentType,
                        contentName = contentName,
                        posterUrl = posterUrl,
                        rating = rating,
                        categoryName = categoryName,
                    )
                database.favoriteDao().insert(favorite)

                // Update entity favorite status
                when (contentType) {
                    "movie" -> database.movieDao().updateFavorite(contentId.toInt(), true)
                    "series" -> database.seriesDao().updateFavorite(contentId.toInt(), true)
                    "live_channel" ->
                        database.liveChannelDao().updateFavorite(contentId.toInt(), true)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun removeFromFavorites(contentId: String, contentType: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val id = "${contentType}_$contentId"
                database.favoriteDao().deleteById(sourceId, id)

                // Update entity favorite status
                when (contentType) {
                    "movie" -> database.movieDao().updateFavorite(contentId.toInt(), false)
                    "series" -> database.seriesDao().updateFavorite(contentId.toInt(), false)
                    "live_channel" ->
                        database.liveChannelDao().updateFavorite(contentId.toInt(), false)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getFavorites(contentType: String?): Result<List<FavoriteEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val favorites =
                    if (contentType != null) {
                        database.favoriteDao().getByType(sourceId, contentType)
                    } else {
                        database.favoriteDao().getAll(sourceId)
                    }
                Result.success(favorites)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun isFavorite(contentId: String, contentType: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val id = "${contentType}_$contentId"
                Result.success(database.favoriteDao().isFavorite(sourceId, id))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ========== WATCH HISTORY ==========

    override suspend fun updateWatchProgress(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        resumePosition: Long,
        duration: Long,
        seriesId: Int?,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val isCompleted = (resumePosition.toDouble() / duration) > 0.9

                val history =
                    WatchHistoryEntity(
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
                        episodeNumber = episodeNumber,
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

    override suspend fun getWatchHistory(limit: Int): Result<List<WatchHistoryEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val history = database.watchHistoryDao().getIncomplete(sourceId, limit)
                Result.success(history)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun clearWatchHistory(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                database.watchHistoryDao().clearAll()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteWatchHistoryItem(contentId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val sourceId = getActiveSourceId()
                val item =
                    database.watchHistoryDao().getByContentId(sourceId, contentId, "movie")
                        ?: database.watchHistoryDao().getByContentId(sourceId, contentId, "series")
                        ?: database
                            .watchHistoryDao()
                            .getByContentId(sourceId, contentId, "live_channel")

                item?.let { database.watchHistoryDao().delete(it) }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ========== PAGING METHODS ==========

    override fun getLiveStreamsPaged(categoryId: String?): Flow<PagingData<LiveChannel>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.liveChannelDao().getByCategoryIdPaged(categoryId)
            } else {
                database.liveChannelDao().getAllPaged()
            }
        }

        return Pager(
                config =
                    PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        prefetchDistance = 10,
                    ),
                pagingSourceFactory = pagingSourceFactory,
            )
            .flow
            .flowMap { pagingData -> pagingData.map { entity -> entity.toModel() } }
    }

    override fun getMoviesPaged(categoryId: String?): Flow<PagingData<Movie>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.movieDao().getByCategoryIdPaged(categoryId)
            } else {
                database.movieDao().getAllPaged()
            }
        }

        return Pager(
                config =
                    PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        prefetchDistance = 10,
                    ),
                pagingSourceFactory = pagingSourceFactory,
            )
            .flow
            .flowMap { pagingData -> pagingData.map { entity -> entity.toModel() } }
    }

    override fun getSeriesPaged(categoryId: String?): Flow<PagingData<Series>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.seriesDao().getByCategoryIdPaged(categoryId)
            } else {
                database.seriesDao().getAllPaged()
            }
        }

        return Pager(
                config =
                    PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        prefetchDistance = 10,
                    ),
                pagingSourceFactory = pagingSourceFactory,
            )
            .flow
            .flowMap { pagingData -> pagingData.map { entity -> entity.toModel() } }
    }

    // ========== DYNAMIC CATEGORY NAVIGATION ==========

    /**
     * Get top-level navigation (with grouping if enabled). Returns either grouped categories or
     * flat list based on settings.
     */
    override suspend fun getTopLevelNavigation(
        contentType: ContentType,
        groupingEnabled: Boolean,
        separator: String
    ): NavigationResult =
        withContext(Dispatchers.IO) {
            val categories =
                database
                    .categoryDao()
                    .getTopLevel(contentType.value)
                    .map { it.toModel() }
                    .filter { it.childrenCount > 0 } // Hide empty categories

            if (groupingEnabled && separator != "NONE") {
                // Group categories by separator
                val grouped = groupCategories(categories, separator)
                NavigationResult.Groups(grouped)
            } else {
                // Return flat list
                NavigationResult.Categories(categories)
            }
        }

    /** Get children of a specific category. Automatically skips single-child levels. */
    override suspend fun getChildCategories(
        contentType: ContentType,
        parentCategoryId: String
    ): NavigationResult =
        withContext(Dispatchers.IO) {
            val parentId =
                parentCategoryId.toIntOrNull()
                    ?: return@withContext NavigationResult.Categories(emptyList())

            var children =
                database
                    .categoryDao()
                    .getChildren(contentType.value, parentId)
                    .map { it.toModel() }
                    .filter { it.childrenCount > 0 } // Hide empty categories

            // Auto-skip single-child levels
            while (children.size == 1 && !children.first().isLeaf) {
                val singleChild = children.first()
                val nextParentId = singleChild.categoryId.toIntOrNull() ?: break
                children =
                    database
                        .categoryDao()
                        .getChildren(contentType.value, nextParentId)
                        .map { it.toModel() }
                        .filter { it.childrenCount > 0 }
            }

            NavigationResult.Categories(children)
        }

    /** Get category by ID. */
    override suspend fun getCategoryById(contentType: ContentType, categoryId: String): Category? =
        withContext(Dispatchers.IO) {
            database.categoryDao().getById(contentType.value, categoryId)?.toModel()
        }

    /** Get count of content items in a leaf category. */
    override suspend fun getContentItemCount(contentType: ContentType, categoryId: String): Int =
        withContext(Dispatchers.IO) {
            when (contentType) {
                ContentType.MOVIES -> database.movieDao().countByCategoryId(categoryId)
                ContentType.SERIES -> database.seriesDao().countByCategoryId(categoryId)
                ContentType.LIVE -> database.liveChannelDao().countByCategoryId(categoryId)
            }
        }

    /** Group categories by separator (helper for getTopLevelNavigation). */
    private fun groupCategories(
        categories: List<Category>,
        separator: String
    ): Map<String, List<Category>> {
        return categories.groupBy { category ->
            when (separator) {
                "FIRST_WORD" -> category.categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
                "|",
                "-",
                "/" -> {
                    val parts = category.categoryName.split(separator, limit = 2)
                    if (parts.size > 1) parts[0].trim() else "Other"
                }
                else -> "Other"
            }
        }
    }

    /**
     * Compute and cache children counts during initial load. Should be called after inserting
     * categories from API.
     */
    private suspend fun computeChildrenCounts(
        contentType: ContentType,
        categories: List<Category>
    ) =
        withContext(Dispatchers.IO) {
            // Build parent→children map
            val childrenMap: Map<Int, List<Category>> = categories.groupBy { it.parentId }

            // Find leaf categories (no children in category table)
            val leafCategoryIds =
                categories
                    .filter { category ->
                        childrenMap[category.categoryId.toIntOrNull() ?: -1].isNullOrEmpty()
                    }
                    .map { it.categoryId }

            // Mark leaf categories
            if (leafCategoryIds.isNotEmpty()) {
                database
                    .categoryDao()
                    .updateIsLeaf(contentType.value, leafCategoryIds, isLeaf = true)
            }

            // Update children counts for all categories
            categories.forEach { category ->
                val categoryIdInt = category.categoryId.toIntOrNull() ?: return@forEach

                // For leaf categories, count actual content items
                val count =
                    if (category.categoryId in leafCategoryIds) {
                        when (contentType) {
                            ContentType.MOVIES ->
                                database.movieDao().countByCategoryId(category.categoryId)
                            ContentType.SERIES ->
                                database.seriesDao().countByCategoryId(category.categoryId)
                            ContentType.LIVE ->
                                database.liveChannelDao().countByCategoryId(category.categoryId)
                        }
                    } else {
                        // For non-leaf, count direct children categories
                        childrenMap[categoryIdInt]?.size ?: 0
                    }

                database
                    .categoryDao()
                    .updateChildrenCount(contentType.value, category.categoryId, count)
            }
        }

    // ========== COMPATIBILITY LAYER (for old ViewModels using CategoryGrouper.NavigationTree)
    // ==========

    /**
     * Get cached navigation tree for movies (compatibility method for old ViewModels). Converts new
     * dynamic navigation to old CategoryGrouper format.
     */
    override suspend fun getCachedVodNavigationTree(): CategoryGrouper.NavigationTree? =
        withContext(Dispatchers.IO) {
            try {

                val groupingEnabled = preferencesManager.isMoviesGroupingEnabled()
                val separator = preferencesManager.getMoviesGroupingSeparator()

                when (
                    val result =
                        getTopLevelNavigation(ContentType.MOVIES, groupingEnabled, separator)
                ) {
                    is NavigationResult.Groups -> {
                        // Strip group prefixes from category names
                        val groups =
                            result.groups.map { (groupName, categories) ->
                                val strippedCategories =
                                    categories.map { category ->
                                        category.copy(
                                            categoryName =
                                                CategoryTreeBuilder.stripGroupPrefix(
                                                    category.categoryName,
                                                    separator
                                                )
                                        )
                                    }
                                CategoryGrouper.GroupNode(groupName, strippedCategories)
                            }
                        CategoryGrouper.NavigationTree(groups)
                    }
                    is NavigationResult.Categories -> {
                        // Create a single "All" group
                        val group = CategoryGrouper.GroupNode("All", result.categories)
                        CategoryGrouper.NavigationTree(listOf(group))
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    /** Get cached navigation tree for series (compatibility method for old ViewModels). */
    override suspend fun getCachedSeriesNavigationTree(): CategoryGrouper.NavigationTree? =
        withContext(Dispatchers.IO) {
            try {

                val groupingEnabled = preferencesManager.isSeriesGroupingEnabled()
                val separator = preferencesManager.getSeriesGroupingSeparator()

                when (
                    val result =
                        getTopLevelNavigation(ContentType.SERIES, groupingEnabled, separator)
                ) {
                    is NavigationResult.Groups -> {
                        val groups =
                            result.groups.map { (groupName, categories) ->
                                val strippedCategories =
                                    categories.map { category ->
                                        category.copy(
                                            categoryName =
                                                CategoryTreeBuilder.stripGroupPrefix(
                                                    category.categoryName,
                                                    separator
                                                )
                                        )
                                    }
                                CategoryGrouper.GroupNode(groupName, strippedCategories)
                            }
                        CategoryGrouper.NavigationTree(groups)
                    }
                    is NavigationResult.Categories -> {
                        val group = CategoryGrouper.GroupNode("All", result.categories)
                        CategoryGrouper.NavigationTree(listOf(group))
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    /** Get cached navigation tree for live TV (compatibility method for old ViewModels). */
    override suspend fun getCachedLiveNavigationTree(): CategoryGrouper.NavigationTree? =
        withContext(Dispatchers.IO) {
            try {

                val groupingEnabled = preferencesManager.isLiveGroupingEnabled()
                val separator = preferencesManager.getLiveGroupingSeparator()

                when (
                    val result = getTopLevelNavigation(ContentType.LIVE, groupingEnabled, separator)
                ) {
                    is NavigationResult.Groups -> {
                        val groups =
                            result.groups.map { (groupName, categories) ->
                                val strippedCategories =
                                    categories.map { category ->
                                        category.copy(
                                            categoryName =
                                                CategoryTreeBuilder.stripGroupPrefix(
                                                    category.categoryName,
                                                    separator
                                                )
                                        )
                                    }
                                CategoryGrouper.GroupNode(groupName, strippedCategories)
                            }
                        CategoryGrouper.NavigationTree(groups)
                    }
                    is NavigationResult.Categories -> {
                        val group = CategoryGrouper.GroupNode("All", result.categories)
                        CategoryGrouper.NavigationTree(listOf(group))
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    // ========== CACHE MANAGEMENT ==========

    override suspend fun clearAllCache(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                database.liveChannelDao().clearAll()
                database.movieDao().clearAll()
                database.seriesDao().clearAll()
                database.categoryDao().clearAll()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Clear all cached data for a specific source */
    override suspend fun clearSourceCache(sourceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Clear all content for this source using sourceId-specific deletes
                database.liveChannelDao().deleteBySourceId(sourceId)
                database.movieDao().deleteBySourceId(sourceId)
                database.seriesDao().deleteBySourceId(sourceId)
                database.categoryDao().deleteBySourceId(sourceId)
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
