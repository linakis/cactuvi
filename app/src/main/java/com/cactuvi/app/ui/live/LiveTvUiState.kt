package com.cactuvi.app.ui.live

import com.cactuvi.app.data.models.Category

/**
 * UI state for Live TV screen.
 * Single source of truth for all Live TV UI state.
 * 
 * Note: Live TV uses simple category list, not hierarchical tree like Movies/Series.
 */
data class LiveTvUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    // Derived properties for UI
    val showLoading: Boolean get() = isLoading && categories.isEmpty()
    val showContent: Boolean get() = categories.isNotEmpty()
    val showError: Boolean get() = error != null && categories.isEmpty()
    
    val isViewingCategory: Boolean get() = selectedCategoryId != null
    val selectedCategory: Category? 
        get() = selectedCategoryId?.let { id -> categories.find { it.categoryId == id } }
}
