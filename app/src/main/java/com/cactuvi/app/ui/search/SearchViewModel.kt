package com.cactuvi.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.mappers.toModel
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
 * ViewModel for Search screen. Handles context-aware search queries with debouncing and content
 * filtering using MVVM + UDF pattern.
 *
 * Search respects navigation context:
 * - Home: Search all movies/series/live
 * - Root Movies: Search all movies
 * - Group: Search within that group's categories
 * - Category: Search within that category only
 * - All levels respect filtering logic (grouping separator)
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

    init {
        setupDebouncedSearch()
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

    fun setSearchContext(context: SearchContext) {
        android.util.Log.d("SearchViewModel", "setSearchContext: $context")
        _uiState.update { it.copy(context = context) }
        // Re-run search with new context
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

        val context = _uiState.value.context

        viewModelScope.launch {
            val searchResults =
                withContext(Dispatchers.IO) {
                    when (context.contentType) {
                        SearchActivity.TYPE_MOVIES -> searchMovies(query, context)
                        SearchActivity.TYPE_SERIES -> searchSeries(query, context)
                        SearchActivity.TYPE_LIVE -> searchLive(query, context)
                        SearchActivity.TYPE_ALL -> searchAll(query, context)
                        else -> null
                    }
                }

            if (searchResults != null && !searchResults.isEmpty()) {
                _uiState.update {
                    it.copy(
                        results = searchResults,
                        isEmpty = false,
                        emptyMessage = "",
                    )
                }
            } else {
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

    private suspend fun searchMovies(query: String, context: SearchContext): SearchResults? {
        android.util.Log.d("SearchViewModel", "searchMovies: query='$query', context=$context")

        val movieEntities =
            when {
                // Specific category scope
                context.categoryId != null -> {
                    android.util.Log.d(
                        "SearchViewModel",
                        "Searching in category: ${context.categoryId}"
                    )
                    database.movieDao().searchByNameInCategory(query, context.categoryId)
                }
                // Group scope (use grouping separator to filter categories)
                context.groupName != null && context.groupingEnabled -> {
                    val groupPattern = "${context.groupName}${context.groupingSeparator}%"
                    android.util.Log.d(
                        "SearchViewModel",
                        "Searching in group with pattern: $groupPattern"
                    )
                    database.movieDao().searchByNameInGroup(query, groupPattern)
                }
                // Root movies scope (all movies)
                else -> {
                    android.util.Log.d("SearchViewModel", "Searching all movies")
                    database.movieDao().searchByName(query)
                }
            }

        android.util.Log.d("SearchViewModel", "Found ${movieEntities.size} movie entities")
        val movies = movieEntities.map { it.toModel() }
        return if (movies.isNotEmpty()) SearchResults.MovieList(movies) else null
    }

    private suspend fun searchSeries(query: String, context: SearchContext): SearchResults? {
        val seriesEntities =
            when {
                // Specific category scope
                context.categoryId != null -> {
                    database.seriesDao().searchByNameInCategory(query, context.categoryId)
                }
                // Group scope (use grouping separator to filter categories)
                context.groupName != null && context.groupingEnabled -> {
                    val groupPattern = "${context.groupName}${context.groupingSeparator}%"
                    database.seriesDao().searchByNameInGroup(query, groupPattern)
                }
                // Root series scope (all series)
                else -> {
                    database.seriesDao().searchByName(query)
                }
            }

        val series = seriesEntities.map { it.toModel() }
        return if (series.isNotEmpty()) SearchResults.SeriesList(series) else null
    }

    private suspend fun searchLive(query: String, context: SearchContext): SearchResults? {
        // Live channels don't have a display model yet, so we'll return null for now
        // TODO: Add LiveChannelList to SearchResults sealed class when live TV search is needed
        return null
    }

    private suspend fun searchAll(query: String, context: SearchContext): SearchResults? {
        // When searching "all", return multi-section results with movies AND series
        val moviesResult = searchMovies(query, context)
        val seriesResult = searchSeries(query, context)

        val movies =
            when (moviesResult) {
                is SearchResults.MovieList -> moviesResult.movies
                else -> emptyList()
            }

        val series =
            when (seriesResult) {
                is SearchResults.SeriesList -> seriesResult.series
                else -> emptyList()
            }

        if (movies.isEmpty() && series.isEmpty()) {
            return null
        }

        return SearchResults.MultiSection(movies = movies, series = series)
    }

    private fun SearchResults?.isEmpty(): Boolean {
        return when (this) {
            is SearchResults.MovieList -> movies.isEmpty()
            is SearchResults.SeriesList -> series.isEmpty()
            is SearchResults.MultiSection -> isEmpty()
            null -> true
        }
    }
}
