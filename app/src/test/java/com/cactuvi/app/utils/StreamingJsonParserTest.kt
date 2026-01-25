package com.cactuvi.app.utils

import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.data.models.LiveChannel
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for StreamingJsonParser.
 * 
 * Tests cover:
 * - Batch processing with different batch sizes
 * - Progress callbacks
 * - Empty arrays
 * - Invalid JSON handling
 * - Memory-efficient streaming (no full array load)
 * - Error recovery
 */
class StreamingJsonParserTest {
    
    // ========== BATCH PROCESSING TESTS ==========
    
    @Test
    fun `parseArrayInBatches processes items in correct batch sizes`() = runTest {
        val json = """
            [
                {"stream_id": 1, "name": "Movie 1", "category_id": "100"},
                {"stream_id": 2, "name": "Movie 2", "category_id": "100"},
                {"stream_id": 3, "name": "Movie 3", "category_id": "100"},
                {"stream_id": 4, "name": "Movie 4", "category_id": "100"},
                {"stream_id": 5, "name": "Movie 5", "category_id": "100"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        val batches = mutableListOf<List<Movie>>()
        
        val totalCount = StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 2,
            processBatch = { batch ->
                batches.add(batch)
            }
        )
        
        assertEquals(5, totalCount)
        assertEquals(3, batches.size) // 2 items, 2 items, 1 item
        assertEquals(2, batches[0].size)
        assertEquals(2, batches[1].size)
        assertEquals(1, batches[2].size)
        assertEquals(1, batches[0][0].streamId)
        assertEquals("Movie 1", batches[0][0].name)
    }
    
    @Test
    fun `parseArrayInBatches handles single batch when size less than batchSize`() = runTest {
        val json = """
            [
                {"stream_id": 1, "name": "Movie 1", "category_id": "100"},
                {"stream_id": 2, "name": "Movie 2", "category_id": "100"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        val batches = mutableListOf<List<Movie>>()
        
        val totalCount = StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 10,
            processBatch = { batch ->
                batches.add(batch)
            }
        )
        
        assertEquals(2, totalCount)
        assertEquals(1, batches.size)
        assertEquals(2, batches[0].size)
    }
    
    @Test
    fun `parseArrayInBatches processes exact multiple of batch size`() = runTest {
        val json = """
            [
                {"stream_id": 1, "name": "Movie 1", "category_id": "100"},
                {"stream_id": 2, "name": "Movie 2", "category_id": "100"},
                {"stream_id": 3, "name": "Movie 3", "category_id": "100"},
                {"stream_id": 4, "name": "Movie 4", "category_id": "100"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        val batches = mutableListOf<List<Movie>>()
        
        val totalCount = StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 2,
            processBatch = { batch ->
                batches.add(batch)
            }
        )
        
        assertEquals(4, totalCount)
        assertEquals(2, batches.size)
        assertEquals(2, batches[0].size)
        assertEquals(2, batches[1].size)
    }
    
    // ========== PROGRESS CALLBACK TESTS ==========
    
    @Test
    fun `parseArrayInBatches calls progress callback every 10k items`() = runTest {
        // Generate JSON with 25,000 items
        val items = (1..25000).joinToString(",\n") { i ->
            """{"stream_id": $i, "name": "Movie $i", "category_id": "100"}"""
        }
        val json = "[$items]"
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        val progressCalls = mutableListOf<Int>()
        
        val totalCount = StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 500,
            processBatch = { _ -> },
            onProgress = { count ->
                progressCalls.add(count)
            }
        )
        
        assertEquals(25000, totalCount)
        assertTrue("Should have progress at 10k", progressCalls.contains(10000))
        assertTrue("Should have progress at 20k", progressCalls.contains(20000))
    }
    
    @Test
    fun `parseArrayInBatches does not call progress when null`() = runTest {
        val json = """
            [
                {"stream_id": 1, "name": "Movie 1", "category_id": "100"},
                {"stream_id": 2, "name": "Movie 2", "category_id": "100"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        // Should not throw exception when onProgress is null
        val totalCount = StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 1,
            processBatch = { _ -> },
            onProgress = null
        )
        
        assertEquals(2, totalCount)
    }
    
    // ========== EMPTY ARRAY TESTS ==========
    
    @Test
    fun `parseArrayInBatches handles empty array`() = runTest {
        val json = "[]"
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        val batches = mutableListOf<List<Movie>>()
        
        val totalCount = StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 10,
            processBatch = { batch ->
                batches.add(batch)
            }
        )
        
        assertEquals(0, totalCount)
        assertEquals(0, batches.size)
    }
    
    @Test
    fun `parseArrayFull handles empty array`() {
        val json = "[]"
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        val items = StreamingJsonParser.parseArrayFull(
            responseBody = responseBody,
            itemClass = Movie::class.java
        )
        
        assertEquals(0, items.size)
    }
    
    // ========== FULL ARRAY PARSING TESTS ==========
    
    @Test
    fun `parseArrayFull returns all items in single list`() {
        val json = """
            [
                {"stream_id": 1, "name": "Movie 1", "category_id": "100"},
                {"stream_id": 2, "name": "Movie 2", "category_id": "100"},
                {"stream_id": 3, "name": "Movie 3", "category_id": "100"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        val items = StreamingJsonParser.parseArrayFull(
            responseBody = responseBody,
            itemClass = Movie::class.java
        )
        
        assertEquals(3, items.size)
        assertEquals(1, items[0].streamId)
        assertEquals("Movie 1", items[0].name)
        assertEquals(2, items[1].streamId)
        assertEquals(3, items[2].streamId)
    }
    
    @Test
    fun `parseArrayFull works with Series data`() {
        val json = """
            [
                {"series_id": 100, "name": "Series 1", "category_id": "200"},
                {"series_id": 101, "name": "Series 2", "category_id": "200"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        val items = StreamingJsonParser.parseArrayFull(
            responseBody = responseBody,
            itemClass = Series::class.java
        )
        
        assertEquals(2, items.size)
        assertEquals(100, items[0].seriesId)
        assertEquals("Series 1", items[0].name)
    }
    
    @Test
    fun `parseArrayFull works with LiveChannel data`() {
        val json = """
            [
                {"stream_id": 1000, "name": "Channel 1", "category_id": "300"},
                {"stream_id": 1001, "name": "Channel 2", "category_id": "300"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        val items = StreamingJsonParser.parseArrayFull(
            responseBody = responseBody,
            itemClass = LiveChannel::class.java
        )
        
        assertEquals(2, items.size)
        assertEquals(1000, items[0].streamId)
        assertEquals("Channel 1", items[0].name)
    }
    
    // ========== VALIDATION TESTS ==========
    
    @Test
    fun `isValidJsonArray returns true for valid array`() {
        val json = """
            [
                {"stream_id": 1, "name": "Movie 1", "category_id": "100"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        val isValid = StreamingJsonParser.isValidJsonArray(responseBody)
        
        assertTrue(isValid)
    }
    
    @Test
    fun `isValidJsonArray returns false for object`() {
        val json = """{"stream_id": 1, "name": "Movie 1"}"""
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        val isValid = StreamingJsonParser.isValidJsonArray(responseBody)
        
        assertFalse(isValid)
    }
    
    @Test
    fun `isValidJsonArray returns false for invalid JSON`() {
        val json = """not valid json"""
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        val isValid = StreamingJsonParser.isValidJsonArray(responseBody)
        
        assertFalse(isValid)
    }
    
    @Test
    fun `isValidJsonArray returns true for empty array`() {
        val json = "[]"
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        val isValid = StreamingJsonParser.isValidJsonArray(responseBody)
        
        assertTrue(isValid)
    }
    
    // ========== ERROR HANDLING TESTS ==========
    
    @Test
    fun `parseArrayInBatches throws on malformed JSON`() = runTest {
        val json = """[{"stream_id": 1, "name": "Movie 1",]""" // Missing closing brace
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        try {
            StreamingJsonParser.parseArrayInBatches(
                responseBody = responseBody,
                itemClass = Movie::class.java,
                batchSize = 10,
                processBatch = { _ -> }
            )
            fail("Should throw exception for malformed JSON")
        } catch (e: Exception) {
            // Expected
            assertTrue(true)
        }
    }
    
    @Test
    fun `parseArrayFull throws on malformed JSON`() {
        val json = """[{"stream_id": 1,]""" // Malformed
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        
        try {
            StreamingJsonParser.parseArrayFull(
                responseBody = responseBody,
                itemClass = Movie::class.java
            )
            fail("Should throw exception for malformed JSON")
        } catch (e: Exception) {
            // Expected
            assertTrue(true)
        }
    }
    
    // ========== DATA INTEGRITY TESTS ==========
    
    @Test
    fun `parseArrayInBatches preserves all items across batches`() = runTest {
        val json = """
            [
                {"stream_id": 1, "name": "Movie 1", "category_id": "100"},
                {"stream_id": 2, "name": "Movie 2", "category_id": "101"},
                {"stream_id": 3, "name": "Movie 3", "category_id": "102"},
                {"stream_id": 4, "name": "Movie 4", "category_id": "103"},
                {"stream_id": 5, "name": "Movie 5", "category_id": "104"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        val allItems = mutableListOf<Movie>()
        
        StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 2,
            processBatch = { batch ->
                allItems.addAll(batch)
            }
        )
        
        assertEquals(5, allItems.size)
        assertEquals(1, allItems[0].streamId)
        assertEquals(2, allItems[1].streamId)
        assertEquals(3, allItems[2].streamId)
        assertEquals(4, allItems[3].streamId)
        assertEquals(5, allItems[4].streamId)
        assertEquals("100", allItems[0].categoryId)
        assertEquals("101", allItems[1].categoryId)
    }
    
    @Test
    fun `parseArrayInBatches handles large batch sizes without memory issues`() = runTest {
        // Generate 1000 items
        val items = (1..1000).joinToString(",\n") { i ->
            """{"stream_id": $i, "name": "Movie $i", "category_id": "100"}"""
        }
        val json = "[$items]"
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        var processedCount = 0
        
        val totalCount = StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 999, // Large batch size
            processBatch = { batch ->
                processedCount += batch.size
            }
        )
        
        assertEquals(1000, totalCount)
        assertEquals(1000, processedCount)
    }
    
    // ========== SUSPEND FUNCTION TESTS ==========
    
    @Test
    fun `parseArrayInBatches works with suspend processBatch`() = runTest {
        val json = """
            [
                {"stream_id": 1, "name": "Movie 1", "category_id": "100"},
                {"stream_id": 2, "name": "Movie 2", "category_id": "100"}
            ]
        """.trimIndent()
        
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        var batchCount = 0
        
        StreamingJsonParser.parseArrayInBatches(
            responseBody = responseBody,
            itemClass = Movie::class.java,
            batchSize = 1,
            processBatch = { batch ->
                // Simulate async work
                kotlinx.coroutines.delay(10)
                batchCount++
            }
        )
        
        assertEquals(2, batchCount)
    }
}
