package com.cactuvi.app.domain.usecase

import com.cactuvi.app.data.models.MovieInfo
import com.cactuvi.app.domain.repository.ContentRepository
import javax.inject.Inject

/**
 * Get detailed information for a specific movie.
 * Returns Result<MovieInfo> with movie details including description, cast, rating, etc.
 */
class GetMovieInfoUseCase @Inject constructor(
    private val contentRepository: ContentRepository
) {
    suspend operator fun invoke(vodId: Int): Result<MovieInfo> {
        return contentRepository.getMovieInfo(vodId)
    }
}
