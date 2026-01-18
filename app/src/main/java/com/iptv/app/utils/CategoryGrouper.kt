package com.iptv.app.utils

import com.iptv.app.data.models.Category

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
     * Build navigation tree for live TV categories
     * Pattern: "UK| SPORT", "US| NEWS" -> Groups: UK, US
     */
    fun buildLiveNavigationTree(categories: List<Category>): NavigationTree {
        val grouped = categories
            .groupBy { category ->
                // Extract prefix before pipe
                val parts = category.categoryName.split("|")
                if (parts.size > 1) {
                    parts[0].trim()
                } else {
                    // Fallback: extract first word
                    category.categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
                }
            }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        
        return NavigationTree(grouped)
    }
    
    /**
     * Build navigation tree for VOD categories
     * Pattern: "EN - ACTION", "FR - COMEDIE" -> Groups: EN, FR
     */
    fun buildVodNavigationTree(categories: List<Category>): NavigationTree {
        val grouped = categories
            .groupBy { category ->
                // Extract prefix before dash
                val dashParts = category.categoryName.split("-")
                if (dashParts.size > 1) {
                    dashParts[0].trim()
                } else {
                    // Check for slash pattern (PT/BR)
                    val slashParts = category.categoryName.split("/")
                    if (slashParts.size > 1) {
                        slashParts[0].trim()
                    } else {
                        // Extract first word
                        category.categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
                    }
                }
            }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        
        return NavigationTree(grouped)
    }
    
    /**
     * Build navigation tree for series categories
     * Pattern: "NETFLIX SERIES", "FRANCE SÉRIES" -> Groups: NETFLIX, FRANCE
     */
    fun buildSeriesNavigationTree(categories: List<Category>): NavigationTree {
        val grouped = categories
            .groupBy { category ->
                // Extract first word (platform or region)
                category.categoryName.split(" ").firstOrNull()?.trim() ?: "Other"
            }
            .map { (groupName, cats) ->
                GroupNode(groupName, cats.sortedBy { it.categoryName })
            }
            .sortedBy { it.name }
        
        return NavigationTree(grouped)
    }
}
