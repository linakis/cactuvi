package com.cactuvi.app.ui.search

import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.models.Series

/**
 * Search context representing the current navigation position when search was invoked. Used to
 * scope search results to specific groups, categories, and apply filtering logic.
 */
data class SearchContext(
    val contentType: String,
    val groupName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val groupingEnabled: Boolean = false,
    val groupingSeparator: String = "-",
)

/** UI state for Search screen. Single source of truth for all search-related UI data. */
data class SearchUiState(
    val query: String = "",
    val context: SearchContext = SearchContext(contentType = SearchActivity.TYPE_ALL),
    val results: SearchResults? = null,
    val isEmpty: Boolean = true,
    val emptyMessage: String = "Type at least 2 characters to search",
)

/** Sealed class representing search results. Can be single-type or multi-section results. */
sealed class SearchResults {
    /** Single content type results */
    data class MovieList(val movies: List<Movie>) : SearchResults()

    data class SeriesList(val series: List<Series>) : SearchResults()

    /** Multi-section results (for "all" search from home) */
    data class MultiSection(
        val movies: List<Movie> = emptyList(),
        val series: List<Series> = emptyList(),
    ) : SearchResults() {
        fun isEmpty(): Boolean = movies.isEmpty() && series.isEmpty()
    }
}
