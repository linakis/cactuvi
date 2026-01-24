package com.cactuvi.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "favorites",
    primaryKeys = ["sourceId", "id"],
    indices = [Index(value = ["sourceId", "contentType"])]
)
data class FavoriteEntity(
    val sourceId: String,            // Source identifier for multi-source support
    val id: String,                  // "movie_123" or "series_456" or "channel_789"
    val contentId: String,
    val contentType: String,         // "movie", "series", "live_channel"
    val contentName: String,
    val posterUrl: String?,
    val rating: String?,
    val categoryName: String,
    val addedAt: Long = System.currentTimeMillis()
)
