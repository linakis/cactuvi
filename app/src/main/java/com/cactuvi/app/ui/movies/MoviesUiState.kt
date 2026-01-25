package com.cactuvi.app.ui.movies

import com.cactuvi.app.utils.CategoryGrouper

/**
 * UI state for Movies screen.
 * Single source of truth for all Movies UI state.
 * 
 * Note: Uses CategoryGrouper types for now to match existing adapters.
 * Phase 4 will refactor adapters to use domain models.
 */
data class MoviesUiState(
    val navigationTree: CategoryGrouper.NavigationTree? = null,
    val currentLevel: NavigationLevel = NavigationLevel.GROUPS,
    val selectedGroupName: String? = null,
    val selectedCategoryId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    // Derived properties for UI
    val showLoading: Boolean get() = isLoading && navigationTree == null
    val showContent: Boolean get() = navigationTree != null
    val showError: Boolean get() = error != null && navigationTree == null
    
    // Helper to get selected group from tree
    val selectedGroup: CategoryGrouper.GroupNode? 
        get() = selectedGroupName?.let { name -> navigationTree?.findGroup(name) }
}

enum class NavigationLevel {
    GROUPS,      // Show all groups
    CATEGORIES,  // Show categories in group
    CONTENT      // Show filtered content
}
