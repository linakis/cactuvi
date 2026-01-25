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
        
        val contentTypeFilter = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: TYPE_ALL
        viewModel.setContentType(contentTypeFilter)
        
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
        
        resultsRecyclerView.layoutManager = GridLayoutManager(this, 3)
    }
    
    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.updateQuery(it) }
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.updateQuery(newText ?: "")
                return true
            }
        })
    }
    
    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { uiState ->
                renderUiState(uiState)
            }
        }
    }
    
    private fun renderUiState(uiState: SearchUiState) {
        when {
            uiState.isEmpty -> {
                showEmptyState(uiState.emptyMessage)
            }
            else -> {
                when (val results = uiState.results) {
                    is SearchResults.MovieList -> displayMovies(results.movies)
                    is SearchResults.SeriesList -> displaySeries(results.series)
                    null -> showEmptyState(uiState.emptyMessage)
                }
            }
        }
    }
    
    private fun displayMovies(movies: List<com.cactuvi.app.data.models.Movie>) {
        val adapter = MovieAdapter(movies) { movie ->
            val intent = Intent(this, MovieDetailActivity::class.java).apply {
                putExtra("VOD_ID", movie.streamId)
                putExtra("STREAM_ID", movie.streamId)
                putExtra("TITLE", movie.name)
                putExtra("POSTER_URL", movie.streamIcon)
                putExtra("CONTAINER_EXTENSION", movie.containerExtension)
            }
            startActivity(intent)
        }
        resultsRecyclerView.adapter = adapter
        
        resultsRecyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }
    
    private fun displaySeries(series: List<com.cactuvi.app.data.models.Series>) {
        val adapter = SeriesAdapter(series) { seriesItem ->
            val intent = Intent(this, SeriesDetailActivity::class.java).apply {
                putExtra("SERIES_ID", seriesItem.seriesId)
                putExtra("TITLE", seriesItem.name)
                putExtra("COVER_URL", seriesItem.cover)
            }
            startActivity(intent)
        }
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
