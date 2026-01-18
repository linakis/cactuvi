package com.iptv.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val contentId: String, // Format: "movie_123" or "series_456_episode_789"
    val contentType: String, // "movie" or "series"
    val contentName: String,
    val episodeName: String? = null, // For series episodes
    val seasonNumber: Int? = null, // For series episodes
    val episodeNumber: Int? = null, // For series episodes
    val posterUrl: String?,
    val streamUrl: String,
    val downloadUri: String? = null, // Local file path after download
    val status: String, // "queued", "downloading", "completed", "failed", "paused"
    val progress: Float = 0f, // 0.0 to 1.0
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val failureReason: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
