package com.iptv.app.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iptv.app.R
import com.iptv.app.data.db.entities.DownloadEntity

class DownloadsAdapter(
    private val onPlayClick: (DownloadEntity) -> Unit,
    private val onDeleteClick: (DownloadEntity) -> Unit
) : ListAdapter<DownloadEntity, DownloadsAdapter.DownloadViewHolder>(DownloadDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return DownloadViewHolder(view, onPlayClick, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class DownloadViewHolder(
        itemView: View,
        private val onPlayClick: (DownloadEntity) -> Unit,
        private val onDeleteClick: (DownloadEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val subtitleText: TextView = itemView.findViewById(R.id.subtitleText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        
        fun bind(download: DownloadEntity) {
            titleText.text = download.contentName
            
            // Set subtitle based on content type
            subtitleText.text = when (download.contentType) {
                "series" -> {
                    if (download.seasonNumber != null && download.episodeNumber != null) {
                        "S${download.seasonNumber}E${download.episodeNumber}"
                    } else {
                        "Series"
                    }
                }
                "movie" -> "Movie"
                else -> download.contentType
            }
            
            // Load poster
            Glide.with(itemView.context)
                .load(download.posterUrl)
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .centerCrop()
                .into(posterImage)
            
            // Click listeners
            itemView.setOnClickListener {
                onPlayClick(download)
            }
            
            deleteButton.setOnClickListener {
                onDeleteClick(download)
            }
        }
    }
    
    class DownloadDiffCallback : DiffUtil.ItemCallback<DownloadEntity>() {
        override fun areItemsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
            return oldItem.contentId == newItem.contentId
        }
        
        override fun areContentsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
            return oldItem == newItem
        }
    }
}
