package com.iptv.app.data.db.entities

import androidx.room.Entity

@Entity(
    tableName = "categories",
    primaryKeys = ["categoryId", "type"]
)
data class CategoryEntity(
    val categoryId: String,
    val categoryName: String,
    val type: String,  // "live", "vod", "series"
    val lastUpdated: Long = System.currentTimeMillis()
)
