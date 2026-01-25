package com.cactuvi.app.data.sync

import android.content.Context
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.utils.CategoryGrouper.NavigationTree
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.SyncPreferencesManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Coordinates background sync with parallel API execution.
 * Implements stale-while-revalidate pattern:
 * 1. Capture old navigation tree state (fast, parallel)
 * 2. Fetch from APIs (PARALLEL - Movies/Series/Live simultaneously)
 * 3. Capture new navigation tree state (fast, parallel)
 * 4. Diff detection (parallel)
 * 5. Emit diff events to ReactiveUpdateManager
 * 6. Update sync metadata
 * 
 * Handles partial failures (Option A): If Movies succeeds but Series fails,
 * still apply Movies diffs and update status accordingly.
 */
class SyncCoordinator(context: Context) {
    
    private val repository = ContentRepository.getInstance(context)
    private val database = AppDatabase.getInstance(context)
    private val syncPrefs = SyncPreferencesManager.getInstance(context)
    private val reactiveUpdateManager = ReactiveUpdateManager.getInstance()
    
    /**
     * Sync all content types in parallel and generate diff events.
     * Target: 6-8 seconds total sync time (not 13-15s sequential).
     */
    suspend fun syncAll(): SyncResult = coroutineScope {
        try {
            // ========== PHASE 1: Capture old state (parallel) ==========
            val (oldMoviesTree, oldSeriesTree, oldLiveTree) = coroutineScope {
                Triple(
                    async { repository.getCachedVodNavigationTree() },
                    async { repository.getCachedSeriesNavigationTree() },
                    async { repository.getCachedLiveNavigationTree() }
                )
            }.let { (m, s, l) ->
                Triple(m.await(), s.await(), l.await())
            }
            
            // ========== PHASE 2: API FETCH (PARALLEL - CRITICAL!) ==========
            // Launch all three load operations in parallel and await completion
            coroutineScope {
                listOf(
                    async { repository.loadMovies(forceRefresh = true) },
                    async { repository.loadSeries(forceRefresh = true) },
                    async { repository.loadLive(forceRefresh = true) }
                ).map { it.await() }
            }
            
            // Check success by examining final states
            val moviesSuccess = repository.moviesState.value.isSuccess()
            val seriesSuccess = repository.seriesState.value.isSuccess()
            val liveSuccess = repository.liveState.value.isSuccess()
            
            // ========== PHASE 3: Capture new state (parallel) ==========
            val (newMoviesTree, newSeriesTree, newLiveTree) = coroutineScope {
                Triple(
                    async { repository.getCachedVodNavigationTree() },
                    async { repository.getCachedSeriesNavigationTree() },
                    async { repository.getCachedLiveNavigationTree() }
                )
            }.let { (m, s, l) ->
                Triple(m.await(), s.await(), l.await())
            }
            
            // ========== PHASE 4: Diff detection (parallel) ==========
            val allDiffs = mutableListOf<ContentDiff>()
            
            val diffs = coroutineScope {
                listOf(
                    async {
                        if (moviesSuccess && oldMoviesTree != null && newMoviesTree != null) {
                            ContentDiffEngine.diffNavigationTree("movies", oldMoviesTree, newMoviesTree)
                        } else emptyList()
                    },
                    async {
                        if (seriesSuccess && oldSeriesTree != null && newSeriesTree != null) {
                            ContentDiffEngine.diffNavigationTree("series", oldSeriesTree, newSeriesTree)
                        } else emptyList()
                    },
                    async {
                        if (liveSuccess && oldLiveTree != null && newLiveTree != null) {
                            ContentDiffEngine.diffNavigationTree("live", oldLiveTree, newLiveTree)
                        } else emptyList()
                    }
                ).map { it.await() }.flatten()
            }
            
            allDiffs.addAll(diffs)
            
            // ========== PHASE 5: Emit diff events ==========
            if (allDiffs.isNotEmpty()) {
                reactiveUpdateManager.emitDiffs(allDiffs)
            }
            
            // ========== PHASE 6: Update sync metadata ==========
            val now = System.currentTimeMillis()
            
            // Update timestamps for successful syncs
            if (moviesSuccess) {
                syncPrefs.updateLastSync("movies", now)
                updateSyncStatus("movies", "SUCCESS", null)
            } else {
                val errorMsg = (repository.moviesState.value as? com.cactuvi.app.data.models.DataState.Error)?.error?.message
                updateSyncStatus("movies", "FAILED", errorMsg)
            }
            
            if (seriesSuccess) {
                syncPrefs.updateLastSync("series", now)
                updateSyncStatus("series", "SUCCESS", null)
            } else {
                val errorMsg = (repository.seriesState.value as? com.cactuvi.app.data.models.DataState.Error)?.error?.message
                updateSyncStatus("series", "FAILED", errorMsg)
            }
            
            if (liveSuccess) {
                syncPrefs.updateLastSync("live", now)
                updateSyncStatus("live", "SUCCESS", null)
            } else {
                val errorMsg = (repository.liveState.value as? com.cactuvi.app.data.models.DataState.Error)?.error?.message
                updateSyncStatus("live", "FAILED", errorMsg)
            }
            
            // Return success if at least one content type succeeded (partial failure handling)
            if (moviesSuccess || seriesSuccess || liveSuccess) {
                SyncResult.Success(
                    totalDiffs = allDiffs.size,
                    moviesSuccess = moviesSuccess,
                    seriesSuccess = seriesSuccess,
                    liveSuccess = liveSuccess
                )
            } else {
                SyncResult.Failure(Exception("All content types failed to sync"))
            }
            
        } catch (e: Exception) {
            SyncResult.Failure(e)
        }
    }
    
    /**
     * Update sync status in cache metadata
     */
    private suspend fun updateSyncStatus(contentType: String, status: String, error: String?) {
        val metadata = database.cacheMetadataDao().get(contentType)
        if (metadata != null) {
            val updated = metadata.copy(
                lastSyncAttempt = System.currentTimeMillis(),
                lastSyncSuccess = if (status == "SUCCESS") System.currentTimeMillis() else metadata.lastSyncSuccess,
                syncStatus = status,
                lastSyncError = error
            )
            database.cacheMetadataDao().insert(updated)
        }
    }
}
