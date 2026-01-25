package com.cactuvi.app.utils

import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.ContentFilterSettings

/**
 * Utility class for building hierarchical navigation trees from categories using parent_id
 * relationships. Supports arbitrary nesting depth and optional grouping by name separators.
 */
object CategoryTreeBuilder {

    /**
     * Represents a hierarchical navigation tree with support for arbitrary nesting.
     *
     * @property roots Top-level nodes (parent_id = 0 or no parent found)
     */
    data class NavigationTree(
        val roots: List<CategoryNode>,
    ) {
        /** Total number of categories in the tree (all levels) */
        val totalCategories: Int
            get() = roots.sumOf { it.totalDescendants + 1 }

        /** Get all leaf nodes (categories with no children) */
        val leafNodes: List<CategoryNode>
            get() = roots.flatMap { it.getAllLeafNodes() }

        /** Check if navigation should skip to content (only 1 leaf category) */
        val shouldSkipToContent: Boolean
            get() = leafNodes.size == 1

        /** Get the single leaf node if only one exists */
        val singleLeafNode: CategoryNode?
            get() = if (leafNodes.size == 1) leafNodes.first() else null
    }

    /**
     * Represents a node in the category tree (supports arbitrary nesting).
     *
     * @property category The category data
     * @property children Child nodes (nested categories)
     * @property depth Depth in tree (0 = root, 1 = first level, etc.)
     * @property isLeaf True if this node has no children
     */
    data class CategoryNode(
        val category: Category,
        val children: List<CategoryNode>,
        val depth: Int,
    ) {
        val isLeaf: Boolean
            get() = children.isEmpty()

        /** Total number of descendants (recursive count of all child nodes) */
        val totalDescendants: Int
            get() = children.size + children.sumOf { it.totalDescendants }

        /** Get all leaf nodes from this subtree */
        fun getAllLeafNodes(): List<CategoryNode> {
            return if (isLeaf) {
                listOf(this)
            } else {
                children.flatMap { it.getAllLeafNodes() }
            }
        }

        /** Find child node by category ID */
        fun findChild(categoryId: String): CategoryNode? {
            return children.find { it.category.categoryId == categoryId }
        }
    }

    /**
     * Build hierarchical navigation tree from flat category list using parent_id relationships.
     *
     * Algorithm:
     * 1. Optional: Create group layer by splitting category names (backward compatibility)
     * 2. Build parent-child map using parentId field
     * 3. Identify root nodes (parentId = 0 or parent not found)
     * 4. Recursively build subtrees from roots
     * 5. Apply filtering (blacklist/whitelist)
     *
     * @param categories Flat list of categories from API
     * @param groupingEnabled If true, creates group layer from name separator (legacy behavior)
     * @param separator Group separator character ("|", "-", "FIRST_WORD")
     * @param hiddenCategories Set of category names to hide/show
     * @param filterMode Blacklist (hide) or whitelist (show only)
     * @return NavigationTree with hierarchical structure
     */
    fun buildNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "|",
        hiddenCategories: Set<String> = emptySet(),
        filterMode: ContentFilterSettings.FilterMode = ContentFilterSettings.FilterMode.BLACKLIST,
    ): NavigationTree {
        val startTime = PerformanceLogger.start("CategoryTreeBuilder.buildNavigationTree")
        PerformanceLogger.log(
            "Input: ${categories.size} categories, grouping=$groupingEnabled, separator='$separator', hiddenCategories=${hiddenCategories.size}",
        )

        // Step 1: Apply category-level filtering first
        val filteredCategories =
            if (hiddenCategories.isEmpty()) {
                categories
            } else {
                when (filterMode) {
                    ContentFilterSettings.FilterMode.BLACKLIST ->
                        categories.filter { it.categoryName !in hiddenCategories }
                    ContentFilterSettings.FilterMode.WHITELIST ->
                        categories.filter { it.categoryName in hiddenCategories }
                }
            }

        if (filteredCategories.isEmpty()) {
            PerformanceLogger.end(
                "CategoryTreeBuilder.buildNavigationTree",
                startTime,
                "EMPTY - all categories filtered out",
            )
            return NavigationTree(emptyList())
        }

        // Step 2: Build category ID map for quick lookups
        val categoryMap = filteredCategories.associateBy { it.categoryId }

        // Step 3: Build parent-child map
        val childrenMap = mutableMapOf<String, MutableList<Category>>()
        val rootCategories = mutableListOf<Category>()

        for (category in filteredCategories) {
            val parentId = category.parentId.toString()
            if (category.parentId == 0 || categoryMap[parentId] == null) {
                // Root category (parentId = 0 or parent not found)
                rootCategories.add(category)
            } else {
                // Child category
                childrenMap.getOrPut(parentId) { mutableListOf() }.add(category)
            }
        }

        PerformanceLogger.log(
            "Tree structure: ${rootCategories.size} roots, ${childrenMap.size} parent categories with children",
        )

        // Step 4: Build tree recursively
        val roots =
            rootCategories
                .map { buildSubtree(it, childrenMap, depth = 0) }
                .sortedBy { it.category.categoryName }

        // Step 5: Optional grouping layer (backward compatibility with CategoryGrouper)
        val finalTree =
            if (groupingEnabled && separator != "NONE") {
                applyGroupingLayer(roots, separator)
            } else {
                NavigationTree(roots)
            }

        PerformanceLogger.end(
            "CategoryTreeBuilder.buildNavigationTree",
            startTime,
            "SUCCESS - ${finalTree.roots.size} roots, ${finalTree.totalCategories} total categories",
        )
        return finalTree
    }

    /**
     * Recursively build subtree from a category node.
     *
     * @param category Current category
     * @param childrenMap Map of parent_id -> child categories
     * @param depth Current depth in tree
     * @param visited Set of visited category IDs (for cycle detection)
     * @return CategoryNode with all descendants
     */
    private fun buildSubtree(
        category: Category,
        childrenMap: Map<String, List<Category>>,
        depth: Int,
        visited: MutableSet<String> = mutableSetOf(),
    ): CategoryNode {
        // Cycle detection
        if (category.categoryId in visited) {
            PerformanceLogger.log(
                "WARNING: Circular reference detected for category '${category.categoryName}' (id=${category.categoryId})",
            )
            return CategoryNode(category, emptyList(), depth)
        }
        visited.add(category.categoryId)

        // Find children
        val children =
            childrenMap[category.categoryId]
                ?.map { child -> buildSubtree(child, childrenMap, depth + 1, visited) }
                ?.sortedBy { it.category.categoryName } ?: emptyList()

        return CategoryNode(category, children, depth)
    }

    /**
     * Apply grouping layer on top of existing tree (for backward compatibility). Creates virtual
     * "group" categories by splitting category names. When grouping is enabled, category names are
     * automatically stripped of their group prefix for cleaner display.
     *
     * Example: "UK | Sports" becomes "Sports" under the "UK" group.
     *
     * @param roots Existing root nodes
     * @param separator Group separator
     * @return NavigationTree with group layer added and prefixes stripped
     */
    private fun applyGroupingLayer(roots: List<CategoryNode>, separator: String): NavigationTree {
        // Group roots by extracted group name
        val grouped = roots.groupBy { extractGroupName(it.category.categoryName, separator) }

        // Create virtual group nodes
        val groupNodes =
            grouped
                .map { (groupName, nodes) ->
                    // Create a virtual category for the group
                    val virtualCategory =
                        Category(
                            categoryId = "GROUP_$groupName",
                            categoryName = groupName,
                            parentId = 0,
                        )

                    // Strip group prefix from child category names (root level only)
                    val strippedNodes =
                        nodes.map { node ->
                            val strippedCategory =
                                node.category.copy(
                                    categoryName =
                                        stripGroupPrefix(node.category.categoryName, separator)
                                )
                            // Copy node with updated category, preserving all children as-is
                            node.copy(category = strippedCategory)
                        }

                    CategoryNode(virtualCategory, strippedNodes, depth = 0)
                }
                .sortedBy { it.category.categoryName }

        return NavigationTree(groupNodes)
    }

    /** Extract group name from category name using specified separator (from CategoryGrouper) */
    private fun extractGroupName(categoryName: String, separator: String): String {
        return when (separator) {
            "FIRST_WORD" -> {
                // Extract first word
                categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
            }
            "|",
            "-",
            "/" -> {
                // Extract prefix before separator
                val parts = categoryName.split(separator)
                if (parts.size > 1) {
                    parts[0].trim()
                } else {
                    // Fallback: extract first word
                    categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
                }
            }
            else -> {
                // Unknown separator, extract first word as fallback
                categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
            }
        }
    }

    /**
     * Strip group prefix from category name using separator. Only strips the first occurrence
     * (group prefix), keeping any additional separators in the name.
     *
     * Examples:
     * - "UK | Sports" with "|" → "Sports"
     * - "UK | Sports | Football" with "|" → "Sports | Football" (keeps rest)
     * - "Action - Thriller" with "-" → "Thriller"
     * - "Action Movies" with "FIRST_WORD" → "Movies"
     * - "Standalone" (no separator) → "Standalone" (unchanged)
     * - "UK | " (empty after strip) → "UK | " (fallback to original)
     *
     * @param categoryName Original category name
     * @param separator Group separator
     * @return Name with group prefix stripped, or original if invalid
     */
    internal fun stripGroupPrefix(categoryName: String, separator: String): String {
        val stripped =
            when (separator) {
                "FIRST_WORD" -> {
                    // Remove first word only
                    val parts = categoryName.split(" ", limit = 2)
                    if (parts.size > 1) parts[1].trim() else categoryName
                }
                "|",
                "-",
                "/" -> {
                    // Remove prefix before first separator occurrence
                    val parts = categoryName.split(separator, limit = 2)
                    if (parts.size > 1) {
                        parts[1].trim()
                    } else {
                        // No separator found, return original
                        categoryName
                    }
                }
                else -> categoryName // Unknown separator, don't strip
            }

        // Fallback: if result is empty/whitespace, return original
        return if (stripped.isBlank()) categoryName else stripped
    }

    /**
     * Flatten tree to list of categories (for debugging/testing).
     *
     * @param tree Navigation tree
     * @return Flat list of all categories in tree
     */
    fun flattenTree(tree: NavigationTree): List<Category> {
        fun flatten(node: CategoryNode): List<Category> {
            return listOf(node.category) + node.children.flatMap { flatten(it) }
        }
        return tree.roots.flatMap { flatten(it) }
    }

    /**
     * Convert CategoryTreeBuilder.NavigationTree to CategoryGrouper.NavigationTree for backward
     * compatibility. Assumes the tree has a grouping layer (groups at root level with categories as
     * children).
     *
     * This is a temporary bridge method to allow incremental migration from CategoryGrouper to
     * CategoryTreeBuilder.
     *
     * @param tree CategoryTreeBuilder navigation tree (with grouping enabled)
     * @return CategoryGrouper-compatible NavigationTree
     */
    fun toGroupedNavigationTree(tree: NavigationTree): CategoryGrouper.NavigationTree {
        val groups =
            tree.roots.map { groupNode ->
                // Extract group name from virtual category
                val groupName = groupNode.category.categoryName

                // Get all leaf categories from this group's subtree
                val categories =
                    groupNode.children.map { categoryNode ->
                        // For now, just take the immediate children as categories
                        // (assuming 2-level hierarchy: Group -> Category)
                        categoryNode.category
                    }

                CategoryGrouper.GroupNode(name = groupName, categories = categories)
            }

        return CategoryGrouper.NavigationTree(groups)
    }
}
