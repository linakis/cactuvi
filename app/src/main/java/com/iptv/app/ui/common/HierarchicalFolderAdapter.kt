package com.iptv.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.app.R

class HierarchicalFolderAdapter(
    private val groups: List<HierarchicalItem.GroupItem>,
    private val onGroupToggled: (HierarchicalItem.GroupItem) -> Unit,
    private val onCategoryToggled: (HierarchicalItem.CategoryItem) -> Unit,
    private val onGroupExpanded: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private var displayList: List<HierarchicalItem> = HierarchicalItemHelper.buildDisplayList(groups)
    
    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_CATEGORY = 1
    }
    
    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val expandArrow: ImageView = view.findViewById(R.id.expandArrow)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        val groupName: TextView = view.findViewById(R.id.groupName)
        val groupCount: TextView = view.findViewById(R.id.groupCount)
        
        fun bind(item: HierarchicalItem.GroupItem) {
            groupName.text = item.name
            groupCount.text = "${item.getChildCount()} categories"
            
            // Set checkbox state (tri-state support)
            checkbox.isChecked = item.isChecked
            // Note: Material3 CheckBox doesn't natively support indeterminate on Android
            // We'll use alpha to indicate indeterminate state
            checkbox.alpha = if (item.isIndeterminate) 0.5f else 1.0f
            
            // Set expand arrow rotation
            expandArrow.rotation = if (item.isExpanded) 180f else 0f
            
            // Click on entire row expands/collapses
            itemView.setOnClickListener {
                onGroupExpanded(item.name)
                notifyDataSetChanged()
            }
            
            // Click on checkbox toggles selection
            checkbox.setOnClickListener {
                item.isChecked = !item.isChecked
                item.isIndeterminate = false
                onGroupToggled(item)
                notifyDataSetChanged()
            }
        }
    }
    
    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        val categoryName: TextView = view.findViewById(R.id.categoryName)
        
        fun bind(item: HierarchicalItem.CategoryItem) {
            categoryName.text = item.name
            checkbox.isChecked = item.isChecked
            
            itemView.setOnClickListener {
                item.isChecked = !item.isChecked
                checkbox.isChecked = item.isChecked
                onCategoryToggled(item)
            }
            
            checkbox.setOnClickListener {
                item.isChecked = checkbox.isChecked
                onCategoryToggled(item)
            }
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is HierarchicalItem.GroupItem -> VIEW_TYPE_GROUP
            is HierarchicalItem.CategoryItem -> VIEW_TYPE_CATEGORY
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_folder_group, parent, false)
                GroupViewHolder(view)
            }
            VIEW_TYPE_CATEGORY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_folder_category, parent, false)
                CategoryViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {
            is HierarchicalItem.GroupItem -> (holder as GroupViewHolder).bind(item)
            is HierarchicalItem.CategoryItem -> (holder as CategoryViewHolder).bind(item)
        }
    }
    
    override fun getItemCount() = displayList.size
    
    fun updateDisplayList() {
        displayList = HierarchicalItemHelper.buildDisplayList(groups)
        notifyDataSetChanged()
    }
    
    fun selectAll() {
        groups.forEach { group ->
            group.isChecked = true
            group.isIndeterminate = false
        }
        
        displayList.filterIsInstance<HierarchicalItem.CategoryItem>().forEach {
            it.isChecked = true
        }
        
        notifyDataSetChanged()
    }
    
    fun deselectAll() {
        groups.forEach { group ->
            group.isChecked = false
            group.isIndeterminate = false
        }
        
        displayList.filterIsInstance<HierarchicalItem.CategoryItem>().forEach {
            it.isChecked = false
        }
        
        notifyDataSetChanged()
    }
    
    fun expandAll() {
        groups.forEach { it.isExpanded = true }
        updateDisplayList()
    }
    
    fun collapseAll() {
        groups.forEach { it.isExpanded = false }
        updateDisplayList()
    }
    
    fun getSelectedGroups(): Set<String> {
        return HierarchicalItemHelper.getSelectedGroups(groups)
    }
    
    fun getSelectedCategories(): Set<String> {
        return HierarchicalItemHelper.getSelectedCategories(groups, displayList)
    }
    
    /**
     * Filter displayed items based on search query
     * Auto-expands groups with matching children
     */
    fun filter(query: String) {
        if (query.isBlank()) {
            // Show all, restore previous expanded state
            displayList = HierarchicalItemHelper.buildDisplayList(groups)
            notifyDataSetChanged()
            return
        }
        
        val lowerQuery = query.lowercase()
        val filteredList = mutableListOf<HierarchicalItem>()
        
        for (group in groups) {
            val groupMatches = group.name.lowercase().contains(lowerQuery)
            val matchingCategories = group.categories.filter {
                it.categoryName.lowercase().contains(lowerQuery)
            }
            
            if (groupMatches || matchingCategories.isNotEmpty()) {
                // Add group
                filteredList.add(group)
                
                // Auto-expand if has matching children
                if (matchingCategories.isNotEmpty()) {
                    matchingCategories.forEach { category ->
                        filteredList.add(
                            HierarchicalItem.CategoryItem(
                                name = category.categoryName,
                                groupName = group.name,
                                category = category,
                                isChecked = displayList.filterIsInstance<HierarchicalItem.CategoryItem>()
                                    .find { it.name == category.categoryName }?.isChecked ?: false
                            )
                        )
                    }
                } else if (groupMatches && group.isExpanded) {
                    // Group name matches and was already expanded
                    group.categories.forEach { category ->
                        filteredList.add(
                            HierarchicalItem.CategoryItem(
                                name = category.categoryName,
                                groupName = group.name,
                                category = category,
                                isChecked = displayList.filterIsInstance<HierarchicalItem.CategoryItem>()
                                    .find { it.name == category.categoryName }?.isChecked ?: false
                            )
                        )
                    }
                }
            }
        }
        
        displayList = filteredList
        notifyDataSetChanged()
    }
}
