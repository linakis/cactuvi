package com.cactuvi.app.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.ui.common.MovieAdapter
import com.cactuvi.app.ui.common.SeriesAdapter
import com.cactuvi.app.ui.detail.MovieDetailActivity
import com.cactuvi.app.ui.detail.SeriesDetailActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONTENT_TYPE = "content_type"
        const val EXTRA_GROUP_NAME = "group_name"
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_CATEGORY_NAME = "category_name"
        const val EXTRA_GROUPING_ENABLED = "grouping_enabled"
        const val EXTRA_GROUPING_SEPARATOR = "grouping_separator"

        const val TYPE_ALL = "all"
        const val TYPE_MOVIES = "movies"
        const val TYPE_SERIES = "series"
        const val TYPE_LIVE = "live"
    }

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var modernToolbar: ModernToolbar
    private lateinit var searchView: SearchView
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // Extract search context from intent
        val contentType = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: TYPE_ALL
        val groupName = intent.getStringExtra(EXTRA_GROUP_NAME)
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME)
        val groupingEnabled = intent.getBooleanExtra(EXTRA_GROUPING_ENABLED, false)
        val groupingSeparator = intent.getStringExtra(EXTRA_GROUPING_SEPARATOR) ?: "-"

        val searchContext =
            SearchContext(
                contentType = contentType,
                groupName = groupName,
                categoryId = categoryId,
                categoryName = categoryName,
                groupingEnabled = groupingEnabled,
                groupingSeparator = groupingSeparator
            )

        viewModel.setSearchContext(searchContext)

        initViews()
        setupSearch()
        observeUiState()
    }

    private fun initViews() {
        modernToolbar = findViewById(R.id.modernToolbar)
        searchView = findViewById(R.id.searchView)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)
        emptyState = findViewById(R.id.emptyState)
        emptyText = findViewById(R.id.emptyText)

        modernToolbar.onBackClick = { finish() }

        // Update toolbar title based on context
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { uiState ->
                val context = uiState.context
                val titleParts = mutableListOf<String>()

                when (context.contentType) {
                    TYPE_MOVIES -> titleParts.add("Movies")
                    TYPE_SERIES -> titleParts.add("Series")
                    TYPE_LIVE -> titleParts.add("Live TV")
                    TYPE_ALL -> titleParts.add("All")
                }

                context.groupName?.let { titleParts.add(it) }
                context.categoryName?.let { titleParts.add(it) }

                modernToolbar.title = "Search: ${titleParts.joinToString(" > ")}"
            }
        }

        resultsRecyclerView.layoutManager = GridLayoutManager(this, 3)
    }

    private fun setupSearch() {
        // Request focus and show keyboard immediately
        searchView.requestFocus()
        searchView.isIconified = false

        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { viewModel.updateQuery(it) }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.updateQuery(newText ?: "")
                    return true
                }
            }
        )
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { uiState -> renderUiState(uiState) }
        }
    }

    private fun renderUiState(uiState: SearchUiState) {
        when {
            uiState.isEmpty -> {
                showEmptyState(uiState.emptyMessage)
            }
            else -> {
                when (val results = uiState.results) {
                    is SearchResults.MultiSection -> displayMultiSection(results)
                    is SearchResults.MovieList -> displayMovies(results.movies)
                    is SearchResults.SeriesList -> displaySeries(results.series)
                    null -> showEmptyState(uiState.emptyMessage)
                }
            }
        }
    }

    private fun displayMultiSection(results: SearchResults.MultiSection) {
        val adapter =
            SectionedSearchAdapter(
                onMovieClick = { movie -> navigateToMovieDetail(movie) },
                onSeriesClick = { series -> navigateToSeriesDetail(series) }
            )
        adapter.updateResults(results.movies, results.series)
        resultsRecyclerView.adapter = adapter

        resultsRecyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun navigateToMovieDetail(movie: com.cactuvi.app.data.models.Movie) {
        val intent =
            Intent(this, MovieDetailActivity::class.java).apply {
                putExtra("VOD_ID", movie.streamId)
                putExtra("STREAM_ID", movie.streamId)
                putExtra("TITLE", movie.name)
                putExtra("POSTER_URL", movie.streamIcon)
                putExtra("CONTAINER_EXTENSION", movie.containerExtension)
            }
        startActivity(intent)
    }

    private fun navigateToSeriesDetail(seriesItem: com.cactuvi.app.data.models.Series) {
        val intent =
            Intent(this, SeriesDetailActivity::class.java).apply {
                putExtra("SERIES_ID", seriesItem.seriesId)
                putExtra("TITLE", seriesItem.name)
                putExtra("COVER_URL", seriesItem.cover)
            }
        startActivity(intent)
    }

    private fun displayMovies(movies: List<com.cactuvi.app.data.models.Movie>) {
        val adapter = MovieAdapter(movies) { movie -> navigateToMovieDetail(movie) }
        resultsRecyclerView.adapter = adapter

        resultsRecyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun displaySeries(series: List<com.cactuvi.app.data.models.Series>) {
        val adapter = SeriesAdapter(series) { seriesItem -> navigateToSeriesDetail(seriesItem) }
        resultsRecyclerView.adapter = adapter

        resultsRecyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun showEmptyState(message: String) {
        emptyText.text = message
        emptyState.visibility = View.VISIBLE
        resultsRecyclerView.visibility = View.GONE
    }
}
