package com.cactuvi.app.di

import android.content.Context
import com.cactuvi.app.data.api.ApiClient
import com.cactuvi.app.data.api.XtreamApiService
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.source.local.LiveLocalDataSource
import com.cactuvi.app.data.source.local.MovieLocalDataSource
import com.cactuvi.app.data.source.local.SeriesLocalDataSource
import com.cactuvi.app.data.source.remote.LiveRemoteDataSource
import com.cactuvi.app.data.source.remote.MovieRemoteDataSource
import com.cactuvi.app.data.source.remote.SeriesRemoteDataSource
import com.cactuvi.app.utils.SourceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Hilt module providing data layer dependencies.
 *
 * Scope: SingletonComponent - lives for entire application lifetime All provided dependencies
 * are @Singleton (single instance per app)
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Provide singleton database instance. Uses existing AppDatabase.getInstance() pattern. */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    /** Provide SourceManager for accessing active source configuration. */
    @Provides
    @Singleton
    fun provideSourceManager(@ApplicationContext context: Context): SourceManager {
        return SourceManager.getInstance(context)
    }

    /**
     * Provide base URL from active source. Returns active source's server URL or default
     * placeholder.
     *
     * Note: This is re-evaluated per-injection. In Phase 2, we'll refactor to handle dynamic source
     * switching properly.
     */
    @Provides
    @ActiveSourceUrl
    fun provideActiveSourceUrl(sourceManager: SourceManager): String {
        // Use runBlocking here because we need the value synchronously for DI
        // In Phase 2, we'll refactor to handle async source loading properly
        return runBlocking { sourceManager.getActiveSource()?.server ?: "http://placeholder.local" }
    }

    /**
     * Provide XtreamApiService configured with active source's base URL.
     *
     * Singleton scope means this uses the URL from app startup. TODO Phase 2: Refactor to support
     * dynamic source switching without restarting app.
     */
    @Provides
    @Singleton
    fun provideXtreamApiService(@ActiveSourceUrl baseUrl: String): XtreamApiService {
        return ApiClient.createService(baseUrl)
    }

    // ========== LOCAL DATA SOURCES ==========

    @Provides
    @Singleton
    fun provideMovieLocalDataSource(database: AppDatabase): MovieLocalDataSource {
        return MovieLocalDataSource(database)
    }

    @Provides
    @Singleton
    fun provideSeriesLocalDataSource(database: AppDatabase): SeriesLocalDataSource {
        return SeriesLocalDataSource(database)
    }

    @Provides
    @Singleton
    fun provideLiveLocalDataSource(database: AppDatabase): LiveLocalDataSource {
        return LiveLocalDataSource(database)
    }

    // ========== REPOSITORY ==========

    // ========== REMOTE DATA SOURCES ==========

    @Provides
    @Singleton
    fun provideMovieRemoteDataSource(apiService: XtreamApiService): MovieRemoteDataSource {
        return MovieRemoteDataSource(apiService)
    }

    @Provides
    @Singleton
    fun provideSeriesRemoteDataSource(apiService: XtreamApiService): SeriesRemoteDataSource {
        return SeriesRemoteDataSource(apiService)
    }

    @Provides
    @Singleton
    fun provideLiveRemoteDataSource(apiService: XtreamApiService): LiveRemoteDataSource {
        return LiveRemoteDataSource(apiService)
    }

    // ========== REPOSITORY ==========

    /**
     * Provide ContentRepository implementation. Returns singleton instance for now (Phase 2.1).
     * TODO Phase 3: Refactor to proper constructor injection when Repository no longer uses
     * singleton pattern.
     */
    @Provides
    @Singleton
    fun provideContentRepository(
        @ApplicationContext context: Context,
    ): com.cactuvi.app.domain.repository.ContentRepository {
        return com.cactuvi.app.data.repository.ContentRepositoryImpl.getInstance(context)
    }
}
