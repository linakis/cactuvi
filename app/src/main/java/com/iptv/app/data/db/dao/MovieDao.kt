package com.iptv.app.data.db.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.iptv.app.data.db.entities.MovieEntity

@Dao
interface MovieDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)
    
    @Query("SELECT * FROM movies ORDER BY num ASC")
    suspend fun getAll(): List<MovieEntity>
    
    @Query("SELECT * FROM movies ORDER BY num ASC")
    fun getAllPaged(): PagingSource<Int, MovieEntity>
    
    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY num ASC")
    suspend fun getByCategoryId(categoryId: String): List<MovieEntity>
    
    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY num ASC")
    fun getByCategoryIdPaged(categoryId: String): PagingSource<Int, MovieEntity>
    
    @Query("SELECT * FROM movies WHERE isFavorite = 1 ORDER BY name ASC")
    suspend fun getFavorites(): List<MovieEntity>
    
    @Query("SELECT * FROM movies ORDER BY added DESC LIMIT :limit")
    suspend fun getRecentlyAdded(limit: Int = 20): List<MovieEntity>
    
    @Query("SELECT * FROM movies WHERE rating5Based IS NOT NULL AND rating5Based > 0 ORDER BY rating5Based DESC LIMIT :limit")
    suspend fun getTopRated(limit: Int = 20): List<MovieEntity>
    
    @Query("UPDATE movies SET isFavorite = :isFavorite WHERE streamId = :streamId")
    suspend fun updateFavorite(streamId: Int, isFavorite: Boolean)
    
    @Query("UPDATE movies SET resumePosition = :position WHERE streamId = :streamId")
    suspend fun updateResumePosition(streamId: Int, position: Long)
    
    @Query("DELETE FROM movies WHERE lastUpdated < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("DELETE FROM movies")
    suspend fun clearAll()
}
