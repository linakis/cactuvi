package com.cactuvi.app.data.db.entities

import androidx.room.Entity

@Entity(
    tableName = "categories",
    primaryKeys = ["sourceId", "categoryId", "type"]
)
data class CategoryEntity(
    val sourceId: String,
    val categoryId: String,
    val categoryName: String,
    val type: String,  // "live", "vod", "series"
    val lastUpdated: Long = System.currentTimeMillis()
)
