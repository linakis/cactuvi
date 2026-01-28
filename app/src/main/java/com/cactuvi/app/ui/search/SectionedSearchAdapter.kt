package com.cactuvi.app.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cactuvi.app.R
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.Series

/**
 * Adapter for displaying search results in sections (Movies, Series, Live TV). Used when searching
 * from home (TYPE_ALL) to show results grouped by content type.
 */
class SectionedSearchAdapter(
    private val onMovieClick: (Movie) -> Unit,
    private val onSeriesClick: (Series) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SearchItem>()

    companion object {
        private const val VIEW_TYPE_SECTION_HEADER = 0
        private const val VIEW_TYPE_MOVIE = 1
        private const val VIEW_TYPE_SERIES = 2
    }

    sealed class SearchItem {
        data class SectionHeader(val title: String, val count: Int) : SearchItem()

        data class MovieItem(val movie: Movie) : SearchItem()

        data class SeriesItem(val series: Series) : SearchItem()
    }

    fun updateResults(movies: List<Movie>, series: List<Series>) {
        items.clear()

        // Add Movies section if not empty
        if (movies.isNotEmpty()) {
            items.add(SearchItem.SectionHeader("Movies", movies.size))
            items.addAll(movies.map { SearchItem.MovieItem(it) })
        }

        // Add Series section if not empty
        if (series.isNotEmpty()) {
            items.add(SearchItem.SectionHeader("Series", series.size))
            items.addAll(series.map { SearchItem.SeriesItem(it) })
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchItem.SectionHeader -> VIEW_TYPE_SECTION_HEADER
            is SearchItem.MovieItem -> VIEW_TYPE_MOVIE
            is SearchItem.SeriesItem -> VIEW_TYPE_SERIES
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SECTION_HEADER -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_search_section_header, parent, false)
                SectionHeaderViewHolder(view)
            }
            VIEW_TYPE_MOVIE -> {
                val view =
                    LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
                MovieViewHolder(view)
            }
            VIEW_TYPE_SERIES -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(
                            R.layout.item_movie,
                            parent,
                            false
                        ) // Reuse movie layout for series
                SeriesViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SearchItem.SectionHeader -> {
                (holder as SectionHeaderViewHolder).bind(item)
            }
            is SearchItem.MovieItem -> {
                (holder as MovieViewHolder).bind(item.movie, onMovieClick)
            }
            is SearchItem.SeriesItem -> {
                (holder as SeriesViewHolder).bind(item.series, onSeriesClick)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // ========== VIEW HOLDERS ==========

    class SectionHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.sectionTitle)
        private val countText: TextView = itemView.findViewById(R.id.sectionCount)

        fun bind(header: SearchItem.SectionHeader) {
            titleText.text = header.title
            countText.text = "${header.count} results"
        }
    }

    class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ImageView = itemView.findViewById(R.id.moviePoster)
        private val titleText: TextView = itemView.findViewById(R.id.movieName)
        private val ratingText: TextView = itemView.findViewById(R.id.movieRating)

        fun bind(movie: Movie, onClick: (Movie) -> Unit) {
            titleText.text = movie.name

            // Display rating
            if (!movie.rating.isNullOrEmpty() && movie.rating != "0") {
                ratingText.text = "★ ${movie.rating}"
                ratingText.visibility = View.VISIBLE
            } else {
                ratingText.visibility = View.GONE
            }

            Glide.with(itemView.context)
                .load(movie.streamIcon)
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .into(posterImage)

            itemView.setOnClickListener { onClick(movie) }
        }
    }

    class SeriesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.moviePoster)
        private val titleText: TextView = itemView.findViewById(R.id.movieName)
        private val ratingText: TextView = itemView.findViewById(R.id.movieRating)

        fun bind(series: Series, onClick: (Series) -> Unit) {
            titleText.text = series.name

            // Display rating
            if (!series.rating.isNullOrEmpty() && series.rating != "0") {
                ratingText.text = "★ ${series.rating}"
                ratingText.visibility = View.VISIBLE
            } else {
                ratingText.visibility = View.GONE
            }

            Glide.with(itemView.context)
                .load(series.cover)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(coverImage)

            itemView.setOnClickListener { onClick(series) }
        }
    }
}
