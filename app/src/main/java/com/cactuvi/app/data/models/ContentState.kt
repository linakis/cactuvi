package com.cactuvi.app.data.models

/**
 * Unified state combining sync operation state (DataState) with database state (NavigationResult).
 *
 * This sealed class eliminates the race condition where UI observes database queries that return
 * empty results during initial sync, causing the UI to skip syncing states and jump directly to
 * showing content.
 *
 * The Repository layer combines two independent streams:
 * 1. DataState (API sync state: Idle/Fetching/Parsing/Success/Error)
 * 2. NavigationResult (DB state: Empty/HasGroups/HasCategories)
 *
 * And derives a single ContentState based on truth table logic.
 *
 * Generic over data payload type for reusability across:
 * - Navigation (ContentState<NavigationResult>)
 * - Content items (ContentState<PagingData<T>>)
 * - Search results, favorites, etc.
 *
 * @param T The data payload type (use Nothing for states without data)
 */
sealed class ContentState<out T> {

    /**
     * Initial state - no sync has started, no data available. Typically shown on first app launch
     * before any API calls.
     *
     * UI: Could show empty state or trigger initial sync.
     */
    object Initial : ContentState<Nothing>()

    /**
     * First-time sync in progress (blocking - no cached data exists). User must wait for sync to
     * complete before using the app.
     *
     * UI: Show full-screen sync progress with phase and percentage.
     *
     * @param phase Current sync phase (Fetching/Parsing/Persisting/Indexing)
     * @param progress Overall progress percentage (0-100), null if indeterminate
     */
    data class SyncingFirstTime(val phase: SyncPhase, val progress: Int?) : ContentState<Nothing>()

    /**
     * Data available and ready to display. May have background sync in progress (non-blocking,
     * silent).
     *
     * UI: Show content. Optionally show small background sync indicator.
     *
     * @param data The payload (NavigationResult, PagingData, etc.)
     * @param backgroundSync Null if no sync, or BackgroundSyncState if syncing in background
     */
    data class Ready<T>(val data: T, val backgroundSync: BackgroundSyncState? = null) :
        ContentState<T>()

    /**
     * Sync error occurred with no cached data available. Fatal error - user cannot proceed without
     * retrying.
     *
     * UI: Show error screen with retry button.
     *
     * @param error The error that occurred
     * @param phase The sync phase where the error occurred
     */
    data class Error(val error: Throwable, val phase: SyncPhase) : ContentState<Nothing>()

    /**
     * Sync error occurred but cached data is available. Non-fatal - user can continue with stale
     * data.
     *
     * UI: Show cached data. Optionally show toast/snackbar with error.
     *
     * @param data The cached data payload
     * @param error The error that occurred
     * @param phase The sync phase where the error occurred
     */
    data class ErrorWithCache<T>(val data: T, val error: Throwable, val phase: SyncPhase) :
        ContentState<T>()
}

/**
 * Background sync state when Ready state has sync in progress. Used to show non-blocking sync
 * indicators (e.g., small spinner in toolbar).
 *
 * @param phase Current sync phase
 * @param progress Overall progress percentage (0-100), null if indeterminate
 */
data class BackgroundSyncState(val phase: SyncPhase, val progress: Int?)

// SyncPhase enum is defined in DataState.kt and reused here
