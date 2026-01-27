package com.cactuvi.app.services

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.cactuvi.app.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@UnstableApi
class DownloadTracker(
    private val downloadManager: DownloadManager,
    private val downloadRepository: DownloadRepository,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        downloadManager.addListener(DownloadListener())
    }

    private inner class DownloadListener : DownloadManager.Listener {

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            scope.launch {
                Log.d(
                    "DownloadTracker",
                    "Download state changed: id=${download.request.id}, state=${download.state}, bytes=${download.bytesDownloaded}, contentLength=${download.contentLength}"
                )

                when (download.state) {
                    Download.STATE_QUEUED -> {
                        Log.d("DownloadTracker", "Download QUEUED: ${download.request.id}")
                        downloadRepository.updateDownloadProgress(
                            contentId = download.request.id,
                            status = "queued",
                            progress = 0f,
                            bytesDownloaded = 0L,
                        )
                    }
                    Download.STATE_DOWNLOADING -> {
                        val progress =
                            if (download.bytesDownloaded > 0 && download.contentLength > 0) {
                                (download.bytesDownloaded.toFloat() /
                                    download.contentLength.toFloat())
                            } else {
                                0f
                            }

                        Log.d(
                            "DownloadTracker",
                            "Download DOWNLOADING: ${download.request.id}, progress=$progress, bytes=${download.bytesDownloaded}/${download.contentLength}"
                        )

                        downloadRepository.updateDownloadProgress(
                            contentId = download.request.id,
                            status = "downloading",
                            progress = progress,
                            bytesDownloaded = download.bytesDownloaded,
                        )
                    }
                    Download.STATE_COMPLETED -> {
                        // Get the download URI from Media3
                        val downloadUri = download.request.uri.toString()

                        Log.d(
                            "DownloadTracker",
                            "Download COMPLETED: ${download.request.id}, uri=$downloadUri"
                        )

                        downloadRepository.markDownloadComplete(
                            contentId = download.request.id,
                            downloadUri = downloadUri,
                        )
                    }
                    Download.STATE_FAILED -> {
                        val reason = finalException?.message ?: "Unknown error"
                        Log.e(
                            "DownloadTracker",
                            "Download FAILED: ${download.request.id}, reason=$reason",
                            finalException
                        )
                        downloadRepository.markDownloadFailed(
                            contentId = download.request.id,
                            reason = reason,
                        )
                    }
                    Download.STATE_STOPPED -> {
                        downloadRepository.updateDownloadProgress(
                            contentId = download.request.id,
                            status = "paused",
                            progress = 0f,
                            bytesDownloaded = download.bytesDownloaded,
                        )
                    }
                    Download.STATE_REMOVING -> {
                        // Download is being removed, no action needed
                        // The repository will handle deletion
                    }
                    Download.STATE_RESTARTING -> {
                        downloadRepository.updateDownloadProgress(
                            contentId = download.request.id,
                            status = "downloading",
                            progress = 0f,
                            bytesDownloaded = 0L,
                        )
                    }
                }
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            // Download removed, database cleanup handled by repository
        }
    }

    fun release() {
        downloadRepository.release()
    }
}
