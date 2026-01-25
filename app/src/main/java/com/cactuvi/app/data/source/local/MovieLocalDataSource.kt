package com.cactuvi.app.data.source.local

import androidx.paging.PagingSource
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.entities.MovieEntity
import com.cactuvi.app.data.db.entities.CacheMetadataEntity

/**
 * Local data source for movies.
 * Handles all database operations for movies.
 * 
 * Note: @Inject annotation will be added in Phase 1.4 (Hilt setup)
 */
class MovieLocalDataSource(
    private val database: AppDatabase
) {
    /**
     * Get all movies from database
     */
    suspend fun getAll(): List<MovieEntity> {
        return database.movieDao().getAll()
    }
    
    /**
     * Get movies by category (paged)
     */
    fun getByCategoryPaged(categoryId: String): PagingSource<Int, MovieEntity> {
        return database.movieDao().getByCategoryIdPaged(categoryId)
    }
    
    /**
     * Get all movies (paged)
     */
    fun getAllPaged(): PagingSource<Int, MovieEntity> {
        return database.movieDao().getAllPaged()
    }
    
    /**
     * Delete movies by source ID
     */
    suspend fun deleteBySourceId(sourceId: String) {
        database.movieDao().deleteBySourceId(sourceId)
    }
    
    /**
     * Insert movies in batch via DbWriter
     */
    suspend fun insertAll(movies: List<MovieEntity>) {
        database.getDbWriter().writeMovies(movies)
    }
    
    /**
     * Get cache metadata for movies
     */
    suspend fun getCacheMetadata(): CacheMetadataEntity? {
        return database.cacheMetadataDao().get("movies")
    }
    
    /**
     * Update cache metadata
     */
    suspend fun updateCacheMetadata(metadata: CacheMetadataEntity) {
        database.cacheMetadataDao().insert(metadata)
    }
    
    /**
     * Get count of movies
     */
    suspend fun getCount(): Int {
        return database.movieDao().getCount()
    }
    
    /**
     * Get count by category
     */
    suspend fun getCountByCategory(categoryId: String): Int {
        return database.movieDao().getCountByCategory(categoryId)
    }
}
