package com.cactuvi.app.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cactuvi.app.R
import com.cactuvi.app.data.db.entities.DownloadEntity

class ActiveDownloadsAdapter(
    private val onCancelClick: (DownloadEntity) -> Unit,
) :
    ListAdapter<DownloadEntity, ActiveDownloadsAdapter.ActiveDownloadViewHolder>(
        ActiveDownloadDiffCallback()
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActiveDownloadViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download_active, parent, false)
        return ActiveDownloadViewHolder(view, onCancelClick)
    }

    override fun onBindViewHolder(holder: ActiveDownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ActiveDownloadViewHolder(
        itemView: View,
        private val onCancelClick: (DownloadEntity) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {

        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val downloadProgress: ProgressBar = itemView.findViewById(R.id.downloadProgress)
        private val sizeText: TextView = itemView.findViewById(R.id.sizeText)
        private val actionButton: ImageButton = itemView.findViewById(R.id.actionButton)

        fun bind(download: DownloadEntity) {
            // Set title with episode info if applicable
            titleText.text =
                if (download.contentType == "series" && download.episodeName != null) {
                    "${download.contentName} - ${download.episodeName}"
                } else {
                    download.contentName
                }

            // Set status text
            val progressPercent = (download.progress * 100).toInt()
            statusText.text =
                when (download.status) {
                    "queued" -> itemView.context.getString(R.string.download_queued)
                    "downloading" ->
                        itemView.context.getString(R.string.download_progress, progressPercent)
                    "paused" -> itemView.context.getString(R.string.download_paused)
                    "failed" -> itemView.context.getString(R.string.download_failed)
                    else -> download.status.replaceFirstChar { it.uppercase() }
                }

            // Set progress bar
            downloadProgress.progress = progressPercent

            // Set size text
            sizeText.text = formatDownloadSize(download.bytesDownloaded, download.totalBytes)

            // Load poster
            Glide.with(itemView.context)
                .load(download.posterUrl)
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .centerCrop()
                .into(posterImage)

            // Action button
            actionButton.setOnClickListener { onCancelClick(download) }
        }

        private fun formatDownloadSize(downloaded: Long, total: Long): String {
            val downloadedMB = downloaded / (1024.0 * 1024.0)
            val totalMB = total / (1024.0 * 1024.0)

            return if (total > 0) {
                String.format("%.1f MB / %.1f MB", downloadedMB, totalMB)
            } else if (downloaded > 0) {
                String.format("%.1f MB", downloadedMB)
            } else {
                itemView.context.getString(R.string.download_calculating)
            }
        }
    }

    class ActiveDownloadDiffCallback : DiffUtil.ItemCallback<DownloadEntity>() {
        override fun areItemsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
            return oldItem.contentId == newItem.contentId
        }

        override fun areContentsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
            return oldItem == newItem
        }
    }
}
