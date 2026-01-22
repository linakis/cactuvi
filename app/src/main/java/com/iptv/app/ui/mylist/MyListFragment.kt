package com.iptv.app.ui.mylist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iptv.app.R
import com.iptv.app.data.db.entities.FavoriteEntity
import com.iptv.app.data.models.LiveChannel
import com.iptv.app.data.models.Movie
import com.iptv.app.data.models.Series
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.ui.common.LiveChannelAdapter
import com.iptv.app.ui.common.MovieAdapter
import com.iptv.app.ui.common.SeriesAdapter
import com.iptv.app.ui.detail.MovieDetailActivity
import com.iptv.app.ui.detail.SeriesDetailActivity
import com.iptv.app.ui.player.PlayerActivity
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.SourceManager
import com.iptv.app.utils.StreamUrlBuilder
import kotlinx.coroutines.launch

class MyListFragment : Fragment() {
    
    private var contentType: String? = null
    private lateinit var repository: ContentRepository
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    
    companion object {
        private const val ARG_CONTENT_TYPE = "content_type"
        
        fun newInstance(contentType: String?): MyListFragment {
            val fragment = MyListFragment()
            val args = Bundle()
            args.putString(ARG_CONTENT_TYPE, contentType)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentType = arguments?.getString(ARG_CONTENT_TYPE)
        repository = ContentRepository(
            SourceManager.getInstance(requireContext()),
            requireContext()
        )
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_list, container, false)
        
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyText = view.findViewById(R.id.emptyText)
        
        setupRecyclerView()
        loadFavorites()
        
        return view
    }
    
    override fun onResume() {
        super.onResume()
        // Reload favorites when returning to this fragment
        loadFavorites()
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
    }
    
    private fun loadFavorites() {
        lifecycleScope.launch {
            val result = repository.getFavorites(contentType)
            
            if (result.isSuccess) {
                val favorites = result.getOrNull() ?: emptyList()
                
                if (favorites.isEmpty()) {
                    showEmptyState()
                } else {
                    displayFavorites(favorites)
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Failed to load favorites",
                    Toast.LENGTH_SHORT
                ).show()
                showEmptyState()
            }
        }
    }
    
    private fun displayFavorites(favorites: List<FavoriteEntity>) {
        when (contentType) {
            "live_channel" -> displayLiveChannels(favorites)
            "movie" -> displayMovies(favorites)
            "series" -> displaySeries(favorites)
            else -> displayMixed(favorites)
        }
        
        recyclerView.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
    }
    
    private fun displayLiveChannels(favorites: List<FavoriteEntity>) {
        // Convert FavoriteEntity to LiveChannel
        val channels = favorites.map { favorite ->
            LiveChannel(
                num = 0,
                name = favorite.contentName,
                streamType = "live",
                streamId = favorite.contentId.toIntOrNull() ?: 0,
                streamIcon = favorite.posterUrl,
                epgChannelId = null,
                added = null,
                categoryId = "",
                customSid = null,
                tvArchive = null,
                directSource = null,
                tvArchiveDuration = null
            )
        }
        
        val adapter = LiveChannelAdapter(channels) { channel ->
            playLiveChannel(channel)
        }
        recyclerView.adapter = adapter
    }
    
    private fun displayMovies(favorites: List<FavoriteEntity>) {
        // Convert FavoriteEntity to Movie
        val movies = favorites.map { favorite ->
            Movie(
                num = 0,
                name = favorite.contentName,
                streamType = "movie",
                streamId = favorite.contentId.toIntOrNull() ?: 0,
                streamIcon = favorite.posterUrl,
                rating = favorite.rating,
                rating5Based = null,
                added = null,
                categoryId = "",
                containerExtension = "mp4",
                customSid = null,
                directSource = null
            )
        }
        
        val adapter = MovieAdapter(movies) { movie ->
            openMovieDetail(movie)
        }
        recyclerView.adapter = adapter
    }
    
    private fun displaySeries(favorites: List<FavoriteEntity>) {
        // Convert FavoriteEntity to Series
        val seriesList = favorites.map { favorite ->
            Series(
                num = 0,
                name = favorite.contentName,
                seriesId = favorite.contentId.toIntOrNull() ?: 0,
                cover = favorite.posterUrl,
                plot = null,
                cast = null,
                director = null,
                genre = null,
                releaseDate = null,
                lastModified = null,
                rating = favorite.rating,
                rating5Based = null,
                backdropPath = null,
                youtubeTrailer = null,
                episodeRunTime = null,
                categoryId = ""
            )
        }
        
        val adapter = SeriesAdapter(seriesList) { series ->
            openSeriesDetail(series)
        }
        recyclerView.adapter = adapter
    }
    
    private fun displayMixed(favorites: List<FavoriteEntity>) {
        // Group by type and display in sections
        // For simplicity, we'll just show all movies first, then series, then channels
        val movies = favorites.filter { it.contentType == "movie" }
        val series = favorites.filter { it.contentType == "series" }
        val channels = favorites.filter { it.contentType == "live_channel" }
        
        // For now, just display the most common type
        when {
            movies.size >= series.size && movies.size >= channels.size -> displayMovies(movies)
            series.size >= channels.size -> displaySeries(series)
            else -> displayLiveChannels(channels)
        }
    }
    
    private fun playLiveChannel(channel: LiveChannel) {
        val credentials = CredentialsManager.getInstance(requireContext())
        if (credentials == null) {
            Toast.makeText(requireContext(), "No credentials found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val streamUrl = StreamUrlBuilder.buildLiveUrl(
            server = credentials.getServer(),
            username = credentials.getUsername(),
            password = credentials.getPassword(),
            streamId = channel.streamId,
            extension = "ts"
        )
        
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("TITLE", channel.name)
            putExtra("CONTENT_ID", channel.streamId.toString())
            putExtra("CONTENT_TYPE", "live_channel")
            putExtra("POSTER_URL", channel.streamIcon)
            putExtra("RESUME_POSITION", 0L)
        }
        startActivity(intent)
    }
    
    private fun openMovieDetail(movie: Movie) {
        val intent = Intent(requireContext(), MovieDetailActivity::class.java).apply {
            putExtra("VOD_ID", movie.streamId)
            putExtra("STREAM_ID", movie.streamId)
            putExtra("TITLE", movie.name)
            putExtra("POSTER_URL", movie.streamIcon)
            putExtra("CONTAINER_EXTENSION", movie.containerExtension)
        }
        startActivity(intent)
    }
    
    private fun openSeriesDetail(series: Series) {
        val intent = Intent(requireContext(), SeriesDetailActivity::class.java).apply {
            putExtra("SERIES_ID", series.seriesId)
            putExtra("TITLE", series.name)
            putExtra("COVER_URL", series.cover)
        }
        startActivity(intent)
    }
    
    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = getString(R.string.no_content)
    }
}
