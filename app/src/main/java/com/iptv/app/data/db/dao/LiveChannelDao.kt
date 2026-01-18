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
}
