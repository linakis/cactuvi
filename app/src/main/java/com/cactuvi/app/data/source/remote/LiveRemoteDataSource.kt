package com.cactuvi.app.data.source.remote

import com.cactuvi.app.data.api.XtreamApiService
import com.cactuvi.app.data.models.Category
import okhttp3.ResponseBody

/**
 * Remote data source for live channels.
 * Handles all API calls related to live TV.
 * 
 * Note: @Inject annotation will be added in Phase 1.4 (Hilt setup)
 */
class LiveRemoteDataSource(
    private val apiService: XtreamApiService
) {
    /**
     * Fetch live streams from API.
     * Returns streaming ResponseBody for incremental parsing.
     */
    suspend fun getLiveStreams(username: String, password: String): ResponseBody {
        return apiService.getLiveStreams(username, password)
    }
    
    /**
     * Fetch live categories from API.
     */
    suspend fun getLiveCategories(username: String, password: String): List<Category> {
        return apiService.getLiveCategories(username, password)
    }
}
