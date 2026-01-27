package com.cactuvi.app.mock

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility class to load mock API responses from assets. Maps action query parameters to JSON files.
 */
object MockResponseProvider {

    private const val TAG = "MockResponseProvider"
    private const val MOCK_RESPONSES_DIR = "mock_responses"

    /** Action parameter to JSON file mapping */
    private val actionToFileMap =
        mapOf(
            "get_vod_streams" to "get_vod_streams.json",
            "get_live_streams" to "get_live_streams.json",
            "get_series" to "get_series.json",
            "get_vod_categories" to "get_vod_categories.json",
            "get_live_categories" to "get_live_categories.json",
            "get_series_categories" to "get_series_categories.json",
            "get_account_info" to "get_account_info.json",
            "get_vod_info" to "get_vod_info.json",
            "get_series_info" to "get_series_info.json",
        )

    /**
     * Load mock response JSON for given action parameter.
     *
     * @param context Android context to access assets
     * @param action The action query parameter (e.g., "get_vod_streams")
     * @return JSON string content from assets, or empty array if not found
     */
    fun loadMockResponse(context: Context, action: String?): String {
        if (action == null) {
            Log.w(TAG, "Action parameter is null, returning empty array")
            return "[]"
        }

        val fileName = actionToFileMap[action]
        if (fileName == null) {
            Log.w(TAG, "Unknown action: $action, returning empty array")
            return "[]"
        }

        return try {
            val filePath = "$MOCK_RESPONSES_DIR/$fileName"
            Log.d(TAG, "Loading mock response: $filePath")

            val inputStream = context.assets.open(filePath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.use { it.readText() }

            Log.d(TAG, "Loaded ${content.length} bytes from $fileName")
            content
        } catch (e: Exception) {
            Log.e(TAG, "Error loading mock response for action: $action", e)
            "[]"
        }
    }

    /** Check if given action is supported by mock server. */
    fun isActionSupported(action: String?): Boolean {
        return action != null && actionToFileMap.containsKey(action)
    }

    /** Get list of all supported actions. */
    fun getSupportedActions(): Set<String> {
        return actionToFileMap.keys
    }
}
