package com.cactuvi.app.data.db.dao

import androidx.room.*
import com.cactuvi.app.data.db.entities.WatchHistoryEntity

@Dao
interface WatchHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: WatchHistoryEntity)
    
    @Query("SELECT * FROM watch_history WHERE sourceId = :sourceId AND contentId = :contentId AND contentType = :contentType")
    suspend fun getByContentId(sourceId: String, contentId: String, contentType: String): WatchHistoryEntity?
    
    @Query("SELECT * FROM watch_history WHERE sourceId = :sourceId AND isCompleted = 0 ORDER BY lastWatched DESC LIMIT :limit")
    suspend fun getIncomplete(sourceId: String, limit: Int = 20): List<WatchHistoryEntity>
    
    @Query("SELECT * FROM watch_history WHERE sourceId = :sourceId ORDER BY lastWatched DESC LIMIT :limit")
    suspend fun getRecent(sourceId: String, limit: Int = 20): List<WatchHistoryEntity>
    
    @Query("SELECT * FROM watch_history WHERE sourceId = :sourceId AND contentType = :type ORDER BY lastWatched DESC")
    suspend fun getByType(sourceId: String, type: String): List<WatchHistoryEntity>
    
    @Query("UPDATE watch_history SET resumePosition = :position, lastWatched = :timestamp, isCompleted = :isCompleted WHERE sourceId = :sourceId AND contentId = :id AND contentType = :type")
    suspend fun updatePosition(sourceId: String, id: String, type: String, position: Long, timestamp: Long, isCompleted: Boolean)
    
    @Delete
    suspend fun delete(history: WatchHistoryEntity)
    
    @Query("DELETE FROM watch_history WHERE sourceId = :sourceId AND lastWatched < :timestamp")
    suspend fun deleteOlderThan(sourceId: String, timestamp: Long)
    
    @Query("DELETE FROM watch_history WHERE sourceId = :sourceId")
    suspend fun clearBySource(sourceId: String)
    
    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
