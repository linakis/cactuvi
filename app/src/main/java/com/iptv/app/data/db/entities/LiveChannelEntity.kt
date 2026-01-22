package com.iptv.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "live_channels",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["sourceId", "streamId"], unique = true)
    ]
)
data class LiveChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceId: String,
    val streamId: Int,
    val num: Int,
    val name: String,
    val streamType: String?,
    val streamIcon: String?,
    val epgChannelId: String?,
    val added: String?,
    val categoryId: String,
    val customSid: String?,
    val tvArchive: Int?,
    val directSource: String?,
    val tvArchiveDuration: String?,
    val categoryName: String,
    val isFavorite: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
