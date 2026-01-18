package com.iptv.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,      // "movie_123" or "episode_456" or "channel_789"
    val contentType: String,    // "movie", "series_episode", "live_channel"
    val contentName: String,
    val posterUrl: String?,
    val resumePosition: Long,   // milliseconds
    val duration: Long,         // milliseconds (total)
    val lastWatched: Long,      // timestamp
    val isCompleted: Boolean,
    
    // For series episodes
    val seriesId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)
