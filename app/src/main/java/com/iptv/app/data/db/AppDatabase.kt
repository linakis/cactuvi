package com.iptv.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
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
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING) // Enable WAL for better concurrency
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Optimize SQLite for bulk inserts
                            db.execSQL("PRAGMA synchronous = NORMAL") // Faster than FULL, safe with WAL
                            db.execSQL("PRAGMA temp_store = MEMORY") // Use memory for temp tables
                            db.execSQL("PRAGMA cache_size = -64000") // 64MB cache (negative = KB)
                        }
                    })
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
