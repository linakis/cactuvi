package com.cactuvi.app.domain.model

/**
 * Domain model for navigation tree state. This is separate from the utility class
 * CategoryGrouper.NavigationTree.
 */
data class NavigationTree(
    val groups: List<GroupNode>,
) {
    val totalCategories: Int
        get() = groups.sumOf { it.categories.size }

    val totalItems: Int
        get() = groups.sumOf { it.count }
}

data class GroupNode(
    val name: String,
    val categories: List<ContentCategory>,
) {
    val count: Int
        get() = categories.size
}
