package com.cactuvi.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.utils.CategoryGrouper

class GroupAdapter(
    private val onGroupClick: (CategoryGrouper.GroupNode) -> Unit
) : ListAdapter<CategoryGrouper.GroupNode, GroupAdapter.GroupViewHolder>(GroupDiffCallback()) {
    
    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val groupName: TextView = view.findViewById(R.id.groupName)
        val groupCount: TextView = view.findViewById(R.id.groupCount)
        
        fun bind(group: CategoryGrouper.GroupNode) {
            groupName.text = group.name
            groupCount.text = "(${group.count})"
            
            itemView.setOnClickListener {
                onGroupClick(group)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun updateGroups(newGroups: List<CategoryGrouper.GroupNode>) {
        submitList(newGroups)
    }
    
    private class GroupDiffCallback : DiffUtil.ItemCallback<CategoryGrouper.GroupNode>() {
        override fun areItemsTheSame(
            oldItem: CategoryGrouper.GroupNode,
            newItem: CategoryGrouper.GroupNode
        ): Boolean {
            return oldItem.name == newItem.name
        }
        
        override fun areContentsTheSame(
            oldItem: CategoryGrouper.GroupNode,
            newItem: CategoryGrouper.GroupNode
        ): Boolean {
            return oldItem.name == newItem.name && oldItem.count == newItem.count
        }
    }
}
