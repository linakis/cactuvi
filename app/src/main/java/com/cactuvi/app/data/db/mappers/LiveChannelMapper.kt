package com.cactuvi.app.data.db.mappers

import com.cactuvi.app.data.db.entities.LiveChannelEntity
import com.cactuvi.app.data.models.LiveChannel

fun LiveChannel.toEntity(sourceId: String, categoryName: String): LiveChannelEntity {
    return LiveChannelEntity(
        sourceId = sourceId,
        streamId = streamId,
        num = num,
        name = name,
        streamType = streamType,
        streamIcon = streamIcon,
        epgChannelId = epgChannelId,
        added = added,
        categoryId = categoryId,
        customSid = customSid,
        tvArchive = tvArchive,
        directSource = directSource,
        tvArchiveDuration = tvArchiveDuration,
        categoryName = categoryName,
        isFavorite = isFavorite,
    )
}

fun LiveChannelEntity.toModel(): LiveChannel {
    return LiveChannel(
        num = num,
        name = name,
        streamType = streamType,
        streamId = streamId,
        streamIcon = streamIcon,
        epgChannelId = epgChannelId,
        added = added,
        categoryId = categoryId,
        customSid = customSid,
        tvArchive = tvArchive,
        directSource = directSource,
        tvArchiveDuration = tvArchiveDuration,
        isFavorite = isFavorite,
        categoryName = categoryName,
    )
}
