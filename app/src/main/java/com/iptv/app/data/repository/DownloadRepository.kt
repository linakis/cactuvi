package com.iptv.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import com.iptv.app.data.db.AppDatabase
import com.iptv.app.data.db.entities.DownloadEntity
import com.iptv.app.services.DownloadService
import com.iptv.app.services.DownloadUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@UnstableApi
class DownloadRepository(private val context: Context) {
    
    private val downloadDao = AppDatabase.getInstance(context).downloadDao()
    private val downloadManager = DownloadService.getDownloadManager(context)
    
    fun getAllDownloads(): Flow<List<DownloadEntity>> {
        return downloadDao.getAllDownloads()
    }
    
    fun getCompletedDownloads(): Flow<List<DownloadEntity>> {
        return downloadDao.getCompletedDownloads()
    }
    
    fun getActiveDownloads(): Flow<List<DownloadEntity>> {
        return downloadDao.getActiveDownloads()
    }
    
    fun getDownload(contentId: String): Flow<DownloadEntity?> {
        return downloadDao.getDownloadFlow(contentId)
    }
    
    suspend fun getDownloadSync(contentId: String): DownloadEntity? {
        return downloadDao.getDownload(contentId)
    }
    
    suspend fun startDownload(
        contentId: String,
        contentType: String,
        contentName: String,
        streamUrl: String,
        posterUrl: String?,
        episodeName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        // Check if already exists
        val existing = downloadDao.getDownload(contentId)
        if (existing != null) {
            return // Already queued or downloaded
        }
        
        // Create download entity
        val downloadEntity = DownloadEntity(
            contentId = contentId,
            contentType = contentType,
            contentName = contentName,
            episodeName = episodeName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            posterUrl = posterUrl,
            streamUrl = streamUrl,
            status = "queued",
            progress = 0f
        )
        downloadDao.insert(downloadEntity)
        
        // Create Media3 download request
        val downloadRequest = DownloadRequest.Builder(contentId, Uri.parse(streamUrl))
            .setData(contentName.toByteArray())
            .build()
        
        // Add to Media3 DownloadManager
        downloadManager.addDownload(downloadRequest)
        
        // Start the download service
        Media3DownloadService.sendAddDownload(
            context,
            DownloadService::class.java,
            downloadRequest,
            false
        )
    }
    
    suspend fun pauseDownload(contentId: String) {
        Media3DownloadService.sendSetStopReason(
            context,
            DownloadService::class.java,
            contentId,
            Download.STOP_REASON_NONE,
            false
        )
        downloadDao.updateProgress(contentId, "paused", 0f, 0L)
    }
    
    suspend fun resumeDownload(contentId: String) {
        Media3DownloadService.sendSetStopReason(
            context,
            DownloadService::class.java,
            contentId,
            Download.STOP_REASON_NONE,
            false
        )
        downloadDao.updateProgress(contentId, "downloading", 0f, 0L)
    }
    
    suspend fun cancelDownload(contentId: String) {
        Media3DownloadService.sendRemoveDownload(
            context,
            DownloadService::class.java,
            contentId,
            false
        )
        downloadDao.delete(contentId)
    }
    
    suspend fun deleteDownload(contentId: String) {
        Media3DownloadService.sendRemoveDownload(
            context,
            DownloadService::class.java,
            contentId,
            false
        )
        downloadDao.delete(contentId)
    }
    
    suspend fun updateDownloadProgress(
        contentId: String,
        status: String,
        progress: Float,
        bytesDownloaded: Long
    ) {
        downloadDao.updateProgress(contentId, status, progress, bytesDownloaded)
    }
    
    suspend fun markDownloadComplete(contentId: String, downloadUri: String) {
        downloadDao.markAsCompleted(contentId, "completed", downloadUri)
    }
    
    suspend fun markDownloadFailed(contentId: String, reason: String?) {
        downloadDao.markAsFailed(contentId, "failed", reason)
    }
    
    suspend fun deleteAllDownloads() {
        Media3DownloadService.sendRemoveAllDownloads(
            context,
            DownloadService::class.java,
            false
        )
        downloadDao.deleteAll()
    }
    
    fun isDownloaded(contentId: String): Flow<Boolean> {
        return downloadDao.getDownloadFlow(contentId).map { 
            it?.status == "completed"
        }
    }
    
    fun release() {
        DownloadUtil.release()
    }
}
