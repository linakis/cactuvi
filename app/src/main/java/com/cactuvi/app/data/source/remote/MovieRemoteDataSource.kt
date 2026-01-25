package com.cactuvi.app.data.source.remote

import com.cactuvi.app.data.api.XtreamApiService
import com.cactuvi.app.data.models.Category
import okhttp3.ResponseBody

/**
 * Remote data source for movies (VOD).
 * Handles all API calls related to movies.
 * 
 * Note: @Inject annotation will be added in Phase 1.4 (Hilt setup)
 */
class MovieRemoteDataSource(
    private val apiService: XtreamApiService
) {
    /**
     * Fetch VOD streams from API.
     * Returns streaming ResponseBody for incremental parsing.
     */
    suspend fun getVodStreams(username: String, password: String): ResponseBody {
        return apiService.getVodStreams(username, password)
    }
    
    /**
     * Fetch VOD categories from API.
     */
    suspend fun getVodCategories(username: String, password: String): List<Category> {
        return apiService.getVodCategories(username, password)
    }
}
