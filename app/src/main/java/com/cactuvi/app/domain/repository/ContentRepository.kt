package com.cactuvi.app.domain.repository

import androidx.paging.PagingData
import com.cactuvi.app.data.db.entities.FavoriteEntity
import com.cactuvi.app.data.db.entities.WatchHistoryEntity
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.MovieInfo
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.data.models.SeriesInfo
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for content data. Domain layer defines the contract, data layer implements
 * it.
 *
 * This follows the Dependency Inversion Principle:
 * - Domain layer defines what it needs (this interface)
 * - Data layer provides implementation
 * - Makes domain layer independent of data layer details
 */
interface ContentRepository {
    // Movies
    fun observeMovies(): Flow<Resource<NavigationTree>>

    suspend fun refreshMovies()

    // Series
    fun observeSeries(): Flow<Resource<NavigationTree>>

    suspend fun refreshSeries()

    // Live
    fun observeLive(): Flow<Resource<NavigationTree>>

    suspend fun refreshLive()

    // Paging methods (temporary - will be removed after full migration)
    fun getMoviesPaged(categoryId: String?): Flow<PagingData<Movie>>

    fun getSeriesPaged(categoryId: String?): Flow<PagingData<Series>>

    fun getLiveStreamsPaged(categoryId: String?): Flow<PagingData<LiveChannel>>

    // Detail info
    suspend fun getMovieInfo(vodId: Int): Result<MovieInfo>

    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfo>

    // Favorites
    suspend fun isFavorite(contentId: String, contentType: String): Result<Boolean>

    suspend fun addToFavorites(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        rating: String? = null,
        categoryName: String = "",
    ): Result<Unit>

    suspend fun removeFromFavorites(contentId: String, contentType: String): Result<Unit>

    suspend fun getFavorites(contentType: String?): Result<List<FavoriteEntity>>

    // Watch history
    suspend fun getWatchHistory(limit: Int = 20): Result<List<WatchHistoryEntity>>

    suspend fun deleteWatchHistoryItem(contentId: String): Result<Unit>
}
