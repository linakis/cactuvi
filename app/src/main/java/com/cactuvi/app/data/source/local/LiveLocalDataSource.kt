package com.cactuvi.app.data.source.local

import androidx.paging.PagingSource
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.entities.CacheMetadataEntity
import com.cactuvi.app.data.db.entities.LiveChannelEntity

/**
 * Local data source for live channels. Handles all database operations for live TV.
 *
 * Note: @Inject annotation will be added in Phase 1.4 (Hilt setup)
 */
class LiveLocalDataSource(
    private val database: AppDatabase,
) {
    /** Get all live channels from database */
    suspend fun getAll(): List<LiveChannelEntity> = database.liveChannelDao().getAll()

    /** Get live channels by category (paged) */
    fun getByCategoryPaged(categoryId: String): PagingSource<Int, LiveChannelEntity> =
        database.liveChannelDao().getByCategoryIdPaged(categoryId)

    /** Get all live channels (paged) */
    fun getAllPaged(): PagingSource<Int, LiveChannelEntity> =
        database.liveChannelDao().getAllPaged()

    /** Delete live channels by source ID */
    suspend fun deleteBySourceId(sourceId: String) =
        database.liveChannelDao().deleteBySourceId(sourceId)

    /** Insert live channels in batch via DbWriter */
    suspend fun insertAll(channels: List<LiveChannelEntity>) =
        database.getDbWriter().writeLiveChannels(channels)

    /** Get cache metadata for live channels */
    suspend fun getCacheMetadata(): CacheMetadataEntity? = database.cacheMetadataDao().get("live")

    /** Update cache metadata */
    suspend fun updateCacheMetadata(metadata: CacheMetadataEntity) =
        database.cacheMetadataDao().insert(metadata)

    /** Get count of live channels */
    suspend fun getCount(): Int = database.liveChannelDao().getCount()

    /** Get count by category */
    suspend fun getCountByCategory(categoryId: String): Int =
        database.liveChannelDao().getCountByCategory(categoryId)
}
