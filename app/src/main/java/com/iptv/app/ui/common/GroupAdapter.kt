package com.iptv.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.app.R
import com.iptv.app.utils.CategoryGrouper

class GroupAdapter(
    private var groups: List<CategoryGrouper.GroupNode>,
    private val onGroupClick: (CategoryGrouper.GroupNode) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {
    
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
        holder.bind(groups[position])
    }
    
    override fun getItemCount() = groups.size
    
    fun updateGroups(newGroups: List<CategoryGrouper.GroupNode>) {
        groups = newGroups
        notifyDataSetChanged()
    }
}
