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
import com.cactuvi.app.data.models.Movie

class MoviePagingAdapter(
    private val onMovieClick: (Movie) -> Unit,
) : PagingDataAdapter<Movie, MoviePagingAdapter.ViewHolder>(MOVIE_COMPARATOR) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val moviePoster: ImageView = view.findViewById(R.id.moviePoster)
        val movieName: TextView = view.findViewById(R.id.movieName)
        val movieRating: TextView = view.findViewById(R.id.movieRating)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movie = getItem(position) ?: return

        holder.movieName.text = movie.name

        // Display rating
        if (!movie.rating.isNullOrEmpty() && movie.rating != "0") {
            holder.movieRating.text = "★ ${movie.rating}"
            holder.movieRating.visibility = View.VISIBLE
        } else {
            holder.movieRating.visibility = View.GONE
        }

        // Load movie poster with Glide
        if (!movie.streamIcon.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(movie.streamIcon)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(holder.moviePoster)
        } else {
            holder.moviePoster.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener { onMovieClick(movie) }
    }

    companion object {
        private val MOVIE_COMPARATOR =
            object : DiffUtil.ItemCallback<Movie>() {
                override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean {
                    return oldItem.streamId == newItem.streamId
                }

                override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean {
                    return oldItem == newItem
                }
            }
    }
}
