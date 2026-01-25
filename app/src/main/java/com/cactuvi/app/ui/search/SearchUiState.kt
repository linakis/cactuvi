package com.cactuvi.app.ui.search

import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.Series

/** UI state for Search screen. Single source of truth for all search-related UI data. */
data class SearchUiState(
    val query: String = "",
    val contentType: String = SearchActivity.TYPE_ALL,
    val results: SearchResults? = null,
    val isEmpty: Boolean = true,
    val emptyMessage: String = "Type at least 2 characters to search",
)

/** Sealed class representing search results. Can be movies or series results. */
sealed class SearchResults {
    data class MovieList(val movies: List<Movie>) : SearchResults()

    data class SeriesList(val series: List<Series>) : SearchResults()
}
