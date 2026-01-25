package com.cactuvi.app.domain.usecase

import com.cactuvi.app.data.db.entities.WatchHistoryEntity
import com.cactuvi.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Observe user's watch history.
 * Returns reactive Flow that updates when history changes.
 */
class ObserveWatchHistoryUseCase @Inject constructor(
    private val contentRepository: ContentRepository
) {
    suspend operator fun invoke(limit: Int = 20): Result<List<WatchHistoryEntity>> {
        return contentRepository.getWatchHistory(limit)
    }
}
