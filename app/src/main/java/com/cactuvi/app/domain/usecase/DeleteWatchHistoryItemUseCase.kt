package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.repository.ContentRepository
import javax.inject.Inject

/** Delete a specific item from watch history. */
class DeleteWatchHistoryItemUseCase
@Inject
constructor(
    private val contentRepository: ContentRepository,
) {
    suspend operator fun invoke(contentId: String): Result<Unit> {
        return contentRepository.deleteWatchHistoryItem(contentId)
    }
}
