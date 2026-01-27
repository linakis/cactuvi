package com.cactuvi.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cactuvi.app.data.db.dao.*
import com.cactuvi.app.data.db.entities.*

@Database(
    entities =
        [
            LiveChannelEntity::class,
            MovieEntity::class,
            SeriesEntity::class,
            CategoryEntity::class,
            WatchHistoryEntity::class,
            FavoriteEntity::class,
            DownloadEntity::class,
            CacheMetadataEntity::class,
            StreamSourceEntity::class,
        ],
    version = 10,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun liveChannelDao(): LiveChannelDao

    abstract fun movieDao(): MovieDao

    abstract fun seriesDao(): SeriesDao

    abstract fun categoryDao(): CategoryDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun downloadDao(): DownloadDao

    abstract fun cacheMetadataDao(): CacheMetadataDao

    abstract fun streamSourceDao(): StreamSourceDao

    /**
     * Get access to underlying SQLite database for optimized bulk operations. Used by
     * OptimizedBulkInsert utility.
     */
    fun getSqliteDatabase(): SupportSQLiteDatabase = openHelper.writableDatabase

    /**
     * Get DbWriter instance for serialized database writes. Uses shared Mutex to eliminate
     * concurrent write contention.
     */
    fun getDbWriter(): DbWriter = DbWriter(this)
}
