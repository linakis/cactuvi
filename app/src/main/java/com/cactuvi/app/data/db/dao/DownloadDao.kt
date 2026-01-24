package com.cactuvi.app.data.db.dao

import androidx.room.*
import com.cactuvi.app.data.db.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE status = 'completed' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE status IN ('queued', 'downloading') ORDER BY addedAt ASC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE contentId = :contentId LIMIT 1")
    suspend fun getDownload(contentId: String): DownloadEntity?
    
    @Query("SELECT * FROM downloads WHERE contentId = :contentId LIMIT 1")
    fun getDownloadFlow(contentId: String): Flow<DownloadEntity?>
    
    @Query("SELECT * FROM downloads WHERE contentType = :contentType AND status = 'completed'")
    fun getCompletedDownloadsByType(contentType: String): Flow<List<DownloadEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)
    
    @Update
    suspend fun update(download: DownloadEntity)
    
    @Query("UPDATE downloads SET status = :status, progress = :progress, bytesDownloaded = :bytesDownloaded WHERE contentId = :contentId")
    suspend fun updateProgress(contentId: String, status: String, progress: Float, bytesDownloaded: Long)
    
    @Query("UPDATE downloads SET status = :status, downloadUri = :downloadUri, progress = 1.0, completedAt = :completedAt WHERE contentId = :contentId")
    suspend fun markAsCompleted(contentId: String, status: String = "completed", downloadUri: String, completedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE downloads SET status = :status, failureReason = :failureReason WHERE contentId = :contentId")
    suspend fun markAsFailed(contentId: String, status: String = "failed", failureReason: String?)
    
    @Query("DELETE FROM downloads WHERE contentId = :contentId")
    suspend fun delete(contentId: String)
    
    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
    
    @Query("DELETE FROM downloads WHERE status = 'completed'")
    suspend fun deleteCompleted()
}
