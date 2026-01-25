package com.cactuvi.app.data.source.local

import androidx.paging.PagingSource
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.entities.SeriesEntity
import com.cactuvi.app.data.db.entities.CacheMetadataEntity

/**
 * Local data source for series.
 * Handles all database operations for series.
 * 
 * Note: @Inject annotation will be added in Phase 1.4 (Hilt setup)
 */
class SeriesLocalDataSource(
    private val database: AppDatabase
) {
    /**
     * Get all series from database
     */
    suspend fun getAll(): List<SeriesEntity> = database.seriesDao().getAll()
    
    /**
     * Get series by category (paged)
     */
    fun getByCategoryPaged(categoryId: String): PagingSource<Int, SeriesEntity> =
        database.seriesDao().getByCategoryIdPaged(categoryId)
    
    /**
     * Get all series (paged)
     */
    fun getAllPaged(): PagingSource<Int, SeriesEntity> =
        database.seriesDao().getAllPaged()
    
    /**
     * Delete series by source ID
     */
    suspend fun deleteBySourceId(sourceId: String) =
        database.seriesDao().deleteBySourceId(sourceId)
    
    /**
     * Insert series in batch via DbWriter
     */
    suspend fun insertAll(series: List<SeriesEntity>) =
        database.getDbWriter().writeSeries(series)
    
    /**
     * Get cache metadata for series
     */
    suspend fun getCacheMetadata(): CacheMetadataEntity? =
        database.cacheMetadataDao().get("series")
    
    /**
     * Update cache metadata
     */
    suspend fun updateCacheMetadata(metadata: CacheMetadataEntity) =
        database.cacheMetadataDao().insert(metadata)
    
    /**
     * Get count of series
     */
    suspend fun getCount(): Int = database.seriesDao().getCount()
    
    /**
     * Get count by category
     */
    suspend fun getCountByCategory(categoryId: String): Int =
        database.seriesDao().getCountByCategory(categoryId)
}
