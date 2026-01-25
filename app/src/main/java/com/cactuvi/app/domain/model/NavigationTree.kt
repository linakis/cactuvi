package com.cactuvi.app.domain.model

/**
 * Domain model for navigation tree state.
 * This is separate from the utility class CategoryGrouper.NavigationTree.
 */
data class NavigationTree(
    val groups: List<GroupNode>,
    val totalCategories: Int,
    val totalItems: Int
)

data class GroupNode(
    val name: String,
    val categories: List<ContentCategory>,
    val count: Int
)
