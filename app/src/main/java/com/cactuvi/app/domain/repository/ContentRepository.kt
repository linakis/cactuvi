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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for content data.
 *
 * Uses REACTIVE patterns throughout:
 * - StateFlow for sync state (Loading/Success/Error)
 * - Flow for navigation data (auto-updates when DB changes)
 * - Flow for paged content
 */
interface ContentRepository {
    // ========== SYNC STATE ==========
    // StateFlows for observing loading state of each content type

    /** Current loading state for movies data */
    val moviesState: StateFlow<DataState<Unit>>

    /** Current loading state for series data */
    val seriesState: StateFlow<DataState<Unit>>

    /** Current loading state for live channels data */
    val liveState: StateFlow<DataState<Unit>>

    // ========== AUTHENTICATION ==========

    suspend fun authenticate(): Result<LoginResponse>

    // ========== DATA LOADING ==========
    // These trigger API fetches and update the database

    /** Load all content types in parallel */
    suspend fun loadAllContentParallel():
        Triple<Result<List<Movie>>, Result<List<Series>>, Result<List<LiveChannel>>>

    /** Load movies from API into database */
    suspend fun loadMovies(forceRefresh: Boolean = false)

    /** Load series from API into database */
    suspend fun loadSeries(forceRefresh: Boolean = false)

    /** Load live channels from API into database */
    suspend fun loadLive(forceRefresh: Boolean = false)

    // ========== REACTIVE NAVIGATION ==========
    // These return Flow<T> for automatic UI updates when DB changes

    /**
     * Observe top-level navigation (groups or categories) reactively. Emits new value whenever
     * underlying category data changes.
     */
    fun observeTopLevelNavigation(
        contentType: ContentType,
        groupingEnabled: Boolean,
        separator: String,
    ): Flow<NavigationResult>

    /**
     * Observe child categories reactively. Emits new value whenever underlying category data
     * changes.
     */
    fun observeChildCategories(
        contentType: ContentType,
        parentCategoryId: String,
    ): Flow<NavigationResult>

    /** Observe a single category by ID reactively. */
    fun observeCategory(contentType: ContentType, categoryId: String): Flow<Category?>

    /** Observe count of content items in a leaf category. */
    fun observeContentItemCount(contentType: ContentType, categoryId: String): Flow<Int>

    // ========== PAGED CONTENT ==========

    fun getMoviesPaged(categoryId: String?): Flow<PagingData<Movie>>

    fun getSeriesPaged(categoryId: String?): Flow<PagingData<Series>>

    fun getLiveStreamsPaged(categoryId: String?): Flow<PagingData<LiveChannel>>

    // ========== DETAIL INFO ==========

    suspend fun getMovieInfo(vodId: Int): Result<MovieInfo>

    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfo>

    // ========== FAVORITES ==========

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

    // ========== WATCH HISTORY ==========

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

    // ========== CACHE MANAGEMENT ==========

    suspend fun clearAllCache(): Result<Unit>

    suspend fun clearSourceCache(sourceId: String): Result<Unit>
}
