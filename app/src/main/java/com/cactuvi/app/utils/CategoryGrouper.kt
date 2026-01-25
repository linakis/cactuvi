package com.cactuvi.app.utils

import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.ContentFilterSettings

/**
 * Utility class for building tree-based navigation from categories
 */
object CategoryGrouper {
    
    /**
     * Represents a navigation tree node (group -> categories)
     */
    data class NavigationTree(
        val groups: List<GroupNode>
    ) {
        fun findGroup(groupName: String): GroupNode? {
            return groups.find { it.name == groupName }
        }
        
        /**
         * Check if navigation should skip groups level (only 1 group exists)
         */
        val shouldSkipGroups: Boolean
            get() = groups.size == 1
        
        /**
         * Check if navigation should skip categories level within a group
         * (only 1 category in the group)
         */
        fun shouldSkipCategories(groupName: String): Boolean {
            val group = findGroup(groupName) ?: return false
            return group.categories.size == 1
        }
        
        /**
         * Check if navigation should skip both groups and categories levels
         * (only 1 group with only 1 category)
         */
        val shouldSkipBothLevels: Boolean
            get() = groups.size == 1 && groups.firstOrNull()?.categories?.size == 1
        
        /**
         * Get the single group if only one exists, null otherwise
         */
        val singleGroup: GroupNode?
            get() = if (groups.size == 1) groups.first() else null
        
        /**
         * Get the single category from a group if only one exists, null otherwise
         */
        fun getSingleCategory(groupName: String): Category? {
            val group = findGroup(groupName) ?: return null
            return if (group.categories.size == 1) group.categories.first() else null
        }
    }
    
    data class GroupNode(
        val name: String,
        val categories: List<Category>
    ) {
        val count: Int get() = categories.size
    }
    
    /**
     * Build navigation tree for live TV categories with hierarchical filtering
     */
    fun buildLiveNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "|",
        hiddenGroups: Set<String> = emptySet(),
        hiddenCategories: Set<String> = emptySet(),
        filterMode: ContentFilterSettings.FilterMode = ContentFilterSettings.FilterMode.BLACKLIST
    ): NavigationTree {
        if (!groupingEnabled) {
            // No grouping - filter only by categories
            val filteredCategories = filterCategoriesByName(categories, hiddenCategories, filterMode)
            return NavigationTree(listOf(GroupNode("All Categories", filteredCategories)))
        }
        
        val grouped = categories
            .groupBy { category -> extractGroupName(category.categoryName, separator) }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        
        return filterNavigationTreeHierarchical(NavigationTree(grouped), hiddenGroups, hiddenCategories, filterMode)
    }
    
    /**
     * Build navigation tree for live TV categories with custom separator and filtering (backward compatibility)
     */
    fun buildLiveNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "|",
        hiddenItems: Set<String> = emptySet(),
        filterMode: ContentFilterSettings.FilterMode = ContentFilterSettings.FilterMode.BLACKLIST
    ): NavigationTree {
        if (!groupingEnabled) {
            // No grouping - return all categories in single group
            val filteredCategories = filterCategories(categories, hiddenItems, filterMode)
            return NavigationTree(listOf(GroupNode("All Categories", filteredCategories)))
        }
        
        val grouped = categories
            .groupBy { category -> extractGroupName(category.categoryName, separator) }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        
        return filterNavigationTree(NavigationTree(grouped), hiddenItems, filterMode)
    }
    
    /**
     * Build navigation tree for VOD categories with hierarchical filtering
     */
    fun buildVodNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "-",
        hiddenGroups: Set<String> = emptySet(),
        hiddenCategories: Set<String> = emptySet(),
        filterMode: ContentFilterSettings.FilterMode = ContentFilterSettings.FilterMode.BLACKLIST
    ): NavigationTree {
        val startTime = PerformanceLogger.start("CategoryGrouper.buildVodNavigationTree")
        PerformanceLogger.log("Input: ${categories.size} categories, grouping=$groupingEnabled, hiddenGroups=${hiddenGroups.size}, hiddenCategories=${hiddenCategories.size}")
        
        if (!groupingEnabled) {
            // No grouping - filter only by categories
            val filteredCategories = filterCategoriesByName(categories, hiddenCategories, filterMode)
            PerformanceLogger.end("CategoryGrouper.buildVodNavigationTree", startTime, 
                "NO_GROUPING - 1 group, ${filteredCategories.size} categories")
            return NavigationTree(listOf(GroupNode("All Categories", filteredCategories)))
        }
        
        // Group by separator
        PerformanceLogger.logPhase("buildVodNavigationTree", "Grouping by separator '$separator'")
        val groupByStart = PerformanceLogger.start("Group by separator")
        val grouped = categories
            .groupBy { category -> extractGroupName(category.categoryName, separator) }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        PerformanceLogger.end("Group by separator", groupByStart, "groups=${grouped.size}")
        
        // Apply hierarchical filtering
        PerformanceLogger.logPhase("buildVodNavigationTree", "Applying hierarchical filtering")
        val filterStart = PerformanceLogger.start("Hierarchical filtering")
        val tree = filterNavigationTreeHierarchical(NavigationTree(grouped), hiddenGroups, hiddenCategories, filterMode)
        PerformanceLogger.end("Hierarchical filtering", filterStart, 
            "groups=${tree.groups.size}, totalCategories=${tree.groups.sumOf { it.count }}")
        
        PerformanceLogger.end("CategoryGrouper.buildVodNavigationTree", startTime, 
            "SUCCESS - ${tree.groups.size} groups, ${tree.groups.sumOf { it.count }} categories")
        return tree
    }
    
    /**
     * Build navigation tree for VOD categories with custom separator and filtering (backward compatibility)
     */
    fun buildVodNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "-",
        hiddenItems: Set<String> = emptySet(),
        filterMode: ContentFilterSettings.FilterMode = ContentFilterSettings.FilterMode.BLACKLIST
    ): NavigationTree {
        if (!groupingEnabled) {
            // No grouping - return all categories in single group
            val filteredCategories = filterCategories(categories, hiddenItems, filterMode)
            return NavigationTree(listOf(GroupNode("All Categories", filteredCategories)))
        }
        
        val grouped = categories
            .groupBy { category -> extractGroupName(category.categoryName, separator) }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        
        return filterNavigationTree(NavigationTree(grouped), hiddenItems, filterMode)
    }
    
    /**
     * Build navigation tree for series categories with hierarchical filtering
     */
    fun buildSeriesNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "FIRST_WORD",
        hiddenGroups: Set<String> = emptySet(),
        hiddenCategories: Set<String> = emptySet(),
        filterMode: ContentFilterSettings.FilterMode = ContentFilterSettings.FilterMode.BLACKLIST
    ): NavigationTree {
        if (!groupingEnabled) {
            // No grouping - filter only by categories
            val filteredCategories = filterCategoriesByName(categories, hiddenCategories, filterMode)
            return NavigationTree(listOf(GroupNode("All Categories", filteredCategories)))
        }
        
        val grouped = categories
            .groupBy { category -> extractGroupName(category.categoryName, separator) }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        
        return filterNavigationTreeHierarchical(NavigationTree(grouped), hiddenGroups, hiddenCategories, filterMode)
    }
    
    /**
     * Build navigation tree for series categories with custom separator and filtering (backward compatibility)
     */
    fun buildSeriesNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "FIRST_WORD",
        hiddenItems: Set<String> = emptySet(),
        filterMode: ContentFilterSettings.FilterMode = ContentFilterSettings.FilterMode.BLACKLIST
    ): NavigationTree {
        if (!groupingEnabled) {
            // No grouping - return all categories in single group
            val filteredCategories = filterCategories(categories, hiddenItems, filterMode)
            return NavigationTree(listOf(GroupNode("All Categories", filteredCategories)))
        }
        
        val grouped = categories
            .groupBy { category -> extractGroupName(category.categoryName, separator) }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        
        return filterNavigationTree(NavigationTree(grouped), hiddenItems, filterMode)
    }
    
    /**
     * Extract group name from category name using specified separator
     */
    private fun extractGroupName(categoryName: String, separator: String): String {
        return when (separator) {
            "FIRST_WORD" -> {
                // Extract first word
                categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
            }
            "|", "-", "/" -> {
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
     * Filter categories based on filter mode and hidden items
     */
    private fun filterCategories(
        categories: List<Category>,
        hiddenItems: Set<String>,
        filterMode: ContentFilterSettings.FilterMode
    ): List<Category> {
        if (hiddenItems.isEmpty()) return categories
        
        return when (filterMode) {
            ContentFilterSettings.FilterMode.BLACKLIST -> {
                // Hide selected items
                categories.filter { it.categoryName !in hiddenItems }
            }
            ContentFilterSettings.FilterMode.WHITELIST -> {
                // Show only selected items
                categories.filter { it.categoryName in hiddenItems }
            }
        }
    }
    
    /**
     * Filter navigation tree based on filter mode and hidden items
     */
    private fun filterNavigationTree(
        tree: NavigationTree,
        hiddenItems: Set<String>,
        filterMode: ContentFilterSettings.FilterMode
    ): NavigationTree {
        if (hiddenItems.isEmpty()) return tree
        
        val filteredGroups = when (filterMode) {
            ContentFilterSettings.FilterMode.BLACKLIST -> {
                // Hide selected groups
                tree.groups.filter { it.name !in hiddenItems }
            }
            ContentFilterSettings.FilterMode.WHITELIST -> {
                // Show only selected groups
                tree.groups.filter { it.name in hiddenItems }
            }
        }.filter { it.categories.isNotEmpty() } // Remove empty groups
        
        return NavigationTree(filteredGroups)
    }
    
    /**
     * Filter categories by name (for hierarchical filtering)
     */
    private fun filterCategoriesByName(
        categories: List<Category>,
        hiddenCategories: Set<String>,
        filterMode: ContentFilterSettings.FilterMode
    ): List<Category> {
        if (hiddenCategories.isEmpty()) return categories
        
        return when (filterMode) {
            ContentFilterSettings.FilterMode.BLACKLIST -> {
                // Hide selected categories
                categories.filter { it.categoryName !in hiddenCategories }
            }
            ContentFilterSettings.FilterMode.WHITELIST -> {
                // Show only selected categories
                categories.filter { it.categoryName in hiddenCategories }
            }
        }
    }
    
    /**
     * Filter navigation tree hierarchically (both groups and categories)
     * First filters entire groups, then filters individual categories within remaining groups
     */
    private fun filterNavigationTreeHierarchical(
        tree: NavigationTree,
        hiddenGroups: Set<String>,
        hiddenCategories: Set<String>,
        filterMode: ContentFilterSettings.FilterMode
    ): NavigationTree {
        PerformanceLogger.log("[filterNavigationTreeHierarchical] Input: ${tree.groups.size} groups, mode=$filterMode")
        
        // If both are empty, no filtering needed
        if (hiddenGroups.isEmpty() && hiddenCategories.isEmpty()) {
            PerformanceLogger.log("[filterNavigationTreeHierarchical] No filtering - both sets empty")
            return tree
        }
        
        // Step 1: Filter groups
        val groupFilterStart = PerformanceLogger.start("Filter groups")
        val groupFilteredGroups = when {
            hiddenGroups.isEmpty() -> tree.groups
            filterMode == ContentFilterSettings.FilterMode.BLACKLIST -> {
                // Hide selected groups
                tree.groups.filter { it.name !in hiddenGroups }
            }
            else -> {
                // Show only selected groups (whitelist)
                tree.groups.filter { it.name in hiddenGroups }
            }
        }
        PerformanceLogger.end("Filter groups", groupFilterStart, 
            "input=${tree.groups.size}, output=${groupFilteredGroups.size}")
        
        // Step 2: Filter categories within remaining groups
        val categoryFilterStart = PerformanceLogger.start("Filter categories within groups")
        val fullyFilteredGroups = if (hiddenCategories.isEmpty()) {
            groupFilteredGroups
        } else {
            groupFilteredGroups.map { group ->
                val filteredCategories = when (filterMode) {
                    ContentFilterSettings.FilterMode.BLACKLIST -> {
                        // Hide selected categories
                        group.categories.filter { it.categoryName !in hiddenCategories }
                    }
                    ContentFilterSettings.FilterMode.WHITELIST -> {
                        // Show only selected categories
                        group.categories.filter { it.categoryName in hiddenCategories }
                    }
                }
                GroupNode(group.name, filteredCategories)
            }.filter { it.categories.isNotEmpty() } // Remove empty groups after category filtering
        }
        val totalCategoriesFiltered = fullyFilteredGroups.sumOf { it.count }
        PerformanceLogger.end("Filter categories within groups", categoryFilterStart, 
            "groups=${fullyFilteredGroups.size}, categories=$totalCategoriesFiltered")
        
        PerformanceLogger.log("[filterNavigationTreeHierarchical] Output: ${fullyFilteredGroups.size} groups, $totalCategoriesFiltered categories")
        return NavigationTree(fullyFilteredGroups)
    }
}

