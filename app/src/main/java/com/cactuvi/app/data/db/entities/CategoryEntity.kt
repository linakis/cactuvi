package com.cactuvi.app.data.db.entities

import androidx.room.Entity

@Entity(
    tableName = "categories",
    primaryKeys = ["sourceId", "categoryId", "type"],
)
data class CategoryEntity(
    val sourceId: String,
    val categoryId: String,
    val categoryName: String,
    val parentId: Int, // Parent category ID (0 = root level)
    val type: String, // "live", "vod", "series"
    val childrenCount: Int = 0, // Pre-computed count of direct children (or content items for leaf)
    val isLeaf: Boolean = false, // True if this category contains content items, not subcategories
    val lastUpdated: Long = System.currentTimeMillis(),
)
