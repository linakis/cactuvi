package com.cactuvi.app.domain.repository

import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.data.models.Category
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
}
