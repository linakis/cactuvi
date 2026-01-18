package com.iptv.app.data.db.dao

import androidx.room.*
import com.iptv.app.data.db.entities.WatchHistoryEntity

@Dao
interface WatchHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: WatchHistoryEntity)
    
    @Query("SELECT * FROM watch_history WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun getByContentId(contentId: String, contentType: String): WatchHistoryEntity?
    
    @Query("SELECT * FROM watch_history WHERE isCompleted = 0 ORDER BY lastWatched DESC LIMIT :limit")
    suspend fun getIncomplete(limit: Int = 20): List<WatchHistoryEntity>
    
    @Query("SELECT * FROM watch_history ORDER BY lastWatched DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<WatchHistoryEntity>
    
    @Query("SELECT * FROM watch_history WHERE contentType = :type ORDER BY lastWatched DESC")
    suspend fun getByType(type: String): List<WatchHistoryEntity>
    
    @Query("UPDATE watch_history SET resumePosition = :position, lastWatched = :timestamp, isCompleted = :isCompleted WHERE contentId = :id AND contentType = :type")
    suspend fun updatePosition(id: String, type: String, position: Long, timestamp: Long, isCompleted: Boolean)
    
    @Delete
    suspend fun delete(history: WatchHistoryEntity)
    
    @Query("DELETE FROM watch_history WHERE lastWatched < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
