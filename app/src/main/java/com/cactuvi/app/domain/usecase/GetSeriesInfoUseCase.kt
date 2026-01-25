package com.cactuvi.app.domain.usecase

import com.cactuvi.app.data.models.SeriesInfo
import com.cactuvi.app.domain.repository.ContentRepository
import javax.inject.Inject

/**
 * Get detailed information for a specific series. Returns Result<SeriesInfo> with series details
 * including description, seasons, episodes, etc.
 */
class GetSeriesInfoUseCase
@Inject
constructor(
    private val contentRepository: ContentRepository,
) {
    suspend operator fun invoke(seriesId: Int): Result<SeriesInfo> {
        return contentRepository.getSeriesInfo(seriesId)
    }
}
