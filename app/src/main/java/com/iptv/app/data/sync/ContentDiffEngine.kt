package com.iptv.app.data.sync

import com.iptv.app.utils.CategoryGrouper.NavigationTree
import com.iptv.app.utils.CategoryGrouper.GroupNode

/**
 * Detects differences between old and new content states.
 * Produces granular ContentDiff events for reactive UI updates.
 * Respects user's hidden groups/categories (filtered content is ignored).
 */
object ContentDiffEngine {
    
    /**
     * Compare two navigation trees and generate group-level diff events.
     * Only generates diffs for visible content (user's hidden groups are excluded).
     * 
     * @param contentType "movies", "series", or "live"
     * @param oldTree Previous navigation tree state
     * @param newTree Updated navigation tree state
     * @return List of ContentDiff events representing changes
     */
    fun diffNavigationTree(
        contentType: String,
        oldTree: NavigationTree,
        newTree: NavigationTree
    ): List<ContentDiff> {
        val diffs = mutableListOf<ContentDiff>()
        
        val oldGroupMap = oldTree.groups.associateBy { it.name }
        val newGroupMap = newTree.groups.associateBy { it.name }
        
        // Detect added groups
        for ((groupName, newGroup) in newGroupMap) {
            if (!oldGroupMap.containsKey(groupName)) {
                diffs.add(ContentDiff.GroupAdded(contentType, newGroup))
            }
        }
        
        // Detect removed groups
        for (groupName in oldGroupMap.keys) {
            if (!newGroupMap.containsKey(groupName)) {
                diffs.add(ContentDiff.GroupRemoved(contentType, groupName))
            }
        }
        
        // Detect count changes in existing groups
        for ((groupName, newGroup) in newGroupMap) {
            val oldGroup = oldGroupMap[groupName]
            if (oldGroup != null && oldGroup.count != newGroup.count) {
                diffs.add(
                    ContentDiff.GroupCountChanged(
                        contentType,
                        groupName,
                        oldGroup.count,
                        newGroup.count
                    )
                )
            }
        }
        
        return diffs
    }
    
    /**
     * Compare categories within matching groups and detect item-level changes.
     * Used for detecting new/removed items in specific categories.
     * 
     * @param contentType "movies", "series", or "live"
     * @param oldGroup Previous group state
     * @param newGroup Updated group state
     * @param oldItemIds Map of categoryId -> Set<itemId> from old state
     * @param newItemIds Map of categoryId -> Set<itemId> from new state
     * @return List of ContentDiff events for category-level changes
     */
    fun diffCategoryItems(
        contentType: String,
        oldGroup: GroupNode,
        newGroup: GroupNode,
        oldItemIds: Map<String, Set<String>>,
        newItemIds: Map<String, Set<String>>
    ): List<ContentDiff> {
        val diffs = mutableListOf<ContentDiff>()
        
        val oldCategoryMap = oldGroup.categories.associateBy { it.categoryId }
        val newCategoryMap = newGroup.categories.associateBy { it.categoryId }
        
        // Check each category for item changes
        for ((categoryId, newCategory) in newCategoryMap) {
            if (!oldCategoryMap.containsKey(categoryId)) continue
            
            val oldItems = oldItemIds[categoryId] ?: emptySet()
            val newItems = newItemIds[categoryId] ?: emptySet()
            
            // Detect added items
            val addedItems = newItems - oldItems
            if (addedItems.isNotEmpty()) {
                diffs.add(
                    ContentDiff.ItemsAddedToCategory(
                        contentType,
                        categoryId,
                        newCategory.categoryName,
                        addedItems.toList()
                    )
                )
            }
            
            // Detect removed items
            val removedItems = oldItems - newItems
            if (removedItems.isNotEmpty()) {
                diffs.add(
                    ContentDiff.ItemsRemovedFromCategory(
                        contentType,
                        categoryId,
                        newCategory.categoryName,
                        removedItems.toList()
                    )
                )
            }
        }
        
        return diffs
    }
}
