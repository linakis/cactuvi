package com.iptv.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.app.R
import com.iptv.app.data.models.Category

class CategoryTreeAdapter(
    private var categories: List<Pair<Category, Int>>, // Category with item count
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryTreeAdapter.CategoryViewHolder>() {
    
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
        holder.bind(categories[position])
    }
    
    override fun getItemCount() = categories.size
    
    fun updateCategories(newCategories: List<Pair<Category, Int>>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
