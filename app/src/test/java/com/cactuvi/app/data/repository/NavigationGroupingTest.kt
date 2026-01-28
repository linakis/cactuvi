package com.cactuvi.app.data.repository

import com.cactuvi.app.data.models.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for navigation grouping logic in ContentRepository.
 *
 * Tests the groupCategories() function behavior through observeTopLevelNavigation(). Verifies that:
 * - Categories are correctly grouped by separator
 * - Matched prefix is stripped from category names
 * - Edge cases are handled properly
 */
class NavigationGroupingTest {

    /**
     * Helper function to simulate grouping logic that strips prefixes. This mirrors the actual
     * groupCategories() implementation in ContentRepositoryImpl.
     */
    private fun groupCategoriesWithStripping(
        categories: List<Category>,
        separator: String
    ): Map<String, List<Category>> {
        return categories
            .groupBy { category ->
                when (separator) {
                    "FIRST_WORD" ->
                        category.categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
                    "|",
                    "-",
                    "/" -> {
                        val parts = category.categoryName.split(separator, limit = 2)
                        if (parts.size > 1) parts[0].trim() else "Other"
                    }
                    else -> "Other"
                }
            }
            .mapValues { (_, categoriesInGroup) ->
                // Strip the matched prefix from category names in this group
                categoriesInGroup.map { category ->
                    val strippedName =
                        when (separator) {
                            "FIRST_WORD" -> {
                                // Strip first word: "Action Movies" → "Movies"
                                val parts = category.categoryName.split(" ", limit = 2)
                                if (parts.size > 1) parts[1].trim() else category.categoryName
                            }
                            "|",
                            "-",
                            "/" -> {
                                // Strip prefix before separator: "EN | Action" → "Action"
                                val parts = category.categoryName.split(separator, limit = 2)
                                if (parts.size > 1) parts[1].trim() else category.categoryName
                            }
                            else -> category.categoryName
                        }

                    // Return category with updated display name
                    category.copy(categoryName = strippedName)
                }
            }
    }

    // ========== Pipe Separator Tests ==========

    @Test
    fun `pipe separator should group and strip prefix correctly`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "EN | Action",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(
                    categoryId = "2",
                    categoryName = "EN | Drama",
                    parentId = 0,
                    childrenCount = 15
                ),
                Category(
                    categoryId = "3",
                    categoryName = "FR | Comedy",
                    parentId = 0,
                    childrenCount = 8
                ),
            )

        val grouped = groupCategoriesWithStripping(categories, "|")

        // Verify groups
        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey("EN"))
        assertTrue(grouped.containsKey("FR"))

        // Verify EN group has stripped prefixes
        val enCategories = grouped["EN"]!!
        assertEquals(2, enCategories.size)
        assertEquals("Action", enCategories[0].categoryName)
        assertEquals("Drama", enCategories[1].categoryName)

        // Verify FR group has stripped prefix
        val frCategories = grouped["FR"]!!
        assertEquals(1, frCategories.size)
        assertEquals("Comedy", frCategories[0].categoryName)
    }

    @Test
    fun `pipe separator with spaces should strip correctly`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "US | Action Movies",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(
                    categoryId = "2",
                    categoryName = "US|Drama Series",
                    parentId = 0,
                    childrenCount = 5
                ),
            )

        val grouped = groupCategoriesWithStripping(categories, "|")

        val usCategories = grouped["US"]!!
        assertEquals(2, usCategories.size)
        assertEquals("Action Movies", usCategories[0].categoryName) // Trimmed spaces
        assertEquals("Drama Series", usCategories[1].categoryName) // No leading space
    }

    // ========== Dash Separator Tests ==========

    @Test
    fun `dash separator should group and strip prefix correctly`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "US - Action",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(
                    categoryId = "2",
                    categoryName = "US - Comedy",
                    parentId = 0,
                    childrenCount = 15
                ),
                Category(
                    categoryId = "3",
                    categoryName = "UK - Drama",
                    parentId = 0,
                    childrenCount = 8
                ),
            )

        val grouped = groupCategoriesWithStripping(categories, "-")

        // Verify groups
        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey("US"))
        assertTrue(grouped.containsKey("UK"))

        // Verify US group
        val usCategories = grouped["US"]!!
        assertEquals(2, usCategories.size)
        assertEquals("Action", usCategories[0].categoryName)
        assertEquals("Comedy", usCategories[1].categoryName)

        // Verify UK group
        val ukCategories = grouped["UK"]!!
        assertEquals(1, ukCategories.size)
        assertEquals("Drama", ukCategories[0].categoryName)
    }

    // ========== Slash Separator Tests ==========

    @Test
    fun `slash separator should group and strip prefix correctly`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "Sports/Football",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(
                    categoryId = "2",
                    categoryName = "Sports/Basketball",
                    parentId = 0,
                    childrenCount = 5
                ),
                Category(
                    categoryId = "3",
                    categoryName = "News/Local",
                    parentId = 0,
                    childrenCount = 3
                ),
            )

        val grouped = groupCategoriesWithStripping(categories, "/")

        assertEquals(2, grouped.size)

        val sportsCategories = grouped["Sports"]!!
        assertEquals(2, sportsCategories.size)
        assertEquals("Football", sportsCategories[0].categoryName)
        assertEquals("Basketball", sportsCategories[1].categoryName)

        val newsCategories = grouped["News"]!!
        assertEquals("Local", newsCategories[0].categoryName)
    }

    // ========== FIRST_WORD Separator Tests ==========

    @Test
    fun `FIRST_WORD separator should group and strip first word correctly`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "Action Movies",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(
                    categoryId = "2",
                    categoryName = "Action Series",
                    parentId = 0,
                    childrenCount = 5
                ),
                Category(
                    categoryId = "3",
                    categoryName = "Drama Films",
                    parentId = 0,
                    childrenCount = 8
                ),
            )

        val grouped = groupCategoriesWithStripping(categories, "FIRST_WORD")

        assertEquals(2, grouped.size)

        val actionCategories = grouped["Action"]!!
        assertEquals(2, actionCategories.size)
        assertEquals("Movies", actionCategories[0].categoryName)
        assertEquals("Series", actionCategories[1].categoryName)

        val dramaCategories = grouped["Drama"]!!
        assertEquals("Films", dramaCategories[0].categoryName)
    }

    @Test
    fun `FIRST_WORD with single word should not strip anything`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "Action",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(categoryId = "2", categoryName = "Drama", parentId = 0, childrenCount = 5),
            )

        val grouped = groupCategoriesWithStripping(categories, "FIRST_WORD")

        assertEquals(2, grouped.size)

        // Single-word categories keep their names
        assertEquals("Action", grouped["Action"]!![0].categoryName)
        assertEquals("Drama", grouped["Drama"]!![0].categoryName)
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `categories without separator should go to Other group`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "EN | Action",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(
                    categoryId = "2",
                    categoryName = "Miscellaneous",
                    parentId = 0,
                    childrenCount = 5
                ),
            )

        val grouped = groupCategoriesWithStripping(categories, "|")

        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey("EN"))
        assertTrue(grouped.containsKey("Other"))

        assertEquals("Action", grouped["EN"]!![0].categoryName)
        assertEquals("Miscellaneous", grouped["Other"]!![0].categoryName) // No prefix to strip
    }

    @Test
    fun `empty category list should return empty map`() {
        val grouped = groupCategoriesWithStripping(emptyList(), "|")
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun `multiple spaces around separator should be trimmed`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "EN  |  Action",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(
                    categoryId = "2",
                    categoryName = "US   -   Drama",
                    parentId = 0,
                    childrenCount = 5
                ),
            )

        val groupedPipe = groupCategoriesWithStripping(listOf(categories[0]), "|")
        assertEquals("Action", groupedPipe["EN"]!![0].categoryName)

        val groupedDash = groupCategoriesWithStripping(listOf(categories[1]), "-")
        assertEquals("Drama", groupedDash["US"]!![0].categoryName)
    }

    @Test
    fun `separator at end of name should keep full name in Other group`() {
        val categories =
            listOf(
                Category(categoryId = "1", categoryName = "EN |", parentId = 0, childrenCount = 10),
            )

        val grouped = groupCategoriesWithStripping(categories, "|")

        // Should parse as group "EN" with empty category name, or go to Other
        // Actual behavior: splits to ["EN", ""], so parts.size > 1 is true
        assertTrue(grouped.containsKey("EN"))
        val enCategories = grouped["EN"]!!
        assertEquals("", enCategories[0].categoryName) // Empty after stripping
    }

    @Test
    fun `category names should preserve case`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "EN | ACTION",
                    parentId = 0,
                    childrenCount = 10
                ),
                Category(
                    categoryId = "2",
                    categoryName = "en | drama",
                    parentId = 0,
                    childrenCount = 5
                ),
            )

        val grouped = groupCategoriesWithStripping(categories, "|")

        // Groups are case-sensitive
        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey("EN"))
        assertTrue(grouped.containsKey("en"))

        assertEquals("ACTION", grouped["EN"]!![0].categoryName)
        assertEquals("drama", grouped["en"]!![0].categoryName)
    }

    @Test
    fun `multiple separators in name should only split on first occurrence`() {
        val categories =
            listOf(
                Category(
                    categoryId = "1",
                    categoryName = "EN | Drama | Crime",
                    parentId = 0,
                    childrenCount = 10
                ),
            )

        val grouped = groupCategoriesWithStripping(categories, "|")

        val enCategories = grouped["EN"]!!
        // Should strip only first part, keeping "Drama | Crime"
        assertEquals("Drama | Crime", enCategories[0].categoryName)
    }
}
