package com.cactuvi.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movies",
    indices =
        [
            Index(value = ["categoryId"]),
            Index(value = ["sourceId", "streamId"], unique = true),
        ],
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceId: String,
    val streamId: Int,
    val num: Int,
    val name: String,
    val streamType: String?,
    val streamIcon: String?,
    val rating: String?,
    val rating5Based: Double?,
    val added: String?,
    val categoryId: String,
    val containerExtension: String,
    val customSid: String?,
    val directSource: String?,
    val categoryName: String,
    val isFavorite: Boolean = false,
    val resumePosition: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
)
