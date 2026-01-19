package com.iptv.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.app.R

data class FolderItem(
    val name: String,
    var isChecked: Boolean = false
)

class FolderItemAdapter(
    private var items: List<FolderItem>,
    private val onItemToggle: (FolderItem, Boolean) -> Unit
) : RecyclerView.Adapter<FolderItemAdapter.ViewHolder>() {
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        val folderName: TextView = view.findViewById(R.id.folderName)
        
        fun bind(item: FolderItem) {
            folderName.text = item.name
            checkbox.isChecked = item.isChecked
            
            itemView.setOnClickListener {
                val newState = !checkbox.isChecked
                checkbox.isChecked = newState
                item.isChecked = newState
                onItemToggle(item, newState)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_checkbox, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount() = items.size
    
    fun updateItems(newItems: List<FolderItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    fun selectAll() {
        items.forEach { it.isChecked = true }
        notifyDataSetChanged()
    }
    
    fun deselectAll() {
        items.forEach { it.isChecked = false }
        notifyDataSetChanged()
    }
    
    fun getSelectedItems(): List<String> = items.filter { it.isChecked }.map { it.name }
    
    fun getUnselectedItems(): List<String> = items.filter { !it.isChecked }.map { it.name }
}
