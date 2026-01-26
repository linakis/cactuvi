package com.cactuvi.app.ui.common

import com.cactuvi.app.data.models.Category

/** Breadcrumb item for navigation path display. */
data class BreadcrumbItem(
    val categoryId: String?, // null for group names
    val displayName: String,
    val isGroup: Boolean = false,
)

/**
 * Unified UI state for dynamic level-by-level content navigation.
 *
 * Navigation is computed on-the-fly from category hierarchy:
 * - Root level: Groups (if grouping enabled) or top-level categories
 * - Child levels: Subcategories (arbitrary depth supported)
 * - Leaf level: Content items (movies/series/channels)
 *
 * Auto-skip behavior:
 * - Single-child levels are automatically skipped
 * - Empty categories are hidden (childrenCount = 0)
 * - Breadcrumbs show full path including skipped levels
 */
data class ContentUiState(
    // Current navigation state
    val currentCategoryId: String? = null, // null = root level
    val currentCategories: List<Category> = emptyList(), // Categories at current level
    val currentGroups: Map<String, List<Category>>? = null, // Non-null when showing groups
    val isLeafLevel: Boolean = false, // True when showing content (movies/series/channels)

    // Breadcrumb navigation
    val breadcrumbPath: List<BreadcrumbItem> = emptyList(),

    // Loading/error states
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** Helper: Should show loading indicator */
    val showLoading: Boolean
        get() = isLoading && currentCategories.isEmpty() && currentGroups == null

    /** Helper: Should show error message */
    val showError: Boolean
        get() = error != null && currentCategories.isEmpty() && currentGroups == null

    /** Helper: Should show content */
    val showContent: Boolean
        get() = !isLoading && error == null

    /** Determine if user is currently viewing groups list */
    val isViewingGroups: Boolean
        get() = currentGroups != null

    /** Determine if user is currently viewing categories list */
    val isViewingCategories: Boolean
        get() = !isLeafLevel && currentCategories.isNotEmpty() && currentGroups == null

    /** Determine if user is currently viewing content items */
    val isViewingContent: Boolean
        get() = isLeafLevel
}
