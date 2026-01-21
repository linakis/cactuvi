package com.iptv.app.data.sync

import com.iptv.app.utils.CategoryGrouper.GroupNode

/**
 * Sealed class representing detected differences between old and new content states.
 * Used by ReactiveUpdateManager to apply granular UI updates without full screen reloads.
 */
sealed class ContentDiff {
    
    /**
     * New group added to navigation tree
     */
    data class GroupAdded(
        val contentType: String,  // "movies", "series", "live"
        val group: GroupNode
    ) : ContentDiff()
    
    /**
     * Group removed from navigation tree
     */
    data class GroupRemoved(
        val contentType: String,
        val groupName: String
    ) : ContentDiff()
    
    /**
     * Group's item count changed (without structural changes)
     */
    data class GroupCountChanged(
        val contentType: String,
        val groupName: String,
        val oldCount: Int,
        val newCount: Int
    ) : ContentDiff()
    
    /**
     * New items added to a specific category
     */
    data class ItemsAddedToCategory(
        val contentType: String,
        val categoryId: String,
        val categoryName: String,
        val newItemIds: List<String>
    ) : ContentDiff()
    
    /**
     * Items removed from a specific category
     */
    data class ItemsRemovedFromCategory(
        val contentType: String,
        val categoryId: String,
        val categoryName: String,
        val removedItemIds: List<String>
    ) : ContentDiff()
}
