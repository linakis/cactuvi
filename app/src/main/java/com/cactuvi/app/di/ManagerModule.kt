package com.cactuvi.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.sync.ReactiveUpdateManager
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.PreferencesManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.SyncPreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides singleton managers and database. These will transition from
 * getInstance() pattern to proper DI.
 */
@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "iptv_database"
            )
            .fallbackToDestructiveMigration()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Optimize SQLite for bulk inserts
                        db.execSQL("PRAGMA synchronous = NORMAL") // Faster than FULL, safe with WAL
                        db.execSQL("PRAGMA temp_store = MEMORY") // Use memory for temp tables
                        db.execSQL("PRAGMA cache_size = -64000") // 64MB cache (negative = KB)
                    }
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideCredentialsManager(
        @ApplicationContext context: Context,
        database: AppDatabase
    ): CredentialsManager {
        return CredentialsManager(context, database)
    }

    @Provides
    @Singleton
    fun provideSourceManager(
        @ApplicationContext context: Context,
        database: AppDatabase
    ): SourceManager {
        return SourceManager(context, database)
    }

    @Provides
    @Singleton
    fun provideSyncPreferencesManager(
        @ApplicationContext context: Context
    ): SyncPreferencesManager {
        return SyncPreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideReactiveUpdateManager(): ReactiveUpdateManager {
        return ReactiveUpdateManager()
    }
}
