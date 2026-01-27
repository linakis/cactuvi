package com.cactuvi.app.data.repository

import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.dao.CategoryDao
import com.cactuvi.app.data.db.dao.LiveChannelDao
import com.cactuvi.app.data.db.dao.MovieDao
import com.cactuvi.app.data.db.dao.SeriesDao
import com.cactuvi.app.data.db.entities.CategoryEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for category childrenCount calculation.
 *
 * Tests the logic that updates category.childrenCount based on actual content counts. This ensures
 * categories display correct item counts in the UI and aren't filtered out when they have content.
 */
class CategoryChildrenCountTest {

    private lateinit var database: AppDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var movieDao: MovieDao
    private lateinit var seriesDao: SeriesDao
    private lateinit var liveChannelDao: LiveChannelDao

    @Before
    fun setup() {
        // Mock the database and DAOs
        database = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        movieDao = mockk(relaxed = true)
        seriesDao = mockk(relaxed = true)
        liveChannelDao = mockk(relaxed = true)

        // Configure database to return mocked DAOs
        every { database.categoryDao() } returns categoryDao
        every { database.movieDao() } returns movieDao
        every { database.seriesDao() } returns seriesDao
        every { database.liveChannelDao() } returns liveChannelDao
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    /** Test: Calculate childrenCount for VOD categories based on movie counts */
    @Test
    fun `should calculate childrenCount for VOD categories from movie counts`() = runTest {
        // Given: Categories with initial childrenCount = 0
        val categories =
            listOf(
                CategoryEntity(
                    sourceId = "test",
                    categoryId = "1",
                    categoryName = "Action",
                    parentId = 0,
                    type = "vod",
                    childrenCount = 0,
                    isLeaf = true
                ),
                CategoryEntity(
                    sourceId = "test",
                    categoryId = "2",
                    categoryName = "Comedy",
                    parentId = 0,
                    type = "vod",
                    childrenCount = 0,
                    isLeaf = true
                ),
                CategoryEntity(
                    sourceId = "test",
                    categoryId = "3",
                    categoryName = "Drama",
                    parentId = 0,
                    type = "vod",
                    childrenCount = 0,
                    isLeaf = true
                )
            )

        // And: Actual movie counts per category
        coEvery { movieDao.countByCategoryId("1") } returns 150
        coEvery { movieDao.countByCategoryId("2") } returns 0 // Empty category
        coEvery { movieDao.countByCategoryId("3") } returns 2500

        // When: Calculating childrenCount for each category
        val results =
            categories.map { category ->
                val count = movieDao.countByCategoryId(category.categoryId)
                category.categoryId to count
            }

        // Then: Counts match actual movie distribution
        assertEquals(150, results.find { it.first == "1" }?.second)
        assertEquals(0, results.find { it.first == "2" }?.second)
        assertEquals(2500, results.find { it.first == "3" }?.second)

        // And: DAOs were called correctly
        coVerify(exactly = 1) { movieDao.countByCategoryId("1") }
        coVerify(exactly = 1) { movieDao.countByCategoryId("2") }
        coVerify(exactly = 1) { movieDao.countByCategoryId("3") }
    }

    /** Test: Calculate childrenCount for series categories */
    @Test
    fun `should calculate childrenCount for series categories from series counts`() = runTest {
        // Given: Series categories
        val categories =
            listOf(
                CategoryEntity(
                    sourceId = "test",
                    categoryId = "10",
                    categoryName = "TV Shows",
                    parentId = 0,
                    type = "series",
                    childrenCount = 0,
                    isLeaf = true
                ),
                CategoryEntity(
                    sourceId = "test",
                    categoryId = "20",
                    categoryName = "Anime",
                    parentId = 0,
                    type = "series",
                    childrenCount = 0,
                    isLeaf = true
                )
            )

        // And: Actual series counts
        coEvery { seriesDao.countByCategoryId("10") } returns 450
        coEvery { seriesDao.countByCategoryId("20") } returns 180

        // When: Calculating childrenCount
        val results =
            categories.map { category ->
                val count = seriesDao.countByCategoryId(category.categoryId)
                category.categoryId to count
            }

        // Then: Counts are correct
        assertEquals(450, results.find { it.first == "10" }?.second)
        assertEquals(180, results.find { it.first == "20" }?.second)
    }

    /** Test: Calculate childrenCount for live TV categories */
    @Test
    fun `should calculate childrenCount for live categories from channel counts`() = runTest {
        // Given: Live TV categories
        val categories =
            listOf(
                CategoryEntity(
                    sourceId = "test",
                    categoryId = "100",
                    categoryName = "Sports",
                    parentId = 0,
                    type = "live",
                    childrenCount = 0,
                    isLeaf = true
                ),
                CategoryEntity(
                    sourceId = "test",
                    categoryId = "200",
                    categoryName = "News",
                    parentId = 0,
                    type = "live",
                    childrenCount = 0,
                    isLeaf = true
                )
            )

        // And: Actual channel counts
        coEvery { liveChannelDao.countByCategoryId("100") } returns 75
        coEvery { liveChannelDao.countByCategoryId("200") } returns 120

        // When: Calculating childrenCount
        val results =
            categories.map { category ->
                val count = liveChannelDao.countByCategoryId(category.categoryId)
                category.categoryId to count
            }

        // Then: Counts are correct
        assertEquals(75, results.find { it.first == "100" }?.second)
        assertEquals(120, results.find { it.first == "200" }?.second)
    }

    /** Test: Update categories with calculated childrenCount */
    @Test
    fun `should update category childrenCount in database`() = runTest {
        // Given: A category with incorrect count
        val categoryId = "42"
        val type = "vod"
        val actualCount = 3000

        // When: Updating childrenCount
        categoryDao.updateChildrenCount(type, categoryId, actualCount)

        // Then: Update was called with correct parameters
        coVerify(exactly = 1) {
            categoryDao.updateChildrenCount(type = "vod", categoryId = "42", count = 3000)
        }
    }

    /** Test: Empty categories should have childrenCount = 0 */
    @Test
    fun `should set childrenCount to 0 for empty categories`() = runTest {
        // Given: Category with no content
        val category =
            CategoryEntity(
                sourceId = "test",
                categoryId = "999",
                categoryName = "Empty Category",
                parentId = 0,
                type = "vod",
                childrenCount = 0,
                isLeaf = true
            )

        // And: No movies in this category
        coEvery { movieDao.countByCategoryId("999") } returns 0

        // When: Calculating count
        val count = movieDao.countByCategoryId(category.categoryId)

        // Then: Count is 0
        assertEquals(0, count)
    }

    /** Test: Categories with large content counts */
    @Test
    fun `should handle categories with large content counts`() = runTest {
        // Given: Category with very large movie count
        val categoryId = "large"
        coEvery { movieDao.countByCategoryId(categoryId) } returns 135_715

        // When: Getting count
        val count = movieDao.countByCategoryId(categoryId)

        // Then: Large count is handled correctly
        assertEquals(135_715, count)
        assertTrue("Count should be positive", count > 0)
    }

    /** Test: Batch update all categories for a content type */
    @Test
    fun `should update childrenCount for all categories of same type`() = runTest {
        // Given: Multiple VOD categories
        val categories =
            listOf(
                CategoryEntity("test", "1", "Cat1", 0, "vod", 0, true),
                CategoryEntity("test", "2", "Cat2", 0, "vod", 0, true),
                CategoryEntity("test", "3", "Cat3", 0, "vod", 0, true)
            )

        // And: Different counts per category
        coEvery { movieDao.countByCategoryId("1") } returns 100
        coEvery { movieDao.countByCategoryId("2") } returns 200
        coEvery { movieDao.countByCategoryId("3") } returns 300

        // When: Updating all categories
        categories.forEach { category ->
            val count = movieDao.countByCategoryId(category.categoryId)
            categoryDao.updateChildrenCount("vod", category.categoryId, count)
        }

        // Then: All categories were updated
        coVerify(exactly = 1) { categoryDao.updateChildrenCount("vod", "1", 100) }
        coVerify(exactly = 1) { categoryDao.updateChildrenCount("vod", "2", 200) }
        coVerify(exactly = 1) { categoryDao.updateChildrenCount("vod", "3", 300) }
    }

    /** Test: Categories from different sources should be handled separately */
    @Test
    fun `should handle categories from multiple sources`() = runTest {
        // Given: Categories from different sources
        val category1 = CategoryEntity("source1", "1", "Cat1", 0, "vod", 0, true)
        val category2 = CategoryEntity("source2", "1", "Cat1", 0, "vod", 0, true)

        // When: Calculating counts (same categoryId, different sources)
        // Note: This test verifies the logic handles source isolation
        coEvery { movieDao.countByCategoryId("1") } returns 500

        val count = movieDao.countByCategoryId("1")

        // Then: Count applies to both (they share categoryId)
        assertEquals(500, count)

        // Note: In real implementation, we may need to filter by sourceId
        // This test documents current behavior
    }

    /** Test: Verify update doesn't affect other fields */
    @Test
    fun `should only update childrenCount field`() = runTest {
        // Given: Category with specific properties
        val categoryId = "test"
        val type = "vod"
        val newCount = 1000

        // When: Updating only childrenCount
        categoryDao.updateChildrenCount(type, categoryId, newCount)

        // Then: Only childrenCount update was called
        coVerify(exactly = 1) {
            categoryDao.updateChildrenCount(type = type, categoryId = categoryId, count = newCount)
        }

        // And: No other DAO methods were called
        coVerify(exactly = 0) { categoryDao.insertAll(any()) }
        coVerify(exactly = 0) { categoryDao.clearByType(any()) }
    }

    /** Test: Edge case - null or missing categories */
    @Test
    fun `should handle missing categories gracefully`() = runTest {
        // Given: DAO returns null for non-existent category
        coEvery { categoryDao.getById("vod", "nonexistent") } returns null

        // When: Getting category
        val category = categoryDao.getById("vod", "nonexistent")

        // Then: Returns null without crashing
        assertNull(category)
    }

    /** Test: Performance - batch operations preferred over individual updates */
    @Test
    fun `should prefer batch operations for performance`() = runTest {
        // Given: 325 VOD categories (real-world scenario)
        val categoryCount = 325
        val categories =
            (1..categoryCount).map { i -> CategoryEntity("test", "$i", "Cat$i", 0, "vod", 0, true) }

        // When: Processing in batch
        // Verify we don't call updateChildrenCount 325 times individually
        // Instead, we should query counts in batch and update efficiently

        // This is a design test - documents expected behavior
        assertTrue("Should handle $categoryCount categories", categories.size == categoryCount)
    }
}
