package com.cactuvi.app.ui.detail

import com.cactuvi.app.data.models.MovieInfo

/** UI state for Movie Detail screen. */
data class MovieDetailUiState(
    val isLoading: Boolean = true,
    val movieInfo: MovieInfo? = null,
    val isFavorite: Boolean = false,
    val resumePosition: Long = 0,
    val duration: Long = 0,
    val error: String? = null,
) {
    val showContent: Boolean
        get() = !isLoading && movieInfo != null

    val showError: Boolean
        get() = !isLoading && error != null

    val showResumeButton: Boolean
        get() = resumePosition > 0 && duration > 0
}
