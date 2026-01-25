package com.cactuvi.app.data.db.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.cactuvi.app.data.db.entities.MovieEntity

@Dao
interface MovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInTransaction(movies: List<MovieEntity>)

    @Query("SELECT * FROM movies ORDER BY num ASC") suspend fun getAll(): List<MovieEntity>

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

    @Query(
        "SELECT * FROM movies WHERE rating5Based IS NOT NULL AND rating5Based > 0 ORDER BY rating5Based DESC LIMIT :limit",
    )
    suspend fun getTopRated(limit: Int = 20): List<MovieEntity>

    @Query("UPDATE movies SET isFavorite = :isFavorite WHERE streamId = :streamId")
    suspend fun updateFavorite(streamId: Int, isFavorite: Boolean)

    @Query("UPDATE movies SET resumePosition = :position WHERE streamId = :streamId")
    suspend fun updateResumePosition(streamId: Int, position: Long)

    @Query("DELETE FROM movies WHERE lastUpdated < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM movies WHERE 1") suspend fun clearAll()

    @Query("DELETE FROM movies WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    // ========== CACHE VALIDATION QUERIES ==========

    /**
     * Get count of movies in cache for fast validation. Used as fallback when metadata table not
     * available.
     */
    @Query("SELECT COUNT(*) FROM movies") suspend fun getCount(): Int

    /**
     * Get first row's lastUpdated timestamp for cache validity check. Fast alternative to loading
     * all data just to check timestamp.
     */
    @Query("SELECT lastUpdated FROM movies LIMIT 1") suspend fun getFirstUpdatedTime(): Long?

    /**
     * Get count of movies in a specific category. Used for displaying category counts without
     * loading all movies.
     */
    @Query("SELECT COUNT(*) FROM movies WHERE categoryId = :categoryId")
    suspend fun getCountByCategory(categoryId: String): Int
}
