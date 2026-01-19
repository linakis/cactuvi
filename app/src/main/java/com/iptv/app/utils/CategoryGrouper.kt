package com.iptv.app.utils

import com.iptv.app.data.models.Category
import com.iptv.app.data.models.FilterMode

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
     * Build navigation tree for live TV categories with custom separator and filtering
     */
    fun buildLiveNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "|",
        hiddenItems: Set<String> = emptySet(),
        filterMode: FilterMode = FilterMode.BLACKLIST
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
     * Build navigation tree for VOD categories with custom separator and filtering
     */
    fun buildVodNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "-",
        hiddenItems: Set<String> = emptySet(),
        filterMode: FilterMode = FilterMode.BLACKLIST
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
     * Build navigation tree for series categories with custom separator and filtering
     */
    fun buildSeriesNavigationTree(
        categories: List<Category>,
        groupingEnabled: Boolean = true,
        separator: String = "FIRST_WORD",
        hiddenItems: Set<String> = emptySet(),
        filterMode: FilterMode = FilterMode.BLACKLIST
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
        filterMode: FilterMode
    ): List<Category> {
        if (hiddenItems.isEmpty()) return categories
        
        return when (filterMode) {
            FilterMode.BLACKLIST -> {
                // Hide selected items
                categories.filter { it.categoryName !in hiddenItems }
            }
            FilterMode.WHITELIST -> {
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
        filterMode: FilterMode
    ): NavigationTree {
        if (hiddenItems.isEmpty()) return tree
        
        val filteredGroups = when (filterMode) {
            FilterMode.BLACKLIST -> {
                // Hide selected groups
                tree.groups.filter { it.name !in hiddenItems }
            }
            FilterMode.WHITELIST -> {
                // Show only selected groups
                tree.groups.filter { it.name in hiddenItems }
            }
        }.filter { it.categories.isNotEmpty() } // Remove empty groups
        
        return NavigationTree(filteredGroups)
    }
}
