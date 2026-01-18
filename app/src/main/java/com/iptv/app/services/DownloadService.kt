package com.iptv.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.iptv.app.R

@UnstableApi
class DownloadService : Media3DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.app_name,
    0
) {
    
    companion object {
        const val CHANNEL_ID = "download_channel"
        const val FOREGROUND_NOTIFICATION_ID = 1
        
        private var downloadManager: DownloadManager? = null
        private var downloadTracker: DownloadTracker? = null
        
        fun getDownloadManager(context: Context): DownloadManager {
            if (downloadManager == null) {
                downloadManager = DownloadUtil.getDownloadManager(context)
            }
            return downloadManager!!
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Initialize download tracker
        if (downloadTracker == null) {
            downloadTracker = DownloadTracker(this, getDownloadManager())
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        downloadTracker?.release()
    }
    
    override fun getDownloadManager(): DownloadManager {
        return DownloadService.getDownloadManager(this)
    }
    
    override fun getScheduler(): Scheduler? {
        // Optional: Implement a scheduler for background downloads
        return null
    }
    
    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val downloadCount = downloads.size
        val message = when {
            downloadCount == 0 -> "No downloads"
            downloadCount == 1 -> "Downloading 1 item"
            else -> "Downloading $downloadCount items"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IPTV Downloads")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
