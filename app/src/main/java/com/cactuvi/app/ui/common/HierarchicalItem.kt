package com.cactuvi.app.ui.common

import com.cactuvi.app.data.models.Category

/** Sealed class representing hierarchical items in folder/category selection UI */
sealed class HierarchicalItem {
    abstract val id: String
    abstract val name: String
    abstract var isChecked: Boolean

    /** Group item representing a folder with child categories */
    data class GroupItem(
        override val name: String,
        val categories: List<Category>,
        var isExpanded: Boolean = false,
        override var isChecked: Boolean = false,
        var isIndeterminate: Boolean = false,
    ) : HierarchicalItem() {
        override val id: String = "group_$name"

        fun hasChildren(): Boolean = categories.isNotEmpty()

        fun getChildCount(): Int = categories.size

        /** Get all child category names */
        fun getChildNames(): List<String> = categories.map { it.categoryName }

        /**
         * Update checked state based on children
         *
         * @return true if state changed
         */
        fun updateStateFromChildren(checkedCategories: Set<String>): Boolean {
            val childNames = getChildNames()
            val checkedCount = childNames.count { it in checkedCategories }

            val oldChecked = isChecked
            val oldIndeterminate = isIndeterminate

            when (checkedCount) {
                0 -> {
                    isChecked = false
                    isIndeterminate = false
                }
                childNames.size -> {
                    isChecked = true
                    isIndeterminate = false
                }
                else -> {
                    isChecked = false
                    isIndeterminate = true
                }
            }

            return oldChecked != isChecked || oldIndeterminate != isIndeterminate
        }
    }

    /** Category item representing an individual category within a group */
    data class CategoryItem(
        override val name: String,
        val groupName: String,
        val category: Category,
        override var isChecked: Boolean = false,
    ) : HierarchicalItem() {
        override val id: String = "category_${category.categoryId}"
    }
}

/** Helper functions for working with hierarchical items */
object HierarchicalItemHelper {

    /** Build flat list for RecyclerView from groups, respecting expanded state */
    fun buildDisplayList(groups: List<HierarchicalItem.GroupItem>): List<HierarchicalItem> {
        val result = mutableListOf<HierarchicalItem>()

        for (group in groups) {
            result.add(group)

            if (group.isExpanded) {
                group.categories.forEach { category ->
                    result.add(
                        HierarchicalItem.CategoryItem(
                            name = category.categoryName,
                            groupName = group.name,
                            category = category,
                            isChecked = false, // Will be set based on stored preferences
                        ),
                    )
                }
            }
        }

        return result
    }

    /** Toggle group expansion and return updated display list */
    fun toggleGroupExpansion(
        groups: List<HierarchicalItem.GroupItem>,
        groupName: String
    ): List<HierarchicalItem> {
        groups.find { it.name == groupName }?.let { group -> group.isExpanded = !group.isExpanded }
        return buildDisplayList(groups)
    }

    /** Get all selected group names */
    fun getSelectedGroups(groups: List<HierarchicalItem.GroupItem>): Set<String> {
        return groups.filter { it.isChecked && !it.isIndeterminate }.map { it.name }.toSet()
    }

    /** Get all selected category names (including those in partially selected groups) */
    fun getSelectedCategories(
        groups: List<HierarchicalItem.GroupItem>,
        displayList: List<HierarchicalItem>,
    ): Set<String> {
        val selectedCategories = mutableSetOf<String>()

        // Add categories from fully checked groups
        groups
            .filter { it.isChecked && !it.isIndeterminate }
            .forEach { group -> selectedCategories.addAll(group.getChildNames()) }

        // Add individually checked categories from indeterminate groups
        displayList
            .filterIsInstance<HierarchicalItem.CategoryItem>()
            .filter { it.isChecked }
            .forEach { selectedCategories.add(it.name) }

        return selectedCategories
    }

    /** Apply stored selections to hierarchical items */
    fun applySelections(
        groups: List<HierarchicalItem.GroupItem>,
        selectedGroups: Set<String>,
        selectedCategories: Set<String>,
    ) {
        for (group in groups) {
            // Check if entire group is selected
            if (group.name in selectedGroups) {
                group.isChecked = true
                group.isIndeterminate = false
            } else {
                // Check individual categories
                group.updateStateFromChildren(selectedCategories)
            }
        }
    }

    /** Update category checked state in display list */
    fun updateCategoryChecked(
        displayList: List<HierarchicalItem>,
        categoryId: String,
        isChecked: Boolean,
    ): HierarchicalItem.CategoryItem? {
        return displayList
            .filterIsInstance<HierarchicalItem.CategoryItem>()
            .find { it.id == categoryId }
            ?.also { it.isChecked = isChecked }
    }

    /** Update all children of a group when parent is toggled */
    fun updateGroupChildren(
        displayList: List<HierarchicalItem>,
        groupName: String,
        isChecked: Boolean,
    ): List<HierarchicalItem.CategoryItem> {
        return displayList
            .filterIsInstance<HierarchicalItem.CategoryItem>()
            .filter { it.groupName == groupName }
            .onEach { it.isChecked = isChecked }
    }

    /** Update parent group state based on children */
    fun updateParentState(
        groups: List<HierarchicalItem.GroupItem>,
        displayList: List<HierarchicalItem>,
        groupName: String,
    ): Boolean {
        val group = groups.find { it.name == groupName } ?: return false

        val checkedCategories =
            displayList
                .filterIsInstance<HierarchicalItem.CategoryItem>()
                .filter { it.groupName == groupName && it.isChecked }
                .map { it.name }
                .toSet()

        return group.updateStateFromChildren(checkedCategories)
    }
}
