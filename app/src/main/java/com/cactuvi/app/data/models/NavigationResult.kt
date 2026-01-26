package com.cactuvi.app.data.models

/**
 * Result type for navigation queries. Groups = top-level with grouping enabled Categories = flat
 * list of categories
 */
sealed class NavigationResult {
    data class Groups(val groups: Map<String, List<Category>>) : NavigationResult()

    data class Categories(val categories: List<Category>) : NavigationResult()
}
