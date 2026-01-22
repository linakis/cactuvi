package com.iptv.app.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.iptv.app.data.api.ApiClient
import com.iptv.app.data.api.XtreamApiService
import com.iptv.app.data.db.AppDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.iptv.app.data.db.entities.*
import com.iptv.app.data.db.mappers.*
import com.iptv.app.data.models.*
import com.iptv.app.utils.CategoryGrouper
import com.iptv.app.utils.SourceManager
import com.iptv.app.utils.PerformanceLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.withContext

class ContentRepository(
    private val sourceManager: SourceManager,
    context: Context
) {
    
    private var apiService: XtreamApiService? = null
    private val database = AppDatabase.getInstance(context)
    private val gson = Gson()
    private var currentSourceId: String? = null
    
    companion object {
        // Cache Time-To-Live (TTL) in milliseconds
        // NOTE: TTL only used for fallback safety check (7 days) - background sync handles freshness
        private const val CACHE_TTL_FALLBACK = 7 * 24 * 60 * 60 * 1000L  // 7 days
        private const val CACHE_TTL_CATEGORIES = 7 * 24 * 60 * 60 * 1000L  // 7 days
    }
    
    private suspend fun getApiService(): XtreamApiService {
        val activeSource = sourceManager.getActiveSource()
        
        // Recreate service if source changed or doesn't exist
        if (apiService == null || currentSourceId != activeSource?.id) {
            if (activeSource != null) {
                apiService = ApiClient.createService(activeSource.server)
                currentSourceId = activeSource.id
            } else {
                throw IllegalStateException("No active source configured")
            }
        }
        return apiService!!
    }
    
    private suspend fun getActiveSourceId(): String {
        return sourceManager.getActiveSource()?.id 
            ?: throw IllegalStateException("No active source configured")
    }
    
    private suspend fun getCredentials(): Triple<String, String, String> {
        val source = sourceManager.getActiveSource()
            ?: throw IllegalStateException("No active source configured")
        return Triple(source.username, source.password, source.id)
    }
    
    private fun isCacheValid(lastUpdated: Long, ttl: Long): Boolean {
        return (System.currentTimeMillis() - lastUpdated) < ttl
    }
    
    // ========== AUTHENTICATION ==========
    
    suspend fun authenticate(): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val (username, password, _) = getCredentials()
            val response = getApiService().authenticate(username, password)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== LIVE CHANNELS ==========
    
    suspend fun getLiveStreams(forceRefresh: Boolean = false): Result<List<LiveChannel>> = 
        withContext(Dispatchers.IO) {
            try {
                // Check cache metadata
                val metadata = database.cacheMetadataDao().get("live")
                
                // Return cache immediately if exists and not forcing refresh
                // Unless extremely stale (7+ days) - then force refresh for safety
                if (!forceRefresh && metadata != null) {
                    val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                    if (!isExtremelyStale) {
                        val cachedChannels = database.liveChannelDao().getAll()
                        if (cachedChannels.isNotEmpty()) {
                            return@withContext Result.success(cachedChannels.map { it.toModel() })
                        }
                    }
                }
                
                // Fetch from API
                val (username, password, sourceId) = getCredentials()
                val channels = getApiService().getLiveStreams(username, password)
                
                // Get categories for names
                val categories = getLiveCategories().getOrNull() ?: emptyList()
                val categoryMap = categories.associateBy { it.categoryId }
                
                // Convert and cache
                val entities = channels.map { channel ->
                    val categoryName = categoryMap[channel.categoryId]?.categoryName ?: ""
                    channel.categoryName = categoryName
                    channel.toEntity(sourceId, categoryName)
                }
                database.liveChannelDao().insertAll(entities)
                
                // Update cache metadata
                val newMetadata = CacheMetadataEntity(
                    contentType = "live",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = channels.size,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                
                Result.success(channels)
            } catch (e: Exception) {
                // Try returning cached data on error
                val cachedChannels = database.liveChannelDao().getAll()
                if (cachedChannels.isNotEmpty()) {
                    Result.success(cachedChannels.map { it.toModel() })
                } else {
                    Result.failure(e)
                }
            }
        }
    
    suspend fun getLiveCategories(forceRefresh: Boolean = false): Result<List<Category>> = 
        withContext(Dispatchers.IO) {
            try {
                val cached = database.categoryDao().getAllByType("live")
                
                if (!forceRefresh && cached.isNotEmpty() &&
                    isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)) {
                    return@withContext Result.success(cached.map { it.toModel() })
                }
                
                val (username, password, sourceId) = getCredentials()
                val categories = getApiService().getLiveCategories(username, password)
                
                val entities = categories.map { it.toEntity(sourceId, "live") }
                database.categoryDao().insertAll(entities)
                
                // Cache navigation tree
                cacheLiveNavigationTree(categories)
                
                Result.success(categories)
            } catch (e: Exception) {
                val cached = database.categoryDao().getAllByType("live")
                if (cached.isNotEmpty()) {
                    Result.success(cached.map { it.toModel() })
                } else {
                    Result.failure(e)
                }
            }
        }
    
    // ========== MOVIES ==========
    
    suspend fun getMovies(forceRefresh: Boolean = false): Result<List<Movie>> = 
        withContext(Dispatchers.IO) {
            val startTime = PerformanceLogger.start("Repository.getMovies")
            
            try {
                // Check cache metadata
                PerformanceLogger.logPhase("getMovies", "Checking cache metadata")
                val metadataCheckStart = PerformanceLogger.start("Metadata cache check")
                val metadata = database.cacheMetadataDao().get("movies")
                PerformanceLogger.end("Metadata cache check", metadataCheckStart, 
                    "exists=${metadata != null}, count=${metadata?.itemCount ?: 0}")
                
                // Return cache immediately if exists and not forcing refresh
                // Unless extremely stale (7+ days) - then force refresh for safety
                if (!forceRefresh && metadata != null) {
                    val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                    if (!isExtremelyStale) {
                        PerformanceLogger.logPhase("getMovies", "Loading cached data")
                        val dataLoadStart = PerformanceLogger.start("Load cached movies")
                        val cached = database.movieDao().getAll()
                        PerformanceLogger.end("Load cached movies", dataLoadStart, "count=${cached.size}")
                        
                        if (cached.isNotEmpty()) {
                            PerformanceLogger.logCacheHit("movies", "getMovies", cached.size)
                            PerformanceLogger.end("Repository.getMovies", startTime, "HIT - count=${cached.size}")
                            return@withContext Result.success(cached.map { it.toModel() })
                        }
                    }
                }
                
                // Cache miss - fetch from API
                PerformanceLogger.logCacheMiss("movies", "getMovies", if (forceRefresh) "forceRefresh" else "expired/empty")
                PerformanceLogger.logPhase("getMovies", "Fetching from API")
                val apiStart = PerformanceLogger.start("API fetch")
                val (username, password, sourceId) = getCredentials()
                val movies = getApiService().getVodStreams(username, password)
                PerformanceLogger.end("API fetch", apiStart, "count=${movies.size}")
                
                val categories = getMovieCategories().getOrNull() ?: emptyList()
                val categoryMap = categories.associateBy { it.categoryId }
                
                // Insert into database
                PerformanceLogger.logPhase("getMovies", "Inserting into DB")
                val dbInsertStart = PerformanceLogger.start("DB insert")
                val entities = movies.map { movie ->
                    val categoryName = categoryMap[movie.categoryId]?.categoryName ?: ""
                    movie.categoryName = categoryName
                    movie.toEntity(sourceId, categoryName)
                }
                database.movieDao().insertAll(entities)
                PerformanceLogger.end("DB insert", dbInsertStart, "inserted=${entities.size}")
                
                // Update cache metadata
                PerformanceLogger.logPhase("getMovies", "Updating cache metadata")
                val metadataUpdateStart = PerformanceLogger.start("Update metadata")
                val newMetadata = CacheMetadataEntity(
                    contentType = "movies",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = movies.size,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                PerformanceLogger.end("Update metadata", metadataUpdateStart)
                
                PerformanceLogger.end("Repository.getMovies", startTime, "SUCCESS - count=${movies.size}")
                Result.success(movies)
            } catch (e: Exception) {
                PerformanceLogger.log("getMovies API failed: ${e.message}")
                
                // Fallback to cache
                PerformanceLogger.logPhase("getMovies", "API failed, trying cache fallback")
                val cached = database.movieDao().getAll()
                if (cached.isNotEmpty()) {
                    PerformanceLogger.logCacheHit("movies", "getMovies_fallback", cached.size)
                    PerformanceLogger.end("Repository.getMovies", startTime, "FALLBACK - count=${cached.size}")
                    Result.success(cached.map { it.toModel() })
                } else {
                    PerformanceLogger.end("Repository.getMovies", startTime, "FAILED - no fallback")
                    Result.failure(e)
                }
            }
        }
    
    suspend fun getMovieCategories(forceRefresh: Boolean = false): Result<List<Category>> = 
        withContext(Dispatchers.IO) {
            val startTime = PerformanceLogger.start("Repository.getMovieCategories")
            
            try {
                // Check cache
                PerformanceLogger.logPhase("getMovieCategories", "Checking cache")
                val cacheCheckStart = PerformanceLogger.start("Cache check")
                val cached = database.categoryDao().getAllByType("vod")
                PerformanceLogger.end("Cache check", cacheCheckStart, "cached=${cached.size}")
                
                if (!forceRefresh && cached.isNotEmpty() &&
                    isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)) {
                    PerformanceLogger.logCacheHit("movies", "categories", cached.size)
                    PerformanceLogger.end("Repository.getMovieCategories", startTime, "HIT - count=${cached.size}")
                    return@withContext Result.success(cached.map { it.toModel() })
                }
                
                // Cache miss - fetch from API
                PerformanceLogger.logCacheMiss("movies", "categories", if (forceRefresh) "forceRefresh" else "expired/empty")
                PerformanceLogger.logPhase("getMovieCategories", "Fetching from API")
                val apiStart = PerformanceLogger.start("API fetch")
                val (username, password, sourceId) = getCredentials()
                val categories = getApiService().getVodCategories(username, password)
                PerformanceLogger.end("API fetch", apiStart, "count=${categories.size}")
                
                // Insert into database
                PerformanceLogger.logPhase("getMovieCategories", "Inserting into DB")
                val dbInsertStart = PerformanceLogger.start("DB insert")
                val entities = categories.map { it.toEntity(sourceId, "vod") }
                database.categoryDao().insertAll(entities)
                PerformanceLogger.end("DB insert", dbInsertStart, "inserted=${entities.size}")
                
                // Cache navigation tree
                PerformanceLogger.logPhase("getMovieCategories", "Caching navigation tree")
                val treeCacheStart = PerformanceLogger.start("Cache navigation tree")
                cacheVodNavigationTree(categories)
                PerformanceLogger.end("Cache navigation tree", treeCacheStart)
                
                PerformanceLogger.end("Repository.getMovieCategories", startTime, "SUCCESS - count=${categories.size}")
                Result.success(categories)
            } catch (e: Exception) {
                PerformanceLogger.log("getMovieCategories API failed: ${e.message}")
                
                // Fallback to cache
                PerformanceLogger.logPhase("getMovieCategories", "API failed, trying cache fallback")
                val cached = database.categoryDao().getAllByType("vod")
                if (cached.isNotEmpty()) {
                    PerformanceLogger.logCacheHit("movies", "categories_fallback", cached.size)
                    PerformanceLogger.end("Repository.getMovieCategories", startTime, "FALLBACK - count=${cached.size}")
                    Result.success(cached.map { it.toModel() })
                } else {
                    PerformanceLogger.end("Repository.getMovieCategories", startTime, "FAILED - no fallback")
                    Result.failure(e)
                }
            }
        }
    
    suspend fun getMovieInfo(vodId: Int): Result<MovieInfo> = withContext(Dispatchers.IO) {
        try {
            val (username, password, _) = getCredentials()
            val info = getApiService().getVodInfo(username, password, vodId = vodId)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== SERIES ==========
    
    suspend fun getSeries(forceRefresh: Boolean = false): Result<List<Series>> = 
        withContext(Dispatchers.IO) {
            try {
                // Check cache metadata
                val metadata = database.cacheMetadataDao().get("series")
                
                // Return cache immediately if exists and not forcing refresh
                // Unless extremely stale (7+ days) - then force refresh for safety
                if (!forceRefresh && metadata != null) {
                    val isExtremelyStale = !isCacheValid(metadata.lastUpdated, CACHE_TTL_FALLBACK)
                    if (!isExtremelyStale) {
                        val cached = database.seriesDao().getAll()
                        if (cached.isNotEmpty()) {
                            return@withContext Result.success(cached.map { it.toModel() })
                        }
                    }
                }
                
                val (username, password, sourceId) = getCredentials()
                val series = getApiService().getSeries(username, password)
                
                val categories = getSeriesCategories().getOrNull() ?: emptyList()
                val categoryMap = categories.associateBy { it.categoryId }
                
                val entities = series.map { s ->
                    val categoryName = categoryMap[s.categoryId]?.categoryName ?: ""
                    s.categoryName = categoryName
                    s.toEntity(sourceId, categoryName)
                }
                database.seriesDao().insertAll(entities)
                
                // Update cache metadata
                val newMetadata = CacheMetadataEntity(
                    contentType = "series",
                    lastUpdated = System.currentTimeMillis(),
                    itemCount = series.size,
                    categoryCount = categories.size
                )
                database.cacheMetadataDao().insert(newMetadata)
                
                Result.success(series)
            } catch (e: Exception) {
                val cached = database.seriesDao().getAll()
                if (cached.isNotEmpty()) {
                    Result.success(cached.map { it.toModel() })
                } else {
                    Result.failure(e)
                }
            }
        }
    
    suspend fun getSeriesCategories(forceRefresh: Boolean = false): Result<List<Category>> = 
        withContext(Dispatchers.IO) {
            try {
                val cached = database.categoryDao().getAllByType("series")
                
                if (!forceRefresh && cached.isNotEmpty() &&
                    isCacheValid(cached.first().lastUpdated, CACHE_TTL_CATEGORIES)) {
                    return@withContext Result.success(cached.map { it.toModel() })
                }
                
                val (username, password, sourceId) = getCredentials()
                val categories = getApiService().getSeriesCategories(username, password)
                
                val entities = categories.map { it.toEntity(sourceId, "series") }
                database.categoryDao().insertAll(entities)
                
                // Cache navigation tree
                cacheSeriesNavigationTree(categories)
                
                Result.success(categories)
            } catch (e: Exception) {
                val cached = database.categoryDao().getAllByType("series")
                if (cached.isNotEmpty()) {
                    Result.success(cached.map { it.toModel() })
                } else {
                    Result.failure(e)
                }
            }
        }
    
    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfo> = withContext(Dispatchers.IO) {
        try {
            val (username, password, _) = getCredentials()
            val info = getApiService().getSeriesInfo(username, password, seriesId = seriesId)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== FAVORITES ==========
    
    suspend fun addToFavorites(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        rating: String?,
        categoryName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val favorite = FavoriteEntity(
                id = "${contentType}_${contentId}",
                contentId = contentId,
                contentType = contentType,
                contentName = contentName,
                posterUrl = posterUrl,
                rating = rating,
                categoryName = categoryName
            )
            database.favoriteDao().insert(favorite)
            
            // Update entity favorite status
            when (contentType) {
                "movie" -> database.movieDao().updateFavorite(contentId.toInt(), true)
                "series" -> database.seriesDao().updateFavorite(contentId.toInt(), true)
                "live_channel" -> database.liveChannelDao().updateFavorite(contentId.toInt(), true)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun removeFromFavorites(contentId: String, contentType: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val id = "${contentType}_${contentId}"
                database.favoriteDao().deleteById(id)
                
                // Update entity favorite status
                when (contentType) {
                    "movie" -> database.movieDao().updateFavorite(contentId.toInt(), false)
                    "series" -> database.seriesDao().updateFavorite(contentId.toInt(), false)
                    "live_channel" -> database.liveChannelDao().updateFavorite(contentId.toInt(), false)
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getFavorites(contentType: String? = null): Result<List<FavoriteEntity>> = 
        withContext(Dispatchers.IO) {
            try {
                val favorites = if (contentType != null) {
                    database.favoriteDao().getByType(contentType)
                } else {
                    database.favoriteDao().getAll()
                }
                Result.success(favorites)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun isFavorite(contentId: String, contentType: String): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            try {
                val id = "${contentType}_${contentId}"
                Result.success(database.favoriteDao().isFavorite(id))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    // ========== WATCH HISTORY ==========
    
    suspend fun updateWatchProgress(
        contentId: String,
        contentType: String,
        contentName: String,
        posterUrl: String?,
        resumePosition: Long,
        duration: Long,
        seriesId: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isCompleted = (resumePosition.toDouble() / duration) > 0.9
            
            val history = WatchHistoryEntity(
                contentId = contentId,
                contentType = contentType,
                contentName = contentName,
                posterUrl = posterUrl,
                resumePosition = resumePosition,
                duration = duration,
                lastWatched = System.currentTimeMillis(),
                isCompleted = isCompleted,
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber
            )
            
            database.watchHistoryDao().insert(history)
            
            // Update movie resume position if applicable
            if (contentType == "movie") {
                database.movieDao().updateResumePosition(contentId.toInt(), resumePosition)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getWatchHistory(limit: Int = 20): Result<List<WatchHistoryEntity>> = 
        withContext(Dispatchers.IO) {
            try {
                val history = database.watchHistoryDao().getIncomplete(limit)
                Result.success(history)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun clearWatchHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.watchHistoryDao().clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteWatchHistoryItem(contentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val item = database.watchHistoryDao().getByContentId(contentId, "movie") 
                ?: database.watchHistoryDao().getByContentId(contentId, "series")
                ?: database.watchHistoryDao().getByContentId(contentId, "live_channel")
            
            item?.let {
                database.watchHistoryDao().delete(it)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== PAGING METHODS ==========
    
    fun getLiveStreamsPaged(categoryId: String? = null): Flow<PagingData<LiveChannel>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.liveChannelDao().getByCategoryIdPaged(categoryId)
            } else {
                database.liveChannelDao().getAllPaged()
            }
        }
        
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 10
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow.flowMap { pagingData ->
            pagingData.map { entity -> entity.toModel() }
        }
    }
    
    fun getMoviesPaged(categoryId: String? = null): Flow<PagingData<Movie>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.movieDao().getByCategoryIdPaged(categoryId)
            } else {
                database.movieDao().getAllPaged()
            }
        }
        
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 10
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow.flowMap { pagingData ->
            pagingData.map { entity -> entity.toModel() }
        }
    }
    
    fun getSeriesPaged(categoryId: String? = null): Flow<PagingData<Series>> {
        val pagingSourceFactory = {
            if (categoryId != null) {
                database.seriesDao().getByCategoryIdPaged(categoryId)
            } else {
                database.seriesDao().getAllPaged()
            }
        }
        
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 10
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow.flowMap { pagingData ->
            pagingData.map { entity -> entity.toModel() }
        }
    }
    
    // ========== NAVIGATION TREE CACHING ==========
    
    private suspend fun cacheVodNavigationTree(categories: List<Category>, separator: String = "-") {
        val tree = CategoryGrouper.buildVodNavigationTree(categories)
        val sourceId = getActiveSourceId()
        
        val entities = tree.groups.map { group ->
            NavigationGroupEntity(
                sourceId = sourceId,
                type = "vod",
                groupName = group.name,
                categoryIdsJson = gson.toJson(group.categories.map { it.categoryId }),
                separator = separator
            )
        }
        
        database.navigationGroupDao().deleteByType("vod")
        database.navigationGroupDao().insertAll(entities)
    }
    
    private suspend fun cacheSeriesNavigationTree(categories: List<Category>, separator: String = "FIRST_WORD") {
        val tree = CategoryGrouper.buildSeriesNavigationTree(categories)
        val sourceId = getActiveSourceId()
        
        val entities = tree.groups.map { group ->
            NavigationGroupEntity(
                sourceId = sourceId,
                type = "series",
                groupName = group.name,
                categoryIdsJson = gson.toJson(group.categories.map { it.categoryId }),
                separator = separator
            )
        }
        
        database.navigationGroupDao().deleteByType("series")
        database.navigationGroupDao().insertAll(entities)
    }
    
    private suspend fun cacheLiveNavigationTree(categories: List<Category>, separator: String = "|") {
        val tree = CategoryGrouper.buildLiveNavigationTree(categories)
        val sourceId = getActiveSourceId()
        
        val entities = tree.groups.map { group ->
            NavigationGroupEntity(
                sourceId = sourceId,
                type = "live",
                groupName = group.name,
                categoryIdsJson = gson.toJson(group.categories.map { it.categoryId }),
                separator = separator
            )
        }
        
        database.navigationGroupDao().deleteByType("live")
        database.navigationGroupDao().insertAll(entities)
    }
    
    suspend fun getCachedVodNavigationTree(): CategoryGrouper.NavigationTree? = withContext(Dispatchers.IO) {
        val startTime = PerformanceLogger.start("Repository.getCachedVodNavigationTree")
        
        try {
            // Load navigation group entities
            PerformanceLogger.logPhase("getCachedVodNavigationTree", "Loading navigation groups")
            val entityLoadStart = PerformanceLogger.start("Load navigation entities")
            val entities = database.navigationGroupDao().getByType("vod")
            PerformanceLogger.end("Load navigation entities", entityLoadStart, "count=${entities.size}")
            
            if (entities.isEmpty()) {
                PerformanceLogger.logCacheMiss("movies", "navigationTree", "no entities")
                PerformanceLogger.end("Repository.getCachedVodNavigationTree", startTime, "MISS - empty")
                return@withContext null
            }
            
            // Check TTL
            val firstEntity = entities.first()
            if (!isCacheValid(firstEntity.lastUpdated, CACHE_TTL_CATEGORIES)) {
                PerformanceLogger.logCacheMiss("movies", "navigationTree", "expired TTL")
                PerformanceLogger.end("Repository.getCachedVodNavigationTree", startTime, "MISS - expired")
                return@withContext null
            }
            
            // Get all VOD categories
            PerformanceLogger.logPhase("getCachedVodNavigationTree", "Loading categories")
            val categoryLoadStart = PerformanceLogger.start("Load categories")
            val allCategories = database.categoryDao().getAllByType("vod").map { it.toModel() }
            PerformanceLogger.end("Load categories", categoryLoadStart, "count=${allCategories.size}")
            
            // Reconstruct navigation tree from cached groups
            PerformanceLogger.logPhase("getCachedVodNavigationTree", "Deserializing JSON and building tree")
            val deserializeStart = PerformanceLogger.start("Deserialize and build tree")
            val groups = entities.map { entity ->
                val type = object : TypeToken<List<String>>() {}.type
                val categoryIds: List<String> = gson.fromJson(entity.categoryIdsJson, type)
                val groupCategories = allCategories.filter { it.categoryId in categoryIds }
                CategoryGrouper.GroupNode(entity.groupName, groupCategories)
            }
            PerformanceLogger.end("Deserialize and build tree", deserializeStart, "groups=${groups.size}")
            
            val tree = CategoryGrouper.NavigationTree(groups)
            PerformanceLogger.logCacheHit("movies", "navigationTree", groups.size)
            PerformanceLogger.end("Repository.getCachedVodNavigationTree", startTime, "HIT - groups=${groups.size}")
            tree
        } catch (e: Exception) {
            PerformanceLogger.log("getCachedVodNavigationTree failed: ${e.message}")
            PerformanceLogger.end("Repository.getCachedVodNavigationTree", startTime, "ERROR")
            null
        }
    }
    
    suspend fun getCachedSeriesNavigationTree(): CategoryGrouper.NavigationTree? = withContext(Dispatchers.IO) {
        try {
            val entities = database.navigationGroupDao().getByType("series")
            if (entities.isEmpty()) return@withContext null
            
            // Check TTL
            val firstEntity = entities.first()
            if (!isCacheValid(firstEntity.lastUpdated, CACHE_TTL_CATEGORIES)) {
                return@withContext null
            }
            
            // Get all series categories
            val allCategories = database.categoryDao().getAllByType("series").map { it.toModel() }
            
            // Reconstruct navigation tree from cached groups
            val groups = entities.map { entity ->
                val type = object : TypeToken<List<String>>() {}.type
                val categoryIds: List<String> = gson.fromJson(entity.categoryIdsJson, type)
                val groupCategories = allCategories.filter { it.categoryId in categoryIds }
                CategoryGrouper.GroupNode(entity.groupName, groupCategories)
            }
            
            CategoryGrouper.NavigationTree(groups)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getCachedLiveNavigationTree(): CategoryGrouper.NavigationTree? = withContext(Dispatchers.IO) {
        try {
            val entities = database.navigationGroupDao().getByType("live")
            if (entities.isEmpty()) return@withContext null
            
            // Check TTL
            val firstEntity = entities.first()
            if (!isCacheValid(firstEntity.lastUpdated, CACHE_TTL_CATEGORIES)) {
                return@withContext null
            }
            
            // Get all live categories
            val allCategories = database.categoryDao().getAllByType("live").map { it.toModel() }
            
            // Reconstruct navigation tree from cached groups
            val groups = entities.map { entity ->
                val type = object : TypeToken<List<String>>() {}.type
                val categoryIds: List<String> = gson.fromJson(entity.categoryIdsJson, type)
                val groupCategories = allCategories.filter { it.categoryId in categoryIds }
                CategoryGrouper.GroupNode(entity.groupName, groupCategories)
            }
            
            CategoryGrouper.NavigationTree(groups)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun invalidateVodNavigationTree() = withContext(Dispatchers.IO) {
        database.navigationGroupDao().deleteByType("vod")
    }
    
    suspend fun invalidateSeriesNavigationTree() = withContext(Dispatchers.IO) {
        database.navigationGroupDao().deleteByType("series")
    }
    
    suspend fun invalidateLiveNavigationTree() = withContext(Dispatchers.IO) {
        database.navigationGroupDao().deleteByType("live")
    }
    
    suspend fun invalidateAllNavigationTrees() = withContext(Dispatchers.IO) {
        database.navigationGroupDao().clear()
    }
    
    // Aliases for consistency
    suspend fun invalidateMovieNavigationCache() = invalidateVodNavigationTree()
    suspend fun invalidateSeriesNavigationCache() = invalidateSeriesNavigationTree()
    suspend fun invalidateLiveNavigationCache() = invalidateLiveNavigationTree()
    
    // ========== CACHE MANAGEMENT ==========
    
    suspend fun clearAllCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.liveChannelDao().clearAll()
            database.movieDao().clearAll()
            database.seriesDao().clearAll()
            database.categoryDao().clearAll()
            invalidateAllNavigationTrees()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clear all cached data for a specific source
     */
    suspend fun clearSourceCache(sourceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Clear all content for this source
            // TODO: Add sourceId-specific filters to DAOs
            database.liveChannelDao().clearAll()
            database.movieDao().clearAll()
            database.seriesDao().clearAll()
            database.categoryDao().clearAll()
            database.navigationGroupDao().clear()
            database.cacheMetadataDao().deleteAll()
            
            // Force recreate API service for new source
            apiService = null
            currentSourceId = null
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
