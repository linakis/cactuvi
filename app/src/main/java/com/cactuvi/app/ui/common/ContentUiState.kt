package com.cactuvi.app.ui.common

import com.cactuvi.app.data.models.Category

/** Breadcrumb item for navigation path display. */
data class BreadcrumbItem(
    val categoryId: String?, // null for group names
    val displayName: String,
    val isGroup: Boolean = false,
)

/**
 * Sealed class representing the UI state for content navigation screens.
 *
 * Uses algebraic data types (ADT) to ensure exhaustive handling of all states. Each state variant
 * contains exactly the data needed for that state.
 *
 * State transitions:
 * - Initial → Syncing (when no cache and sync starts)
 * - Initial → Loading (when navigating)
 * - Syncing → Content (when sync completes and data available)
 * - Loading → Content (when navigation completes)
 * - Any → Error (when errors occur)
 */
sealed class ContentUiState {

    /** Initial state before any data is loaded. */
    data object Initial : ContentUiState()

    /**
     * Syncing state - initial data sync in progress, no cached data available. Shows progress
     * indicator to user.
     *
     * @param progress Sync progress percentage (0-100), null for indeterminate
     */
    data class Syncing(val progress: Int?) : ContentUiState()

    /** Loading state - navigation or refresh in progress. */
    data object Loading : ContentUiState()

    /**
     * Error state - an error occurred.
     *
     * @param message Error message to display
     */
    data class Error(val message: String) : ContentUiState()

    /**
     * Content state - data is available to display. Uses sealed subclasses to represent what type
     * of content is being shown.
     */
    sealed class Content : ContentUiState() {
        abstract val breadcrumbPath: List<BreadcrumbItem>

        /**
         * Showing groups at root level.
         *
         * @param groups Map of group name to categories in that group
         * @param breadcrumbPath Current navigation path (empty at root)
         */
        data class Groups(
            val groups: Map<String, List<Category>>,
            override val breadcrumbPath: List<BreadcrumbItem> = emptyList(),
        ) : Content()

        /**
         * Showing categories list.
         *
         * @param categories List of categories to display
         * @param breadcrumbPath Current navigation path
         */
        data class Categories(
            val categories: List<Category>,
            override val breadcrumbPath: List<BreadcrumbItem>,
        ) : Content()

        /**
         * Showing content items (movies, series, channels).
         *
         * @param categoryId The category ID for content paging
         * @param breadcrumbPath Current navigation path
         */
        data class Items(
            val categoryId: String,
            override val breadcrumbPath: List<BreadcrumbItem>,
        ) : Content()
    }
}
