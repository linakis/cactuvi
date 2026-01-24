package com.cactuvi.app.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.data.models.StreamSource

class SourcesAdapter(
    private val onSourceClick: (StreamSource) -> Unit,
    private val onEditClick: (StreamSource) -> Unit,
    private val onDeleteClick: (StreamSource) -> Unit
) : RecyclerView.Adapter<SourcesAdapter.SourceViewHolder>() {
    
    private var sources: List<StreamSource> = emptyList()
    private var activeSourceId: String? = null
    
    fun submitList(sources: List<StreamSource>, activeId: String?) {
        this.sources = sources
        this.activeSourceId = activeId
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_source, parent, false)
        return SourceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(sources[position])
    }
    
    override fun getItemCount() = sources.size
    
    inner class SourceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val radioButton: RadioButton = itemView.findViewById(R.id.radioButton)
        private val nicknameText: TextView = itemView.findViewById(R.id.nicknameText)
        private val serverText: TextView = itemView.findViewById(R.id.serverText)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        
        fun bind(source: StreamSource) {
            nicknameText.text = source.nickname
            serverText.text = source.server
            radioButton.isChecked = source.id == activeSourceId
            
            itemView.setOnClickListener {
                onSourceClick(source)
            }
            
            radioButton.setOnClickListener {
                onSourceClick(source)
            }
            
            editButton.setOnClickListener {
                onEditClick(source)
            }
            
            deleteButton.setOnClickListener {
                onDeleteClick(source)
            }
        }
    }
}
