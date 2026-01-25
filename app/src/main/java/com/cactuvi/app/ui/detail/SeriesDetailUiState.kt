package com.cactuvi.app.ui.detail

import com.cactuvi.app.data.models.SeriesInfo

/** UI state for Series Detail screen. */
data class SeriesDetailUiState(
    val isLoading: Boolean = true,
    val seriesInfo: SeriesInfo? = null,
    val isFavorite: Boolean = false,
    val error: String? = null,
) {
    val showContent: Boolean
        get() = !isLoading && seriesInfo != null

    val showError: Boolean
        get() = !isLoading && error != null
}
