package com.iptv.app.data.db.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.iptv.app.data.db.entities.SeriesEntity

@Dao
interface SeriesDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<SeriesEntity>)
    
    @Query("SELECT * FROM series ORDER BY num ASC")
    suspend fun getAll(): List<SeriesEntity>
    
    @Query("SELECT * FROM series ORDER BY num ASC")
    fun getAllPaged(): PagingSource<Int, SeriesEntity>
    
    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY num ASC")
    suspend fun getByCategoryId(categoryId: String): List<SeriesEntity>
    
    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY num ASC")
    fun getByCategoryIdPaged(categoryId: String): PagingSource<Int, SeriesEntity>
    
    @Query("SELECT * FROM series WHERE isFavorite = 1 ORDER BY name ASC")
    suspend fun getFavorites(): List<SeriesEntity>
    
    @Query("SELECT * FROM series ORDER BY lastModified DESC LIMIT :limit")
    suspend fun getRecentlyAdded(limit: Int = 20): List<SeriesEntity>
    
    @Query("SELECT * FROM series WHERE rating5Based IS NOT NULL AND rating5Based > 0 ORDER BY rating5Based DESC LIMIT :limit")
    suspend fun getTopRated(limit: Int = 20): List<SeriesEntity>
    
    @Query("UPDATE series SET isFavorite = :isFavorite WHERE seriesId = :seriesId")
    suspend fun updateFavorite(seriesId: Int, isFavorite: Boolean)
    
    @Query("DELETE FROM series WHERE lastUpdated < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("DELETE FROM series")
    suspend fun clearAll()
}
