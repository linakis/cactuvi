package com.cactuvi.app.di

import com.cactuvi.app.data.api.ApiClient
import com.cactuvi.app.data.api.XtreamApiService
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.repository.ContentRepositoryImpl
import com.cactuvi.app.data.source.local.LiveLocalDataSource
import com.cactuvi.app.data.source.local.MovieLocalDataSource
import com.cactuvi.app.data.source.local.SeriesLocalDataSource
import com.cactuvi.app.data.source.remote.LiveRemoteDataSource
import com.cactuvi.app.data.source.remote.MovieRemoteDataSource
import com.cactuvi.app.data.source.remote.SeriesRemoteDataSource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.utils.SourceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Hilt module providing data layer dependencies for production flavor.
 *
 * Scope: SingletonComponent - lives for entire application lifetime.
 *
 * Note: AppDatabase and all Managers are provided by ManagerModule (in main source set). This
 * module provides flavor-specific API configuration and data sources.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

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
}

/**
 * Bindings module for interface-to-implementation bindings. Uses @Binds for zero-overhead binding
 * (no object allocation at runtime).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    /**
     * Bind ContentRepository interface to ContentRepositoryImpl. ContentRepositoryImpl is annotated
     * with @Singleton @Inject constructor, so Hilt can instantiate it automatically.
     */
    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository
}
