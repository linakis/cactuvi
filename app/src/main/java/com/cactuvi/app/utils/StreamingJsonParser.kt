package com.cactuvi.app.utils

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStreamReader
import okhttp3.ResponseBody

/**
 * Utility for streaming large JSON arrays without loading entire response into memory. Parses items
 * one-by-one and processes them in batches to minimize memory pressure.
 */
object StreamingJsonParser {

    /**
     * Stream parse a JSON array and process items in batches.
     *
     * @param responseBody The streaming response from Retrofit
     * @param itemClass The class type to deserialize each item to
     * @param batchSize Number of items to collect before calling processBatch
     * @param processBatch Callback invoked for each batch of items
     * @param onProgress Optional callback for progress updates (called every 10,000 items)
     * @return Total number of items processed
     */
    suspend fun <T> parseArrayInBatches(
        responseBody: ResponseBody,
        itemClass: Class<T>,
        batchSize: Int = 500,
        processBatch: suspend (List<T>) -> Unit,
        onProgress: ((Int) -> Unit)? = null,
    ): Int {
        val gson = Gson()
        var totalCount = 0
        val currentBatch = mutableListOf<T>()
        val progressInterval = 10000 // Report progress every 10k items
        var lastProgressReport = 0

        responseBody.use { body ->
            val reader = JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))

            // Start reading the array
            reader.beginArray()

            while (reader.hasNext()) {
                // Parse single item without loading entire array
                val item = gson.fromJson<T>(reader, itemClass)
                currentBatch.add(item)
                totalCount++

                // Report progress every N items
                if (onProgress != null && totalCount - lastProgressReport >= progressInterval) {
                    onProgress(totalCount)
                    lastProgressReport = totalCount
                }

                // Process batch when it reaches batchSize
                if (currentBatch.size >= batchSize) {
                    processBatch(currentBatch.toList())
                    currentBatch.clear()
                }
            }

            // Process remaining items
            if (currentBatch.isNotEmpty()) {
                processBatch(currentBatch.toList())
                currentBatch.clear()
            }

            reader.endArray()
            reader.close()
        }

        return totalCount
    }

    /**
     * Stream parse a JSON array and collect all items (for smaller responses). Use this for
     * categories and other small datasets.
     */
    fun <T> parseArrayFull(responseBody: ResponseBody, itemClass: Class<T>): List<T> {
        val gson = Gson()
        val items = mutableListOf<T>()

        responseBody.use { body ->
            val reader = JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))

            reader.beginArray()
            while (reader.hasNext()) {
                val item = gson.fromJson<T>(reader, itemClass)
                items.add(item)
            }
            reader.endArray()
            reader.close()
        }

        return items
    }

    /**
     * Check if response is a valid JSON array without fully parsing. Useful for error detection.
     */
    fun isValidJsonArray(responseBody: ResponseBody): Boolean {
        return try {
            responseBody.use { body ->
                val reader = JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
                reader.peek() == JsonToken.BEGIN_ARRAY
            }
        } catch (e: Exception) {
            false
        }
    }
}
