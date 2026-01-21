package com.iptv.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "navigation_groups",
    indices = [Index(value = ["type"])]
)
data class NavigationGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,              // "live", "vod", "series"
    val groupName: String,          // "UK", "NETFLIX", "EN", etc.
    val categoryIdsCsv: String,     // CSV: "47,1141,1961,..." (faster than JSON parsing)
    val separator: String,          // "|", "-", "/", "FIRST_WORD"
    val lastUpdated: Long = System.currentTimeMillis()
)
