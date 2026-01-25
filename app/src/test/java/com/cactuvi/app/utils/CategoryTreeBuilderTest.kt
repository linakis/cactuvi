package com.cactuvi.app.utils

import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.ContentFilterSettings
import org.junit.Assert.*
import org.junit.Test

class CategoryTreeBuilderTest {

    // ========== TEST DATA ==========

    private val flatCategoriesNoParent =
        listOf(
            Category("1", "Action", 0),
            Category("2", "Drama", 0),
            Category("3", "Comedy", 0),
        )

    private val nestedCategories =
        listOf(
            Category("1", "Action", 0), // Root
            Category("2", "Action > Sci-Fi", 1), // Child of Action
            Category("3", "Action > Thriller", 1), // Child of Action
            Category("4", "Drama", 0), // Root
            Category("5", "Drama > Romance", 4), // Child of Drama
            Category("6", "Drama > Romance > Classic", 5), // Child of Romance (depth 2)
        )

    private val deeplyNested =
        listOf(
            Category("1", "Level 0", 0),
            Category("2", "Level 1", 1),
            Category("3", "Level 2", 2),
            Category("4", "Level 3", 3),
            Category("5", "Level 4", 4),
            Category("6", "Level 5", 5),
        )

    private val orphanedCategories =
        listOf(
            Category("1", "Root", 0),
            Category("2", "Child of Root", 1),
            Category("3", "Orphan (parent 999 missing)", 999), // Parent doesn't exist
        )

    private val circularCategories =
        listOf(
            Category("1", "Category 1", 2), // Points to Category 2
            Category("2", "Category 2", 1), // Points to Category 1 (circular)
            Category("3", "Root", 0),
        )

    private val groupedByPipe =
        listOf(
            Category("1", "ENGLISH | Action", 0),
            Category("2", "ENGLISH | Drama", 0),
            Category("3", "SPANISH | Comedy", 0),
            Category("4", "SPANISH | News", 0),
        )

    // ========== BASIC STRUCTURE TESTS ==========

    @Test
    fun `flat categories with no parentId builds single level`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(flatCategoriesNoParent, groupingEnabled = false)

        assertEquals(3, tree.roots.size)
        assertTrue(tree.roots.all { it.isLeaf })
        assertTrue(tree.roots.all { it.depth == 0 })
        assertEquals(3, tree.totalCategories)
    }

    @Test
    fun `categories with parent_id equals 0 are roots`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(nestedCategories, groupingEnabled = false)

        val rootCategories = tree.roots.map { it.category.categoryName }
        assertTrue(rootCategories.contains("Action"))
        assertTrue(rootCategories.contains("Drama"))
        assertEquals(2, tree.roots.size)
    }

    @Test
    fun `nested categories build correct hierarchy`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(nestedCategories, groupingEnabled = false)

        // Find Action node
        val actionNode = tree.roots.find { it.category.categoryName == "Action" }
        assertNotNull(actionNode)
        assertEquals(2, actionNode!!.children.size)
        assertTrue(actionNode.children.any { it.category.categoryName == "Action > Sci-Fi" })
        assertTrue(actionNode.children.any { it.category.categoryName == "Action > Thriller" })

        // Find Drama node
        val dramaNode = tree.roots.find { it.category.categoryName == "Drama" }
        assertNotNull(dramaNode)
        assertEquals(1, dramaNode!!.children.size)

        // Check Drama > Romance > Classic (3 levels deep)
        val romanceNode = dramaNode.children.first()
        assertEquals("Drama > Romance", romanceNode.category.categoryName)
        assertEquals(1, romanceNode.children.size)
        assertEquals(
            "Drama > Romance > Classic",
            romanceNode.children.first().category.categoryName
        )
        assertEquals(2, romanceNode.children.first().depth)
    }

    @Test
    fun `deep nesting 5 plus levels works correctly`() {
        val tree = CategoryTreeBuilder.buildNavigationTree(deeplyNested, groupingEnabled = false)

        assertEquals(1, tree.roots.size)

        var currentNode = tree.roots.first()
        assertEquals("Level 0", currentNode.category.categoryName)
        assertEquals(0, currentNode.depth)

        // Traverse down the tree
        for (expectedDepth in 1..5) {
            assertEquals(1, currentNode.children.size)
            currentNode = currentNode.children.first()
            assertEquals("Level $expectedDepth", currentNode.category.categoryName)
            assertEquals(expectedDepth, currentNode.depth)
        }

        // Last node should be a leaf
        assertTrue(currentNode.isLeaf)
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    fun `orphaned categories parent not found are treated as roots`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(orphanedCategories, groupingEnabled = false)

        assertEquals(2, tree.roots.size) // Root + Orphan
        val rootNames = tree.roots.map { it.category.categoryName }
        assertTrue(rootNames.contains("Root"))
        assertTrue(rootNames.contains("Orphan (parent 999 missing)"))
    }

    @Test
    fun `circular references are detected and handled`() {
        // Should not crash, cycles should be broken
        val tree =
            CategoryTreeBuilder.buildNavigationTree(circularCategories, groupingEnabled = false)

        // All categories should be treated as roots since they form cycles
        assertNotNull(tree)
        // At minimum, we should have the root category
        assertTrue(tree.roots.any { it.category.categoryName == "Root" })
    }

    @Test
    fun `empty category list returns empty tree`() {
        val tree = CategoryTreeBuilder.buildNavigationTree(emptyList(), groupingEnabled = false)

        assertEquals(0, tree.roots.size)
        assertEquals(0, tree.totalCategories)
        assertTrue(tree.leafNodes.isEmpty())
    }

    // ========== GROUPING TESTS ==========

    @Test
    fun `grouping enabled creates group layer first`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                groupedByPipe,
                groupingEnabled = true,
                separator = "|"
            )

        // Should have 2 groups: ENGLISH, SPANISH
        assertEquals(2, tree.roots.size)
        val groupNames = tree.roots.map { it.category.categoryName }.toSet()
        assertTrue(groupNames.contains("ENGLISH"))
        assertTrue(groupNames.contains("SPANISH"))

        // Check ENGLISH group has 2 categories
        val englishGroup = tree.roots.find { it.category.categoryName == "ENGLISH" }
        assertNotNull(englishGroup)
        assertEquals(2, englishGroup!!.children.size)
    }

    @Test
    fun `grouping disabled skips group layer`() {
        val tree = CategoryTreeBuilder.buildNavigationTree(groupedByPipe, groupingEnabled = false)

        // Should have 4 root categories (no grouping)
        assertEquals(4, tree.roots.size)
        assertEquals(4, tree.totalCategories)
    }

    @Test
    fun `grouping with FIRST_WORD separator groups by first word`() {
        val categories =
            listOf(
                Category("1", "Action Movies", 0),
                Category("2", "Action Series", 0),
                Category("3", "Drama Movies", 0),
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "FIRST_WORD"
            )

        // Should have 2 groups: Action, Drama
        assertEquals(2, tree.roots.size)
        val groupNames = tree.roots.map { it.category.categoryName }.toSet()
        assertTrue(groupNames.contains("Action"))
        assertTrue(groupNames.contains("Drama"))
    }

    // ========== LEAF NODE TESTS ==========

    @Test
    fun `isLeaf flag correctly identifies leaf nodes`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(nestedCategories, groupingEnabled = false)

        // Root nodes should not be leaves
        assertFalse(tree.roots.first().isLeaf)

        // Leaf nodes
        val leafNodes = tree.leafNodes
        assertEquals(3, leafNodes.size) // Sci-Fi, Thriller, Classic

        leafNodes.forEach { node -> assertTrue(node.isLeaf) }
    }

    @Test
    fun `getAllLeafNodes returns all leaf nodes recursively`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(nestedCategories, groupingEnabled = false)

        val allLeafs = tree.leafNodes
        val leafNames = allLeafs.map { it.category.categoryName }

        assertTrue(leafNames.contains("Action > Sci-Fi"))
        assertTrue(leafNames.contains("Action > Thriller"))
        assertTrue(leafNames.contains("Drama > Romance > Classic"))
    }

    @Test
    fun `shouldSkipToContent is true when only one leaf exists`() {
        val singleLeaf = listOf(Category("1", "Only Category", 0))
        val tree = CategoryTreeBuilder.buildNavigationTree(singleLeaf, groupingEnabled = false)

        assertTrue(tree.shouldSkipToContent)
        assertEquals("Only Category", tree.singleLeafNode?.category?.categoryName)
    }

    // ========== FILTERING TESTS ==========

    @Test
    fun `blacklist filtering hides specified categories`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                flatCategoriesNoParent,
                groupingEnabled = false,
                hiddenCategories = setOf("Drama"),
                filterMode = ContentFilterSettings.FilterMode.BLACKLIST,
            )

        assertEquals(2, tree.roots.size)
        val names = tree.roots.map { it.category.categoryName }
        assertTrue(names.contains("Action"))
        assertTrue(names.contains("Comedy"))
        assertFalse(names.contains("Drama"))
    }

    @Test
    fun `whitelist filtering shows only specified categories`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                flatCategoriesNoParent,
                groupingEnabled = false,
                hiddenCategories = setOf("Action", "Drama"),
                filterMode = ContentFilterSettings.FilterMode.WHITELIST,
            )

        assertEquals(2, tree.roots.size)
        val names = tree.roots.map { it.category.categoryName }
        assertTrue(names.contains("Action"))
        assertTrue(names.contains("Drama"))
        assertFalse(names.contains("Comedy"))
    }

    @Test
    fun `filtering with empty set returns all categories`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                flatCategoriesNoParent,
                groupingEnabled = false,
                hiddenCategories = emptySet(),
            )

        assertEquals(3, tree.roots.size)
    }

    // ========== UTILITY TESTS ==========

    @Test
    fun `flattenTree returns all categories in tree`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(nestedCategories, groupingEnabled = false)
        val flattened = CategoryTreeBuilder.flattenTree(tree)

        assertEquals(6, flattened.size)
        val names = flattened.map { it.categoryName }
        assertTrue(names.contains("Action"))
        assertTrue(names.contains("Drama > Romance > Classic"))
    }

    @Test
    fun `totalCategories counts all nodes in tree`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(nestedCategories, groupingEnabled = false)
        assertEquals(6, tree.totalCategories)
    }

    @Test
    fun `totalDescendants counts all children recursively`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(nestedCategories, groupingEnabled = false)

        val actionNode = tree.roots.find { it.category.categoryName == "Action" }
        assertEquals(2, actionNode!!.totalDescendants)

        val dramaNode = tree.roots.find { it.category.categoryName == "Drama" }
        assertEquals(2, dramaNode!!.totalDescendants) // Romance + Classic
    }

    @Test
    fun `findChild returns correct child node`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(nestedCategories, groupingEnabled = false)

        val actionNode = tree.roots.find { it.category.categoryName == "Action" }
        val sciFiNode = actionNode!!.findChild("2")

        assertNotNull(sciFiNode)
        assertEquals("Action > Sci-Fi", sciFiNode!!.category.categoryName)
    }

    // ========== PREFIX STRIPPING TESTS ==========

    @Test
    fun `grouping with pipe strips prefix from root categories`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                groupedByPipe,
                groupingEnabled = true,
                separator = "|"
            )

        val englishGroup = tree.roots.find { it.category.categoryName == "ENGLISH" }
        assertNotNull(englishGroup)

        val categoryNames = englishGroup!!.children.map { it.category.categoryName }
        assertTrue(categoryNames.contains("Action"))
        assertTrue(categoryNames.contains("Drama"))
        assertFalse(categoryNames.any { it.contains("ENGLISH |") })
    }

    @Test
    fun `grouping with dash strips prefix from root categories`() {
        val categories =
            listOf(
                Category("1", "Action - Movies", 0),
                Category("2", "Action - Series", 0),
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "-"
            )

        val actionGroup = tree.roots.find { it.category.categoryName == "Action" }
        assertNotNull(actionGroup)

        val categoryNames = actionGroup!!.children.map { it.category.categoryName }
        assertTrue(categoryNames.contains("Movies"))
        assertTrue(categoryNames.contains("Series"))
    }

    @Test
    fun `grouping with FIRST_WORD strips first word from root categories`() {
        val categories =
            listOf(
                Category("1", "Action Movies", 0),
                Category("2", "Action Series", 0),
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "FIRST_WORD"
            )

        val actionGroup = tree.roots.find { it.category.categoryName == "Action" }
        assertNotNull(actionGroup)

        val categoryNames = actionGroup!!.children.map { it.category.categoryName }
        assertTrue(categoryNames.contains("Movies"))
        assertTrue(categoryNames.contains("Series"))
    }

    @Test
    fun `prefix stripping keeps multiple separators in name`() {
        val categories =
            listOf(
                Category("1", "UK | Sports | Football", 0),
                Category("2", "UK | Sports | Basketball", 0),
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "|"
            )

        val ukGroup = tree.roots.find { it.category.categoryName == "UK" }
        assertNotNull(ukGroup)

        val categoryNames = ukGroup!!.children.map { it.category.categoryName }
        assertTrue(categoryNames.contains("Sports | Football"))
        assertTrue(categoryNames.contains("Sports | Basketball"))
    }

    @Test
    fun `prefix stripping handles categories without separator`() {
        val categories =
            listOf(
                Category("1", "StandaloneCategory", 0),
                Category("2", "AnotherOne", 0),
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "|"
            )

        // Categories without separator should be grouped by first word
        // and names should remain unchanged (no prefix to strip)
        assertTrue(tree.roots.isNotEmpty())
        val allCategoryNames =
            tree.roots.flatMap { it.children.map { child -> child.category.categoryName } }
        assertTrue(allCategoryNames.contains("StandaloneCategory"))
        assertTrue(allCategoryNames.contains("AnotherOne"))
    }

    @Test
    fun `prefix stripping preserves empty results by keeping original name`() {
        val categories =
            listOf(
                Category("1", "UK | ", 0), // Empty after strip
                Category("2", "UK | Sports", 0),
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "|"
            )

        val ukGroup = tree.roots.find { it.category.categoryName == "UK" }
        assertNotNull(ukGroup)

        val categoryNames = ukGroup!!.children.map { it.category.categoryName }
        // Empty result should fallback to original name
        assertTrue(categoryNames.contains("UK | "))
        assertTrue(categoryNames.contains("Sports"))
    }

    @Test
    fun `prefix stripping trims whitespace after stripping`() {
        val categories =
            listOf(
                Category("1", "UK |   Sports", 0), // Extra spaces
                Category("2", "UK |News", 0), // No space after separator
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "|"
            )

        val ukGroup = tree.roots.find { it.category.categoryName == "UK" }
        assertNotNull(ukGroup)

        val categoryNames = ukGroup!!.children.map { it.category.categoryName }
        assertTrue(categoryNames.contains("Sports"))
        assertTrue(categoryNames.contains("News"))
    }

    @Test
    fun `prefix stripping only affects root level not nested children`() {
        val categories =
            listOf(
                Category("1", "UK | Sports", 0), // Root
                Category("2", "UK | Sports | Football", 1), // Child of Sports
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "|"
            )

        val ukGroup = tree.roots.find { it.category.categoryName == "UK" }
        assertNotNull(ukGroup)

        // Root level: stripped
        val rootCategory = ukGroup!!.children.first()
        assertEquals("Sports", rootCategory.category.categoryName)

        // Nested child: should keep full name (NOT stripped)
        assertEquals(1, rootCategory.children.size)
        assertEquals("UK | Sports | Football", rootCategory.children.first().category.categoryName)
    }

    @Test
    fun `prefix stripping preserves category IDs`() {
        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                groupedByPipe,
                groupingEnabled = true,
                separator = "|"
            )

        val englishGroup = tree.roots.find { it.category.categoryName == "ENGLISH" }
        assertNotNull(englishGroup)

        // Original category IDs should be preserved
        val categoryIds = englishGroup!!.children.map { it.category.categoryId }.toSet()
        assertTrue(categoryIds.contains("1"))
        assertTrue(categoryIds.contains("2"))
    }

    @Test
    fun `prefix stripping works with all separator types`() {
        val categoriesPipe = listOf(Category("1", "UK | Sports", 0))
        val categoriesDash = listOf(Category("2", "UK - Sports", 0))
        val categoriesSlash = listOf(Category("3", "UK / Sports", 0))
        val categoriesWord = listOf(Category("4", "UK Sports", 0))

        val treePipe =
            CategoryTreeBuilder.buildNavigationTree(
                categoriesPipe,
                groupingEnabled = true,
                separator = "|"
            )
        val treeDash =
            CategoryTreeBuilder.buildNavigationTree(
                categoriesDash,
                groupingEnabled = true,
                separator = "-"
            )
        val treeSlash =
            CategoryTreeBuilder.buildNavigationTree(
                categoriesSlash,
                groupingEnabled = true,
                separator = "/"
            )
        val treeWord =
            CategoryTreeBuilder.buildNavigationTree(
                categoriesWord,
                groupingEnabled = true,
                separator = "FIRST_WORD"
            )

        assertEquals("Sports", treePipe.roots.first().children.first().category.categoryName)
        assertEquals("Sports", treeDash.roots.first().children.first().category.categoryName)
        assertEquals("Sports", treeSlash.roots.first().children.first().category.categoryName)
        assertEquals("Sports", treeWord.roots.first().children.first().category.categoryName)
    }

    // ========== MIXED SCENARIO TESTS ==========

    @Test
    fun `nested categories with grouping and filtering`() {
        val categories =
            listOf(
                Category("1", "ENGLISH | Action", 0),
                Category("2", "ENGLISH | Action > Sci-Fi", 1),
                Category("3", "SPANISH | Drama", 0),
                Category("4", "SPANISH | Comedy", 0),
            )

        val tree =
            CategoryTreeBuilder.buildNavigationTree(
                categories,
                groupingEnabled = true,
                separator = "|",
                hiddenCategories = setOf("SPANISH | Comedy"),
                filterMode = ContentFilterSettings.FilterMode.BLACKLIST,
            )

        // Should have 2 groups: ENGLISH, SPANISH
        assertEquals(2, tree.roots.size)

        // SPANISH group should only have Drama (Comedy filtered out)
        val spanishGroup = tree.roots.find { it.category.categoryName == "SPANISH" }
        assertNotNull(spanishGroup)
        assertEquals(1, spanishGroup!!.children.size)
        assertEquals("Drama", spanishGroup.children.first().category.categoryName)

        // ENGLISH group should have Action with Sci-Fi child
        val englishGroup = tree.roots.find { it.category.categoryName == "ENGLISH" }
        assertNotNull(englishGroup)
        assertEquals(1, englishGroup!!.children.size)

        val actionNode = englishGroup.children.first()
        assertEquals("Action", actionNode.category.categoryName)
        assertEquals(1, actionNode.children.size)
        assertEquals("ENGLISH | Action > Sci-Fi", actionNode.children.first().category.categoryName)
    }
}
