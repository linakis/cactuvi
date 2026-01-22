package com.iptv.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "navigation_groups",
    indices = [
        Index(value = ["type"]),
        Index(value = ["sourceId"])
    ]
)
data class NavigationGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sourceId: String,
    val type: String,              // "live", "vod", "series"
    val groupName: String,          // "UK", "NETFLIX", "EN", etc.
    val categoryIdsJson: String,    // JSON array: ["47","1141","1961",...]
    val separator: String,          // "|", "-", "/", "FIRST_WORD"
    val lastUpdated: Long = System.currentTimeMillis()
)
