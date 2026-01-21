package com.iptv.app.data.db.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.iptv.app.data.db.entities.LiveChannelEntity

@Dao
interface LiveChannelDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<LiveChannelEntity>)
    
    @Query("SELECT * FROM live_channels ORDER BY num ASC")
    suspend fun getAll(): List<LiveChannelEntity>
    
    @Query("SELECT * FROM live_channels ORDER BY num ASC")
    fun getAllPaged(): PagingSource<Int, LiveChannelEntity>
    
    @Query("SELECT * FROM live_channels WHERE categoryId = :categoryId ORDER BY num ASC")
    suspend fun getByCategoryId(categoryId: String): List<LiveChannelEntity>
    
    @Query("SELECT * FROM live_channels WHERE categoryId = :categoryId ORDER BY num ASC")
    fun getByCategoryIdPaged(categoryId: String): PagingSource<Int, LiveChannelEntity>
    
    @Query("SELECT * FROM live_channels WHERE isFavorite = 1 ORDER BY name ASC")
    suspend fun getFavorites(): List<LiveChannelEntity>
    
    @Query("UPDATE live_channels SET isFavorite = :isFavorite WHERE streamId = :streamId")
    suspend fun updateFavorite(streamId: Int, isFavorite: Boolean)
    
    @Query("DELETE FROM live_channels WHERE lastUpdated < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("DELETE FROM live_channels")
    suspend fun clearAll()
    
    // ========== CACHE VALIDATION QUERIES ==========
    
    /**
     * Get count of live channels in cache for fast validation.
     * Used as fallback when metadata table not available.
     */
    @Query("SELECT COUNT(*) FROM live_channels")
    suspend fun getCount(): Int
    
    /**
     * Get first row's lastUpdated timestamp for cache validity check.
     * Fast alternative to loading all data just to check timestamp.
     */
    @Query("SELECT lastUpdated FROM live_channels LIMIT 1")
    suspend fun getFirstUpdatedTime(): Long?
}
