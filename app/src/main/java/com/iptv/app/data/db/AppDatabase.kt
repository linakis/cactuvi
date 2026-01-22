package com.iptv.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.iptv.app.data.db.dao.*
import com.iptv.app.data.db.entities.*

@Database(
    entities = [
        LiveChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        CategoryEntity::class,
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        DownloadEntity::class,
        NavigationGroupEntity::class,
        CacheMetadataEntity::class,
        StreamSourceEntity::class
    ],
    version = 7,
    exportSchema = false
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
    abstract fun navigationGroupDao(): NavigationGroupDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
    abstract fun streamSourceDao(): StreamSourceDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
