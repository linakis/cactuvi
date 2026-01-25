package com.cactuvi.app.data.source.remote

import com.cactuvi.app.data.api.XtreamApiService
import com.cactuvi.app.data.models.Category
import okhttp3.ResponseBody

/**
 * Remote data source for series. Handles all API calls related to series.
 *
 * Note: @Inject annotation will be added in Phase 1.4 (Hilt setup)
 */
class SeriesRemoteDataSource(
    private val apiService: XtreamApiService,
) {
    /** Fetch series from API. Returns streaming ResponseBody for incremental parsing. */
    suspend fun getSeries(username: String, password: String): ResponseBody {
        return apiService.getSeries(username, password)
    }

    /** Fetch series categories from API. */
    suspend fun getSeriesCategories(username: String, password: String): List<Category> {
        return apiService.getSeriesCategories(username, password)
    }
}
