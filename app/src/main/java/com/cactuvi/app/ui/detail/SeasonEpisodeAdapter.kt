package com.cactuvi.app.ui.detail

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.data.models.Episode
import com.cactuvi.app.data.models.Season

sealed class SeasonEpisodeItem {
    data class SeasonHeader(
        val season: Season,
        var isExpanded: Boolean = false,
    ) : SeasonEpisodeItem()

    data class EpisodeItem(
        val episode: Episode,
        val seasonNumber: Int,
        val hasProgress: Boolean = false,
    ) : SeasonEpisodeItem()
}

class SeasonEpisodeAdapter(
    private val onEpisodeClick: (Episode, Int) -> Unit,
    private val onDownloadClick: (Episode, Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SeasonEpisodeItem>()
    private var episodesMap: Map<String, List<Episode>> = emptyMap()

    companion object {
        private const val VIEW_TYPE_SEASON = 0
        private const val VIEW_TYPE_EPISODE = 1
    }

    fun updateSeasons(seasons: List<Season>, episodes: Map<String, List<Episode>>) {
        Log.d("SeasonEpisodeAdapter", "=== updateSeasons called ===")
        Log.d("SeasonEpisodeAdapter", "Seasons count: ${seasons.size}")
        Log.d("SeasonEpisodeAdapter", "Episodes map keys: ${episodes.keys}")
        Log.d("SeasonEpisodeAdapter", "Episodes map size: ${episodes.size}")

        this.episodesMap = episodes
        items.clear()

        // All seasons collapsed by default (accordion-style)
        seasons.forEach { season ->
            Log.d(
                "SeasonEpisodeAdapter",
                "Processing season ${season.seasonNumber}: ${season.name}"
            )

            // All seasons start collapsed
            items.add(SeasonEpisodeItem.SeasonHeader(season, isExpanded = false))
            Log.d(
                "SeasonEpisodeAdapter",
                "Added season header for season ${season.seasonNumber}, expanded=false"
            )
        }

        Log.d("SeasonEpisodeAdapter", "Total items in adapter: ${items.size}")
        Log.d("SeasonEpisodeAdapter", "=== updateSeasons complete ===")
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SeasonEpisodeItem.SeasonHeader -> VIEW_TYPE_SEASON
            is SeasonEpisodeItem.EpisodeItem -> VIEW_TYPE_EPISODE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SEASON -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_season_header, parent, false)
                SeasonHeaderViewHolder(view)
            }
            VIEW_TYPE_EPISODE -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_episode, parent, false)
                EpisodeViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        Log.d("SeasonEpisodeAdapter", "Binding position $position of ${items.size}")
        when (val item = items[position]) {
            is SeasonEpisodeItem.SeasonHeader -> {
                Log.d("SeasonEpisodeAdapter", "Binding SeasonHeader: ${item.season.name}")
                (holder as SeasonHeaderViewHolder).bind(item, position)
            }
            is SeasonEpisodeItem.EpisodeItem -> {
                Log.d("SeasonEpisodeAdapter", "Binding Episode: ${item.episode.title}")
                (holder as EpisodeViewHolder).bind(item)
            }
        }
    }

    override fun getItemCount(): Int {
        val count = items.size
        Log.d("SeasonEpisodeAdapter", "getItemCount() returning: $count")
        return count
    }

    private fun toggleSeason(position: Int) {
        val headerItem = items[position] as? SeasonEpisodeItem.SeasonHeader ?: return
        val season = headerItem.season

        Log.d(
            "SeasonEpisodeAdapter",
            "toggleSeason for season ${season.seasonNumber}, currently expanded=${headerItem.isExpanded}",
        )

        if (headerItem.isExpanded) {
            // COLLAPSING: Remove episodes from list
            val episodesToRemove = mutableListOf<Int>()
            for (i in position + 1 until items.size) {
                val item = items[i]
                if (
                    item is SeasonEpisodeItem.EpisodeItem &&
                        item.seasonNumber == season.seasonNumber
                ) {
                    episodesToRemove.add(i)
                } else if (item is SeasonEpisodeItem.SeasonHeader) {
                    break
                }
            }

            // Remove in reverse order to maintain correct indices
            episodesToRemove.reversed().forEach { index -> items.removeAt(index) }

            headerItem.isExpanded = false
            notifyItemChanged(position)
            if (episodesToRemove.isNotEmpty()) {
                notifyItemRangeRemoved(position + 1, episodesToRemove.size)
            }
        } else {
            // ACCORDION BEHAVIOR: First, close any other expanded season
            closeAllSeasons()

            // EXPANDING: Add episodes to list
            val seasonEpisodes = episodesMap[season.seasonNumber.toString()] ?: emptyList()
            val episodeItems =
                seasonEpisodes.map { episode ->
                    SeasonEpisodeItem.EpisodeItem(episode, season.seasonNumber)
                }

            items.addAll(position + 1, episodeItems)

            headerItem.isExpanded = true
            notifyItemChanged(position)
            notifyItemRangeInserted(position + 1, episodeItems.size)
        }
    }

    private fun closeAllSeasons() {
        // Find and collapse all expanded seasons
        var i = 0
        while (i < items.size) {
            val item = items[i]
            if (item is SeasonEpisodeItem.SeasonHeader && item.isExpanded) {
                Log.d(
                    "SeasonEpisodeAdapter",
                    "Closing previously expanded season ${item.season.seasonNumber}"
                )

                // Find and remove episodes for this season
                val episodesToRemove = mutableListOf<Int>()
                var j = i + 1
                while (j < items.size) {
                    val nextItem = items[j]
                    if (
                        nextItem is SeasonEpisodeItem.EpisodeItem &&
                            nextItem.seasonNumber == item.season.seasonNumber
                    ) {
                        episodesToRemove.add(j)
                        j++
                    } else if (nextItem is SeasonEpisodeItem.SeasonHeader) {
                        break
                    } else {
                        j++
                    }
                }

                // Remove in reverse order
                episodesToRemove.reversed().forEach { index -> items.removeAt(index) }

                item.isExpanded = false
                notifyItemChanged(i)
                if (episodesToRemove.isNotEmpty()) {
                    notifyItemRangeRemoved(i + 1, episodesToRemove.size)
                }
            }
            i++
        }
    }

    inner class SeasonHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val seasonText: TextView = itemView.findViewById(R.id.seasonText)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)

        fun bind(item: SeasonEpisodeItem.SeasonHeader, position: Int) {
            val season = item.season
            val context = itemView.context

            seasonText.text =
                context.getString(
                    R.string.season,
                    season.seasonNumber,
                ) + " (${season.episodeCount} ${context.getString(R.string.episodes).lowercase()})"

            // Rotate icon based on expansion state
            val rotation = if (item.isExpanded) 180f else 0f
            expandIcon.rotation = rotation

            itemView.setOnClickListener {
                val fromRotation = expandIcon.rotation
                val toRotation = if (item.isExpanded) 0f else 180f

                val rotate =
                    RotateAnimation(
                            fromRotation,
                            toRotation,
                            RotateAnimation.RELATIVE_TO_SELF,
                            0.5f,
                            RotateAnimation.RELATIVE_TO_SELF,
                            0.5f,
                        )
                        .apply {
                            duration = 200
                            fillAfter = true
                        }
                expandIcon.startAnimation(rotate)
                expandIcon.rotation = toRotation

                toggleSeason(position)
            }
        }
    }

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val episodeNumber: TextView = itemView.findViewById(R.id.episodeNumber)
        private val episodeTitle: TextView = itemView.findViewById(R.id.episodeTitle)
        private val episodeDuration: TextView = itemView.findViewById(R.id.episodeDuration)
        private val progressIndicator: View = itemView.findViewById(R.id.progressIndicator)
        private val downloadButton: ImageButton = itemView.findViewById(R.id.downloadButton)

        fun bind(item: SeasonEpisodeItem.EpisodeItem) {
            val episode = item.episode

            // Directly bind data - no visibility checks needed
            episodeNumber.text = episode.episodeNum.toString()
            episodeTitle.text = episode.title

            // Format duration
            val durationText =
                episode.info?.duration ?: episode.info?.durationSecs?.let { "${it / 60} min" } ?: ""
            episodeDuration.text = durationText

            // Show progress indicator if episode has watch history
            progressIndicator.visibility = if (item.hasProgress) View.VISIBLE else View.GONE

            // Episode row click - plays episode
            itemView.setOnClickListener { onEpisodeClick(episode, item.seasonNumber) }

            // Download button click - downloads episode directly
            downloadButton.setOnClickListener { onDownloadClick(episode, item.seasonNumber) }
        }
    }
}
