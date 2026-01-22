package com.iptv.app.data.db.mappers

import com.iptv.app.data.db.entities.MovieEntity
import com.iptv.app.data.models.Movie

fun Movie.toEntity(sourceId: String, categoryName: String): MovieEntity {
    return MovieEntity(
        sourceId = sourceId,
        streamId = streamId,
        num = num,
        name = name,
        streamType = streamType,
        streamIcon = streamIcon,
        rating = rating,
        rating5Based = rating5Based,
        added = added,
        categoryId = categoryId,
        containerExtension = containerExtension,
        customSid = customSid,
        directSource = directSource,
        categoryName = categoryName,
        isFavorite = isFavorite,
        resumePosition = resumePosition
    )
}

fun MovieEntity.toModel(): Movie {
    return Movie(
        num = num,
        name = name,
        streamType = streamType,
        streamId = streamId,
        streamIcon = streamIcon,
        rating = rating,
        rating5Based = rating5Based,
        added = added,
        categoryId = categoryId,
        containerExtension = containerExtension,
        customSid = customSid,
        directSource = directSource,
        isFavorite = isFavorite,
        categoryName = categoryName,
        resumePosition = resumePosition
    )
}
