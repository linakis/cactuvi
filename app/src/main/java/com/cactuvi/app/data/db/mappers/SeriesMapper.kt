package com.cactuvi.app.data.db.mappers

import com.cactuvi.app.data.db.entities.SeriesEntity
import com.cactuvi.app.data.models.Series

fun Series.toEntity(sourceId: String, categoryName: String): SeriesEntity {
    return SeriesEntity(
        sourceId = sourceId,
        seriesId = seriesId,
        num = num,
        name = name,
        cover = cover,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate,
        lastModified = lastModified,
        rating = rating,
        rating5Based = rating5Based,
        backdropPath = backdropPath,
        youtubeTrailer = youtubeTrailer,
        episodeRunTime = episodeRunTime,
        categoryId = categoryId,
        categoryName = categoryName,
        isFavorite = isFavorite
    )
}

fun SeriesEntity.toModel(): Series {
    return Series(
        num = num,
        name = name,
        seriesId = seriesId,
        cover = cover,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate,
        lastModified = lastModified,
        rating = rating,
        rating5Based = rating5Based,
        backdropPath = backdropPath,
        youtubeTrailer = youtubeTrailer,
        episodeRunTime = episodeRunTime,
        categoryId = categoryId,
        isFavorite = isFavorite,
        categoryName = categoryName
    )
}
