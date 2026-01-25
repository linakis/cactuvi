package com.cactuvi.app.domain.repository

import androidx.paging.PagingData
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.Series
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for content data.
 * Domain layer defines the contract, data layer implements it.
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
    fun observeLive(): Flow<Resource<List<Category>>>
    suspend fun refreshLive()
    
    // Paging methods (temporary - will be removed after full migration)
    fun getMoviesPaged(categoryId: String? = null): Flow<PagingData<Movie>>
    fun getSeriesPaged(categoryId: String? = null): Flow<PagingData<Series>>
    fun getLiveStreamsPaged(categoryId: String? = null): Flow<PagingData<LiveChannel>>
}
