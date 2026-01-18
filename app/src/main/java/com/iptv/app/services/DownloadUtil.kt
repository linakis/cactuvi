package com.iptv.app.services

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object DownloadUtil {
    
    private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
    
    private var downloadManager: DownloadManager? = null
    private var downloadCache: Cache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    
    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            val downloadDirectory = File(context.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
            val cache = getDownloadCache(context)
            
            val dataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setUserAgent("IPTV-App/1.0")
                        .setConnectTimeoutMs(30000)
                        .setReadTimeoutMs(30000)
                )
            
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context),
                cache,
                dataSourceFactory,
                Executors.newFixedThreadPool(3) // Allow 3 simultaneous downloads
            ).apply {
                maxParallelDownloads = 3
            }
        }
        return downloadManager!!
    }
    
    @Synchronized
    private fun getDownloadCache(context: Context): Cache {
        if (downloadCache == null) {
            val downloadDirectory = File(context.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
            downloadCache = SimpleCache(
                downloadDirectory,
                NoOpCacheEvictor(),
                getDatabaseProvider(context)
            )
        }
        return downloadCache!!
    }
    
    @Synchronized
    private fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context)
        }
        return databaseProvider!!
    }
    
    fun release() {
        downloadManager?.release()
        downloadManager = null
        
        try {
            downloadCache?.release()
        } catch (e: Exception) {
            // Ignore
        }
        downloadCache = null
    }
}
