package com.cactuvi.app.di

import android.content.Context
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
import com.cactuvi.app.mock.MockServerManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Mock flavor DataModule - provides mock server configuration.
 *
 * Overrides production DataModule to use MockWebServer on localhost:8080 instead of real API
 * endpoints.
 *
 * Note: AppDatabase and all Managers are provided by ManagerModule (in main source set). This
 * module provides flavor-specific API configuration and data sources.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Provide mock server base URL. Returns localhost:8080 where MockWebServer is running. */
    @Provides
    @ActiveSourceUrl
    fun provideActiveSourceUrl(): String {
        return "http://localhost:8080/"
    }

    /**
     * Provide XtreamApiService configured with mock server URL. Ensures MockServerManager is
     * started before creating the service.
     */
    @Provides
    @Singleton
    fun provideXtreamApiService(
        @ApplicationContext context: Context,
        @ActiveSourceUrl baseUrl: String,
    ): XtreamApiService {
        // Ensure mock server is running
        MockServerManager.getInstance().start(context)
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
