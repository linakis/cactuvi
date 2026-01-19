package com.iptv.app.utils

import com.iptv.app.data.models.Category
import com.iptv.app.data.models.ContentFilterSettings

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
        // If both are empty, no filtering needed
        if (hiddenGroups.isEmpty() && hiddenCategories.isEmpty()) return tree
        
        // Step 1: Filter groups
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
        
        // Step 2: Filter categories within remaining groups
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
        
        return NavigationTree(fullyFilteredGroups)
    }
}

