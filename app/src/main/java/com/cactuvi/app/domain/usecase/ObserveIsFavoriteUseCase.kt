package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Observe if content is marked as favorite.
 * Returns reactive Flow<Boolean> that updates when favorite status changes.
 */
class ObserveIsFavoriteUseCase @Inject constructor(
    private val contentRepository: ContentRepository
) {
    suspend operator fun invoke(contentId: String, contentType: String): Result<Boolean> {
        return contentRepository.isFavorite(contentId, contentType)
    }
}
