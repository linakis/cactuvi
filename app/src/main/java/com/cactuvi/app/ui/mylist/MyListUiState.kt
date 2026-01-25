package com.cactuvi.app.ui.mylist

import com.cactuvi.app.data.db.entities.FavoriteEntity

/**
 * UI state for My List screen.
 */
data class MyListUiState(
    val isLoading: Boolean = true,
    val favorites: List<FavoriteEntity> = emptyList(),
    val error: String? = null
) {
    val showContent: Boolean get() = !isLoading && favorites.isNotEmpty()
    val showEmpty: Boolean get() = !isLoading && favorites.isEmpty()
    val showError: Boolean get() = !isLoading && error != null
}
