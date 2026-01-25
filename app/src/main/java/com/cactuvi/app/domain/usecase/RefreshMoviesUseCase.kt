package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.repository.ContentRepository
import javax.inject.Inject

/**
 * Trigger manual refresh of movies data.
 * Forces re-fetch from API regardless of cache state.
 * 
 * Should be called when user explicitly requests refresh (e.g., pull-to-refresh).
 */
class RefreshMoviesUseCase @Inject constructor(
    private val contentRepository: ContentRepository
) {
    suspend operator fun invoke() {
        contentRepository.refreshMovies()
    }
}
