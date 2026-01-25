package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.repository.ContentRepository

/**
 * Trigger manual refresh of movies data.
 * Forces re-fetch from API regardless of cache state.
 * 
 * Should be called when user explicitly requests refresh (e.g., pull-to-refresh).
 * 
 * Note: @Inject annotation will be added in Phase 1.4 (Hilt setup)
 */
class RefreshMoviesUseCase(
    private val contentRepository: ContentRepository
) {
    suspend operator fun invoke() {
        contentRepository.refreshMovies()
    }
}
