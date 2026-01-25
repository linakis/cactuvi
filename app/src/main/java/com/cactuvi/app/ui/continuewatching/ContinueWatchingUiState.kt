package com.cactuvi.app.ui.continuewatching

import com.cactuvi.app.data.db.entities.WatchHistoryEntity

/**
 * UI state for Continue Watching screen.
 */
data class ContinueWatchingUiState(
    val isLoading: Boolean = true,
    val watchHistory: List<WatchHistoryEntity> = emptyList(),
    val error: String? = null
) {
    val showContent: Boolean get() = !isLoading && watchHistory.isNotEmpty()
    val showEmpty: Boolean get() = !isLoading && watchHistory.isEmpty()
    val showError: Boolean get() = !isLoading && error != null
}
