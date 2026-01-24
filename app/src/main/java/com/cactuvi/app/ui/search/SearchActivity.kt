package com.cactuvi.app.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.data.db.mappers.toModel
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.ui.common.MovieAdapter
import com.cactuvi.app.ui.common.SeriesAdapter
import com.cactuvi.app.ui.detail.MovieDetailActivity
import com.cactuvi.app.ui.detail.SeriesDetailActivity
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.SourceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_CONTENT_TYPE = "content_type"
        const val TYPE_ALL = "all"
        const val TYPE_MOVIES = "movies"
        const val TYPE_SERIES = "series"
        const val TYPE_LIVE = "live"
    }
    
    private lateinit var repository: ContentRepository
    
    private lateinit var modernToolbar: ModernToolbar
    private lateinit var searchView: SearchView
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyText: TextView
    
    private var searchJob: Job? = null
    private var allMovies: List<Movie> = emptyList()
    private var allSeries: List<Series> = emptyList()
    private var contentTypeFilter: String = TYPE_ALL
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        
        contentTypeFilter = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: TYPE_ALL
        
        repository = ContentRepository(
            SourceManager.getInstance(this),
            this
        )
        
        initViews()
        setupSearch()
        loadAllContent()
    }
    
    private fun initViews() {
        modernToolbar = findViewById(R.id.modernToolbar)
        searchView = findViewById(R.id.searchView)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        emptyText = findViewById(R.id.emptyText)
        
        modernToolbar.onBackClick = { finish() }
        
        resultsRecyclerView.layoutManager = GridLayoutManager(this, 3)
    }
    
    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { performSearch(it) }
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    newText?.let { 
                        if (it.length >= 2) {
                            performSearch(it)
                        } else {
                            showEmptyState(getString(R.string.search_placeholder))
                        }
                    }
                }
                return true
            }
        })
    }
    
    private fun loadAllContent() {
        lifecycleScope.launch {
            // Load content from database cache (fast, no API call)
            // Trigger background refresh if needed
            when (contentTypeFilter) {
                TYPE_MOVIES, TYPE_ALL -> {
                    // Trigger background load (if not already loaded)
                    repository.loadMovies(forceRefresh = false)
                    // Load from database for search
                    val database = com.cactuvi.app.data.db.AppDatabase.getInstance(this@SearchActivity)
                    val movieEntities = database.movieDao().getAll()
                    allMovies = movieEntities.map { it.toModel() }
                }
            }
            
            when (contentTypeFilter) {
                TYPE_SERIES, TYPE_ALL -> {
                    // Trigger background load (if not already loaded)
                    repository.loadSeries(forceRefresh = false)
                    // Load from database for search
                    val database = com.cactuvi.app.data.db.AppDatabase.getInstance(this@SearchActivity)
                    val seriesEntities = database.seriesDao().getAll()
                    allSeries = seriesEntities.map { it.toModel() }
                }
            }
            
            // TODO: Add live channels support when needed
        }
    }
    
    private fun performSearch(query: String) {
        val lowerQuery = query.lowercase()
        
        // Search based on content type filter
        when (contentTypeFilter) {
            TYPE_MOVIES -> {
                val matchedMovies = allMovies.filter { 
                    it.name.lowercase().contains(lowerQuery)
                }
                if (matchedMovies.isNotEmpty()) {
                    displayMovies(matchedMovies)
                } else {
                    showEmptyState(getString(R.string.no_results))
                }
            }
            TYPE_SERIES -> {
                val matchedSeries = allSeries.filter {
                    it.name.lowercase().contains(lowerQuery)
                }
                if (matchedSeries.isNotEmpty()) {
                    displaySeries(matchedSeries)
                } else {
                    showEmptyState(getString(R.string.no_results))
                }
            }
            TYPE_ALL -> {
                // Search in movies
                val matchedMovies = allMovies.filter { 
                    it.name.lowercase().contains(lowerQuery)
                }
                
                // Search in series
                val matchedSeries = allSeries.filter {
                    it.name.lowercase().contains(lowerQuery)
                }
                
                // Show movies if found, otherwise show series
                when {
                    matchedMovies.isNotEmpty() -> {
                        displayMovies(matchedMovies)
                    }
                    matchedSeries.isNotEmpty() -> {
                        displaySeries(matchedSeries)
                    }
                    else -> {
                        showEmptyState(getString(R.string.no_results))
                    }
                }
            }
            else -> showEmptyState(getString(R.string.no_results))
        }
    }
    
    private fun displayMovies(movies: List<Movie>) {
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
        progressBar.visibility = View.GONE
    }
    
    private fun displaySeries(series: List<Series>) {
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
        progressBar.visibility = View.GONE
    }
    
    private fun showEmptyState(message: String) {
        emptyText.text = message
        emptyState.visibility = View.VISIBLE
        resultsRecyclerView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }
}
