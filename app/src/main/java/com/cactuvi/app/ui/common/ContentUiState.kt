package com.cactuvi.app.ui.common

import com.cactuvi.app.utils.CategoryGrouper

/**
 * Navigation levels for hierarchical content browsing
 */
enum class NavigationLevel {
    GROUPS,      // Top level: Genre/language groups (may be skipped if only 1 group)
    CATEGORIES,  // Mid level: Categories within a group (may be skipped if only 1 category)
    CONTENT      // Bottom level: Actual content items (always shown)
}

/**
 * Unified UI state for all content types (Movies/Series/LiveTV)
 * 
 * Supports dynamic 2-3 level navigation:
 * - 3 levels: Groups → Categories → Content (when grouping enabled, multiple items at each level)
 * - 2 levels: Categories → Content (when grouping disabled OR only 1 group exists)
 * - 1 level: Content only (when only 1 category exists)
 * 
 * Auto-skip behavior:
 * - If only 1 group exists → skip to CATEGORIES level
 * - If only 1 category in group → skip to CONTENT level
 * - If only 1 group with 1 category → skip directly to CONTENT level
 */
data class ContentUiState(
    val navigationTree: CategoryGrouper.NavigationTree? = null,
    val currentLevel: NavigationLevel = NavigationLevel.GROUPS,
    val selectedGroupName: String? = null,
    val selectedCategoryId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * Get the currently selected group node from the navigation tree
     */
    val selectedGroup: CategoryGrouper.GroupNode?
        get() = selectedGroupName?.let { navigationTree?.findGroup(it) }
    
    /**
     * Build breadcrumb path showing current navigation position
     * Includes skipped levels for user context
     * 
     * Examples:
     * - "Movies > GR > GR | Action" (all levels shown)
     * - "Movies > GR" (at categories level, groups were auto-skipped)
     * - "Movies > IT > IT | Action" (categories auto-skipped, shows in breadcrumb)
     */
    val breadcrumbPath: List<String>
        get() = buildList {
            // Always include selected group if set (even if groups level was skipped)
            selectedGroupName?.let { add(it) }
            
            // Add category name if at content level
            if (currentLevel == NavigationLevel.CONTENT && selectedCategoryId != null) {
                val category = selectedGroup?.categories?.find { it.categoryId == selectedCategoryId }
                category?.let { add(it.categoryName) }
            }
        }
    
    /**
     * Determine if user is currently viewing groups list
     */
    val isViewingGroups: Boolean
        get() = currentLevel == NavigationLevel.GROUPS
    
    /**
     * Determine if user is currently viewing categories list
     */
    val isViewingCategories: Boolean
        get() = currentLevel == NavigationLevel.CATEGORIES
    
    /**
     * Determine if user is currently viewing content items
     */
    val isViewingContent: Boolean
        get() = currentLevel == NavigationLevel.CONTENT
    
    /**
     * Check if navigation should auto-skip groups level
     * (only 1 group exists after filtering)
     */
    val shouldAutoSkipGroups: Boolean
        get() = navigationTree?.shouldSkipGroups == true
    
    /**
     * Check if navigation should auto-skip categories level
     * (only 1 category in selected group)
     */
    val shouldAutoSkipCategories: Boolean
        get() = selectedGroupName?.let { navigationTree?.shouldSkipCategories(it) } == true
    
    /**
     * Check if navigation should skip both groups and categories
     * (only 1 group with only 1 category)
     */
    val shouldAutoSkipBoth: Boolean
        get() = navigationTree?.shouldSkipBothLevels == true
    
    /**
     * Helper: Should show loading indicator
     */
    val showLoading: Boolean
        get() = isLoading
    
    /**
     * Helper: Should show error message
     */
    val showError: Boolean
        get() = error != null && !isLoading
    
    /**
     * Helper: Should show content
     */
    val showContent: Boolean
        get() = !isLoading && error == null && navigationTree != null
}
