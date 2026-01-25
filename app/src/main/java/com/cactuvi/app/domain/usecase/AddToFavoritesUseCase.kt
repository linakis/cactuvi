package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.repository.ContentRepository
import javax.inject.Inject

/** Add content to user's favorites list. */
class AddToFavoritesUseCase
@Inject
constructor(
    private val contentRepository: ContentRepository,
) {
    suspend operator fun invoke(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        rating: String? = null,
        categoryName: String = "",
    ): Result<Unit> {
        return contentRepository.addToFavorites(
            contentId,
            contentType,
            contentName,
            posterUrl,
            rating,
            categoryName
        )
    }
}
