package com.cactuvi.app.ui.movies

import com.cactuvi.app.domain.model.GroupNode
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.ContentCategory

/**
 * UI state for Movies screen.
 * Single source of truth for all Movies UI state.
 */
data class MoviesUiState(
    val navigationTree: NavigationTree? = null,
    val currentLevel: NavigationLevel = NavigationLevel.GROUPS,
    val selectedGroup: GroupNode? = null,
    val selectedCategory: ContentCategory? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    // Derived properties for UI
    val showLoading: Boolean get() = isLoading && navigationTree == null
    val showContent: Boolean get() = navigationTree != null
    val showError: Boolean get() = error != null && navigationTree == null
}

enum class NavigationLevel {
    GROUPS,      // Show all groups
    CATEGORIES,  // Show categories in group
    CONTENT      // Show filtered content
}
