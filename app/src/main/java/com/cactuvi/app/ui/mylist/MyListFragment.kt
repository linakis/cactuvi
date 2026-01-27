package com.cactuvi.app.ui.mylist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.data.db.entities.FavoriteEntity
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.ui.common.LiveChannelAdapter
import com.cactuvi.app.ui.common.MovieAdapter
import com.cactuvi.app.ui.common.SeriesAdapter
import com.cactuvi.app.ui.detail.MovieDetailActivity
import com.cactuvi.app.ui.detail.SeriesDetailActivity
import com.cactuvi.app.ui.player.PlayerActivity
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.StreamUrlBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyListFragment : Fragment() {

    private val viewModel: MyListViewModel by activityViewModels()
    @Inject lateinit var credentialsManager: CredentialsManager
    private var contentType: String? = null

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
        observeUiState()

        return view
    }

    override fun onResume() {
        super.onResume()
        // Load favorites for this content type
        viewModel.loadFavorites(contentType)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state -> renderUiState(state) }
            }
        }
    }

    private fun renderUiState(state: MyListUiState) {
        // Error state
        state.error?.let { errorMsg ->
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
        }

        // Filter favorites by content type
        val filteredFavorites =
            if (contentType != null) {
                state.favorites.filter { it.contentType == contentType }
            } else {
                state.favorites
            }

        // Show/hide UI
        if (filteredFavorites.isEmpty()) {
            showEmptyState()
        } else {
            displayFavorites(filteredFavorites)
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
        val channels =
            favorites.map { favorite ->
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
                    tvArchiveDuration = null,
                )
            }

        val adapter = LiveChannelAdapter(channels) { channel -> playLiveChannel(channel) }
        recyclerView.adapter = adapter
    }

    private fun displayMovies(favorites: List<FavoriteEntity>) {
        // Convert FavoriteEntity to Movie
        val movies =
            favorites.map { favorite ->
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
                    directSource = null,
                )
            }

        val adapter = MovieAdapter(movies) { movie -> openMovieDetail(movie) }
        recyclerView.adapter = adapter
    }

    private fun displaySeries(favorites: List<FavoriteEntity>) {
        // Convert FavoriteEntity to Series
        val seriesList =
            favorites.map { favorite ->
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
                    categoryId = "",
                )
            }

        val adapter = SeriesAdapter(seriesList) { series -> openSeriesDetail(series) }
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
        val streamUrl =
            StreamUrlBuilder.buildLiveUrl(
                server = credentialsManager.getServer(),
                username = credentialsManager.getUsername(),
                password = credentialsManager.getPassword(),
                streamId = channel.streamId,
                extension = "ts",
            )

        val intent =
            Intent(requireContext(), PlayerActivity::class.java).apply {
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
        val intent =
            Intent(requireContext(), MovieDetailActivity::class.java).apply {
                putExtra("VOD_ID", movie.streamId)
                putExtra("STREAM_ID", movie.streamId)
                putExtra("TITLE", movie.name)
                putExtra("POSTER_URL", movie.streamIcon)
                putExtra("CONTAINER_EXTENSION", movie.containerExtension)
            }
        startActivity(intent)
    }

    private fun openSeriesDetail(series: Series) {
        val intent =
            Intent(requireContext(), SeriesDetailActivity::class.java).apply {
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
