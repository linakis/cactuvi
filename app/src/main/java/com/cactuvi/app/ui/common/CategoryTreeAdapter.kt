package com.cactuvi.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.data.models.Category

class CategoryTreeAdapter(
    private val onCategoryClick: (Category) -> Unit
) : ListAdapter<Pair<Category, Int>, CategoryTreeAdapter.CategoryViewHolder>(CategoryDiffCallback()) {
    
    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryName: TextView = view.findViewById(R.id.categoryName)
        val categoryCount: TextView = view.findViewById(R.id.categoryCount)
        
        fun bind(categoryWithCount: Pair<Category, Int>) {
            val (category, count) = categoryWithCount
            categoryName.text = category.categoryName
            categoryCount.text = "($count)"
            
            itemView.setOnClickListener {
                onCategoryClick(category)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_tree, parent, false)
        return CategoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun updateCategories(newCategories: List<Pair<Category, Int>>) {
        submitList(newCategories)
    }
    
    private class CategoryDiffCallback : DiffUtil.ItemCallback<Pair<Category, Int>>() {
        override fun areItemsTheSame(
            oldItem: Pair<Category, Int>,
            newItem: Pair<Category, Int>
        ): Boolean {
            return oldItem.first.categoryId == newItem.first.categoryId
        }
        
        override fun areContentsTheSame(
            oldItem: Pair<Category, Int>,
            newItem: Pair<Category, Int>
        ): Boolean {
            return oldItem.first.categoryId == newItem.first.categoryId &&
                   oldItem.first.categoryName == newItem.first.categoryName &&
                   oldItem.second == newItem.second
        }
    }
}
