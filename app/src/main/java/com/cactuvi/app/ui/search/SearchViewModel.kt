package com.cactuvi.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.mappers.toModel
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for Search screen. Handles search queries with debouncing and content filtering using
 * MVVM + UDF pattern.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val database: AppDatabase,
    private val repository: ContentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    private var allMovies: List<Movie> = emptyList()
    private var allSeries: List<Series> = emptyList()

    init {
        setupDebouncedSearch()
        loadCachedData()
    }

    private fun loadCachedData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Load cached movies
                val movieEntities = database.movieDao().getAll()
                allMovies = movieEntities.map { it.toModel() }

                // Load cached series
                val seriesEntities = database.seriesDao().getAll()
                allSeries = seriesEntities.map { it.toModel() }
            }

            // Trigger background refresh if cache is empty
            if (allMovies.isEmpty()) {
                repository.loadMovies()
            }
            if (allSeries.isEmpty()) {
                repository.loadSeries()
            }
        }
    }

    private fun setupDebouncedSearch() {
        viewModelScope.launch {
            _queryFlow
                .debounce(300) // 300ms debounce
                .collect { query -> performSearch(query) }
        }
    }

    fun updateQuery(query: String) {
        _queryFlow.value = query
        _uiState.update { it.copy(query = query) }
    }

    fun setContentType(contentType: String) {
        _uiState.update { it.copy(contentType = contentType) }
        // Re-run search with new content type
        performSearch(_queryFlow.value)
    }

    private fun performSearch(query: String) {
        if (query.length < 2) {
            _uiState.update {
                it.copy(
                    results = null,
                    isEmpty = true,
                    emptyMessage = "Type at least 2 characters to search",
                )
            }
            return
        }

        val lowerQuery = query.lowercase()
        val contentType = _uiState.value.contentType

        when (contentType) {
            SearchActivity.TYPE_MOVIES -> {
                val matchedMovies = allMovies.filter { it.name.lowercase().contains(lowerQuery) }
                if (matchedMovies.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            results = SearchResults.MovieList(matchedMovies),
                            isEmpty = false,
                            emptyMessage = "",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            results = null,
                            isEmpty = true,
                            emptyMessage = "No movies found",
                        )
                    }
                }
            }
            SearchActivity.TYPE_SERIES -> {
                val matchedSeries = allSeries.filter { it.name.lowercase().contains(lowerQuery) }
                if (matchedSeries.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            results = SearchResults.SeriesList(matchedSeries),
                            isEmpty = false,
                            emptyMessage = "",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            results = null,
                            isEmpty = true,
                            emptyMessage = "No series found",
                        )
                    }
                }
            }
            SearchActivity.TYPE_ALL -> {
                val matchedMovies = allMovies.filter { it.name.lowercase().contains(lowerQuery) }
                val matchedSeries = allSeries.filter { it.name.lowercase().contains(lowerQuery) }

                when {
                    matchedMovies.isNotEmpty() -> {
                        _uiState.update {
                            it.copy(
                                results = SearchResults.MovieList(matchedMovies),
                                isEmpty = false,
                                emptyMessage = "",
                            )
                        }
                    }
                    matchedSeries.isNotEmpty() -> {
                        _uiState.update {
                            it.copy(
                                results = SearchResults.SeriesList(matchedSeries),
                                isEmpty = false,
                                emptyMessage = "",
                            )
                        }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                results = null,
                                isEmpty = true,
                                emptyMessage = "No results found",
                            )
                        }
                    }
                }
            }
        }
    }
}
