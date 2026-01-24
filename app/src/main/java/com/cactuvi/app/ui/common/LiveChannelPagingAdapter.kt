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
import com.cactuvi.app.data.models.LiveChannel

class LiveChannelPagingAdapter(
    private val onChannelClick: (LiveChannel) -> Unit
) : PagingDataAdapter<LiveChannel, LiveChannelPagingAdapter.ViewHolder>(CHANNEL_COMPARATOR) {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val channelIcon: ImageView = view.findViewById(R.id.channelIcon)
        val channelName: TextView = view.findViewById(R.id.channelName)
        val channelCategory: TextView = view.findViewById(R.id.channelCategory)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_channel, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = getItem(position) ?: return
        
        holder.channelName.text = channel.name
        holder.channelCategory.text = channel.categoryName
        
        // Load channel icon with Glide
        if (!channel.streamIcon.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(channel.streamIcon)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(holder.channelIcon)
        } else {
            holder.channelIcon.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        
        holder.itemView.setOnClickListener {
            onChannelClick(channel)
        }
    }
    
    companion object {
        private val CHANNEL_COMPARATOR = object : DiffUtil.ItemCallback<LiveChannel>() {
            override fun areItemsTheSame(oldItem: LiveChannel, newItem: LiveChannel): Boolean {
                return oldItem.streamId == newItem.streamId
            }
            
            override fun areContentsTheSame(oldItem: LiveChannel, newItem: LiveChannel): Boolean {
                return oldItem == newItem
            }
        }
    }
}
