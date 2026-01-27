package com.cactuvi.app.domain.repository

import androidx.paging.PagingData
import com.cactuvi.app.data.db.entities.FavoriteEntity
import com.cactuvi.app.data.db.entities.WatchHistoryEntity
import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.DataState
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.data.models.LoginResponse
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.MovieInfo
import com.cactuvi.app.data.models.NavigationResult
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.data.models.SeriesInfo
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.utils.CategoryGrouper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
    // ========== REACTIVE STATE MANAGEMENT ==========
    // StateFlows for observing loading state of each content type
    // Used by SyncCoordinator and other components that need to check current state

    /** Current loading state for movies data */
    val moviesState: StateFlow<DataState<Unit>>

    /** Current loading state for series data */
    val seriesState: StateFlow<DataState<Unit>>

    /** Current loading state for live channels data */
    val liveState: StateFlow<DataState<Unit>>

    // Authentication
    suspend fun authenticate(): Result<LoginResponse>

    // Parallel loading
    suspend fun loadAllContentParallel():
        Triple<Result<List<Movie>>, Result<List<Series>>, Result<List<LiveChannel>>>

    // Movies
    fun observeMovies(): Flow<Resource<NavigationTree>>

    suspend fun refreshMovies()

    suspend fun loadMovies(forceRefresh: Boolean = false)

    suspend fun getMovies(forceRefresh: Boolean = false): Result<List<Movie>>

    suspend fun getMovieCategories(forceRefresh: Boolean = false): Result<List<Category>>

    // Series
    fun observeSeries(): Flow<Resource<NavigationTree>>

    suspend fun refreshSeries()

    suspend fun loadSeries(forceRefresh: Boolean = false)

    suspend fun getSeries(forceRefresh: Boolean = false): Result<List<Series>>

    suspend fun getSeriesCategories(forceRefresh: Boolean = false): Result<List<Category>>

    // Live
    fun observeLive(): Flow<Resource<NavigationTree>>

    suspend fun refreshLive()

    suspend fun loadLive(forceRefresh: Boolean = false)

    suspend fun getLiveStreams(forceRefresh: Boolean = false): Result<List<LiveChannel>>

    suspend fun getLiveCategories(forceRefresh: Boolean = false): Result<List<Category>>

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

    suspend fun updateWatchProgress(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        resumePosition: Long,
        duration: Long,
        seriesId: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): Result<Unit>

    suspend fun deleteWatchHistoryItem(contentId: String): Result<Unit>

    suspend fun clearWatchHistory(): Result<Unit>

    // Navigation helpers
    suspend fun getTopLevelNavigation(
        contentType: ContentType,
        groupingEnabled: Boolean,
        separator: String,
    ): NavigationResult

    suspend fun getChildCategories(
        contentType: ContentType,
        parentCategoryId: String,
    ): NavigationResult

    suspend fun getCategoryById(contentType: ContentType, categoryId: String): Category?

    suspend fun getContentItemCount(contentType: ContentType, categoryId: String): Int

    // Cache management
    suspend fun getCachedVodNavigationTree(): CategoryGrouper.NavigationTree?

    suspend fun getCachedSeriesNavigationTree(): CategoryGrouper.NavigationTree?

    suspend fun getCachedLiveNavigationTree(): CategoryGrouper.NavigationTree?

    suspend fun clearAllCache(): Result<Unit>

    suspend fun clearSourceCache(sourceId: String): Result<Unit>
}
