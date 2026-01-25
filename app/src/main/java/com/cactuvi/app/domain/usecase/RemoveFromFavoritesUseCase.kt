package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.repository.ContentRepository
import javax.inject.Inject

/** Remove content from user's favorites list. */
class RemoveFromFavoritesUseCase
@Inject
constructor(
    private val contentRepository: ContentRepository,
) {
    suspend operator fun invoke(contentId: String, contentType: String): Result<Unit> {
        return contentRepository.removeFromFavorites(contentId, contentType)
    }
}
