package com.iptv.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.iptv.app.data.db.Converters

@Entity(
    tableName = "series",
    indices = [Index(value = ["categoryId"])]
)
@TypeConverters(Converters::class)
data class SeriesEntity(
    @PrimaryKey val seriesId: Int,
    val num: Int,
    val name: String,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val lastModified: String?,
    val rating: String?,
    val rating5Based: Double?,
    val backdropPath: List<String>?,
    val youtubeTrailer: String?,
    val episodeRunTime: String?,
    val categoryId: String,
    val categoryName: String,
    val isFavorite: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
