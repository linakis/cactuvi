package com.cactuvi.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cactuvi.app.R
import com.cactuvi.app.data.models.Movie

class MovieAdapter(
    private var movies: List<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<MovieAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val moviePoster: ImageView = view.findViewById(R.id.moviePoster)
        val movieName: TextView = view.findViewById(R.id.movieName)
        val movieRating: TextView = view.findViewById(R.id.movieRating)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_movie, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movie = movies[position]
        
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
        
        holder.itemView.setOnClickListener {
            onMovieClick(movie)
        }
    }
    
    override fun getItemCount() = movies.size
    
    fun updateMovies(newMovies: List<Movie>) {
        movies = newMovies
        notifyDataSetChanged()
    }
}
