package com.cactuvi.app.mock

import android.content.Context
import android.util.Log
import java.io.InputStream
import okio.Buffer
import okio.buffer
import okio.source

/**
 * Utility class to load mock API responses from assets. Maps action query parameters to JSON files.
 * Uses streaming (okio Buffer) for large files to avoid OutOfMemoryError.
 */
object MockResponseProvider {

    private const val TAG = "MockResponseProvider"
    private const val MOCK_RESPONSES_DIR = "mock_responses"

    /** Buffer size for streaming large files (64KB) */
    private const val BUFFER_SIZE = 64 * 1024L

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
            "get_server_info" to "get_server_info.json",
            "get_short_epg" to "get_short_epg.json",
            "get_simple_data_table" to "get_simple_data_table.json",
        )

    /**
     * Load mock response as a streaming Buffer for given action parameter. This is memory-efficient
     * for large JSON files (50MB+).
     *
     * @param context Android context to access assets
     * @param action The action query parameter (e.g., "get_vod_streams")
     * @return Buffer containing JSON content, or empty array buffer if not found
     */
    fun loadMockResponseAsBuffer(context: Context, action: String?): Buffer {
        if (action == null) {
            Log.w(TAG, "Action parameter is null, returning empty array")
            return Buffer().writeUtf8("[]")
        }

        val fileName = actionToFileMap[action]
        if (fileName == null) {
            Log.w(TAG, "Unknown action: $action, returning empty array")
            return Buffer().writeUtf8("[]")
        }

        return try {
            val filePath = "$MOCK_RESPONSES_DIR/$fileName"
            Log.d(TAG, "Streaming mock response: $filePath")

            val inputStream: InputStream = context.assets.open(filePath)
            val buffer = Buffer()

            // Stream content in chunks to avoid loading entire file into memory
            inputStream.source().buffer().use { source ->
                var totalBytes = 0L
                while (!source.exhausted()) {
                    val bytesRead = source.read(buffer, BUFFER_SIZE)
                    if (bytesRead > 0) {
                        totalBytes += bytesRead
                    }
                }
                Log.d(TAG, "Streamed $totalBytes bytes from $fileName")
            }

            buffer
        } catch (e: Exception) {
            Log.e(TAG, "Error loading mock response for action: $action", e)
            Buffer().writeUtf8("[]")
        }
    }

    /**
     * Get the file size for an action's mock response (for Content-Length header).
     *
     * @param context Android context to access assets
     * @param action The action query parameter
     * @return File size in bytes, or -1 if unknown
     */
    fun getResponseSize(context: Context, action: String?): Long {
        if (action == null) return 2L // "[]"

        val fileName = actionToFileMap[action] ?: return 2L

        return try {
            val filePath = "$MOCK_RESPONSES_DIR/$fileName"
            context.assets.openFd(filePath).use { it.length }
        } catch (e: Exception) {
            // AssetFileDescriptor not available for compressed assets
            -1L
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
