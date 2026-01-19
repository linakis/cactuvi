package com.iptv.app.ui.continuewatching

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iptv.app.R
import com.iptv.app.data.db.entities.WatchHistoryEntity
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ContinueWatchingAdapter(
    private var items: List<WatchHistoryEntity>,
    private val onItemClick: (WatchHistoryEntity) -> Unit,
    private val onDeleteClick: (WatchHistoryEntity) -> Unit
) : RecyclerView.Adapter<ContinueWatchingAdapter.ViewHolder>() {
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.poster)
        val title: TextView = view.findViewById(R.id.title)
        val progressText: TextView = view.findViewById(R.id.progressText)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val lastWatched: TextView = view.findViewById(R.id.lastWatched)
        val deleteButton: ImageView = view.findViewById(R.id.deleteButton)
        
        fun bind(item: WatchHistoryEntity) {
            title.text = item.contentName
            
            // Format progress
            val currentTime = formatTime(item.resumePosition)
            val totalTime = formatTime(item.duration)
            progressText.text = "$currentTime / $totalTime"
            
            // Set progress bar
            val progress = ((item.resumePosition.toDouble() / item.duration) * 100).toInt()
            progressBar.max = 100
            progressBar.progress = progress
            
            // Format last watched time
            lastWatched.text = formatLastWatched(item.lastWatched)
            
            // Load poster
            if (!item.posterUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(item.posterUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(poster)
            } else {
                poster.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            
            itemView.setOnClickListener {
                onItemClick(item)
            }
            
            deleteButton.setOnClickListener {
                onDeleteClick(item)
            }
        }
        
        private fun formatTime(milliseconds: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
            
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
        
        private fun formatLastWatched(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            
            return when {
                days > 0 -> "Watched ${days}d ago"
                hours > 0 -> "Watched ${hours}h ago"
                minutes > 0 -> "Watched ${minutes}m ago"
                else -> "Watched just now"
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount() = items.size
    
    fun updateItems(newItems: List<WatchHistoryEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}
