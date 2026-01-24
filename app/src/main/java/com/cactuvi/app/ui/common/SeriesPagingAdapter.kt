package com.cactuvi.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cactuvi.app.R
import com.cactuvi.app.data.models.Series

class SeriesPagingAdapter(
    private val onSeriesClick: (Series) -> Unit
) : PagingDataAdapter<Series, SeriesPagingAdapter.ViewHolder>(SERIES_COMPARATOR) {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val seriesPoster: ImageView = view.findViewById(R.id.moviePoster)
        val seriesName: TextView = view.findViewById(R.id.movieName)
        val seriesRating: TextView = view.findViewById(R.id.movieRating)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_movie, parent, false)  // Reuse movie layout
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val seriesItem = getItem(position) ?: return
        
        holder.seriesName.text = seriesItem.name
        
        // Display rating
        if (!seriesItem.rating.isNullOrEmpty() && seriesItem.rating != "0") {
            holder.seriesRating.text = "★ ${seriesItem.rating}"
            holder.seriesRating.visibility = View.VISIBLE
        } else {
            holder.seriesRating.visibility = View.GONE
        }
        
        // Load series cover with Glide
        if (!seriesItem.cover.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(seriesItem.cover)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(holder.seriesPoster)
        } else {
            holder.seriesPoster.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        
        holder.itemView.setOnClickListener {
            onSeriesClick(seriesItem)
        }
    }
    
    companion object {
        private val SERIES_COMPARATOR = object : DiffUtil.ItemCallback<Series>() {
            override fun areItemsTheSame(oldItem: Series, newItem: Series): Boolean {
                return oldItem.seriesId == newItem.seriesId
            }
            
            override fun areContentsTheSame(oldItem: Series, newItem: Series): Boolean {
                return oldItem == newItem
            }
        }
    }
}
