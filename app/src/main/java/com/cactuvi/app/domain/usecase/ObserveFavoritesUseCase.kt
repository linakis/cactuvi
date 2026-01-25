package com.cactuvi.app.domain.usecase

import com.cactuvi.app.data.db.entities.FavoriteEntity
import com.cactuvi.app.domain.repository.ContentRepository
import javax.inject.Inject

/** Observe user's favorites list. Returns reactive Flow that updates when favorites change. */
class ObserveFavoritesUseCase
@Inject
constructor(
    private val contentRepository: ContentRepository,
) {
    suspend operator fun invoke(contentType: String? = null): Result<List<FavoriteEntity>> {
        return contentRepository.getFavorites(contentType)
    }
}
