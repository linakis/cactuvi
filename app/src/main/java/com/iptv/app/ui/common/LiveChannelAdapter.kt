package com.iptv.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iptv.app.R
import com.iptv.app.data.models.LiveChannel

class LiveChannelAdapter(
    private var channels: List<LiveChannel>,
    private val onChannelClick: (LiveChannel) -> Unit
) : RecyclerView.Adapter<LiveChannelAdapter.ViewHolder>() {
    
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
        val channel = channels[position]
        
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
    
    override fun getItemCount() = channels.size
    
    fun updateChannels(newChannels: List<LiveChannel>) {
        channels = newChannels
        notifyDataSetChanged()
    }
}
