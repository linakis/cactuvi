package com.cactuvi.app.ui.search

import app.cash.turbine.test
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.dao.MovieDao
import com.cactuvi.app.data.db.dao.SeriesDao
import com.cactuvi.app.data.db.entities.MovieEntity
import com.cactuvi.app.data.db.entities.SeriesEntity
import com.cactuvi.app.domain.repository.ContentRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SearchViewModel context-aware search functionality.
 *
 * Tests verify that search respects navigation context:
 * - Home: Search all content (movies + series in sections)
 * - Root Movies: Search all movies only
 * - Group: Search within that group's categories
 * - Category: Search within that category only
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var viewModel: SearchViewModel
    private lateinit var database: AppDatabase
    private lateinit var movieDao: MovieDao
    private lateinit var seriesDao: SeriesDao
    private lateinit var repository: ContentRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock DAOs
        movieDao = mockk(relaxed = true)
        seriesDao = mockk(relaxed = true)

        // Mock database
        database =
            mockk(relaxed = true) {
                coEvery { movieDao() } returns movieDao
                coEvery { seriesDao() } returns seriesDao
            }

        // Mock repository
        repository = mockk(relaxed = true)

        viewModel = SearchViewModel(database, repository)
    }

    // ========== SEARCH FROM HOME (ALL) ==========

    @Test
    fun `should return multi-section results when searching from home`() = runTest {
        // Given: Mock data
        val movieEntities =
            listOf(
                MovieEntity(
                    streamId = 1,
                    name = "The Matrix",
                    categoryId = "1",
                    num = 1,
                    streamType = null,
                    streamIcon = "",
                    rating = null,
                    rating5Based = 0.0,
                    added = null,
                    containerExtension = "mp4",
                    customSid = null,
                    directSource = null,
                    categoryName = "Action",
                    lastUpdated = 0L,
                    sourceId = "test"
                )
            )
        val seriesEntities =
            listOf(
                SeriesEntity(
                    seriesId = 1,
                    name = "Breaking Bad",
                    categoryId = "2",
                    num = 1,
                    cover = "",
                    plot = null,
                    cast = null,
                    director = null,
                    genre = null,
                    releaseDate = null,
                    lastModified = null,
                    rating = null,
                    rating5Based = 0.0,
                    backdropPath = null,
                    youtubeTrailer = null,
                    episodeRunTime = null,
                    categoryName = "Drama",
                    lastUpdated = 0L,
                    sourceId = "test"
                )
            )

        coEvery { movieDao.searchByName("matrix") } returns movieEntities
        coEvery { seriesDao.searchByName("matrix") } returns emptyList()

        coEvery { movieDao.searchByName("breaking") } returns emptyList()
        coEvery { seriesDao.searchByName("breaking") } returns seriesEntities

        // When: Search from home (TYPE_ALL)
        val context = SearchContext(contentType = SearchActivity.TYPE_ALL)
        viewModel.setSearchContext(context)
        viewModel.updateQuery("matrix")

        advanceUntilIdle()

        // Then: Should return multi-section with movies
        viewModel.uiState.test {
            val state = awaitItem()
            val results = state.results
            assertTrue(results is SearchResults.MultiSection)
            val multiSection = results as SearchResults.MultiSection
            assertEquals(1, multiSection.movies.size)
            assertEquals("The Matrix", multiSection.movies[0].name)
            assertEquals(0, multiSection.series.size)
        }
    }

    @Test
    fun `should combine movies and series in multi-section when both match`() = runTest {
        // Given: Both movies and series match query
        val movieEntities =
            listOf(
                MovieEntity(
                    streamId = 1,
                    name = "Action Movie",
                    categoryId = "1",
                    num = 1,
                    streamType = null,
                    streamIcon = "",
                    rating = null,
                    rating5Based = 0.0,
                    added = null,
                    containerExtension = "mp4",
                    customSid = null,
                    directSource = null,
                    categoryName = "Action",
                    lastUpdated = 0L,
                    sourceId = "test"
                )
            )
        val seriesEntities =
            listOf(
                SeriesEntity(
                    seriesId = 1,
                    name = "Action Series",
                    categoryId = "2",
                    num = 1,
                    cover = "",
                    plot = null,
                    cast = null,
                    director = null,
                    genre = null,
                    releaseDate = null,
                    lastModified = null,
                    rating = null,
                    rating5Based = 0.0,
                    backdropPath = null,
                    youtubeTrailer = null,
                    episodeRunTime = null,
                    categoryName = "Action",
                    lastUpdated = 0L,
                    sourceId = "test"
                )
            )

        coEvery { movieDao.searchByName("action") } returns movieEntities
        coEvery { seriesDao.searchByName("action") } returns seriesEntities

        // When: Search from home
        val context = SearchContext(contentType = SearchActivity.TYPE_ALL)
        viewModel.setSearchContext(context)
        viewModel.updateQuery("action")

        advanceUntilIdle()

        // Then: Should have both sections
        viewModel.uiState.test {
            val state = awaitItem()
            val results = state.results as SearchResults.MultiSection
            assertEquals(1, results.movies.size)
            assertEquals(1, results.series.size)
        }
    }

    // ========== SEARCH IN SPECIFIC CONTENT TYPE ==========

    @Test
    fun `should search only movies when in movies section`() = runTest {
        // Given: Movies data
        val movieEntities =
            listOf(
                MovieEntity(
                    streamId = 1,
                    name = "Test Movie",
                    categoryId = "1",
                    num = 1,
                    streamType = null,
                    streamIcon = "",
                    rating = null,
                    rating5Based = 0.0,
                    added = null,
                    containerExtension = "mp4",
                    customSid = null,
                    directSource = null,
                    categoryName = "Test",
                    lastUpdated = 0L,
                    sourceId = "test"
                )
            )

        coEvery { movieDao.searchByName("test") } returns movieEntities

        // When: Search in movies section
        val context = SearchContext(contentType = SearchActivity.TYPE_MOVIES)
        viewModel.setSearchContext(context)
        viewModel.updateQuery("test")

        advanceUntilIdle()

        // Then: Should return movie list only
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.results is SearchResults.MovieList)
            val movieList = state.results as SearchResults.MovieList
            assertEquals(1, movieList.movies.size)
        }
    }

    // ========== SEARCH IN GROUP ==========

    @Test
    fun `should search within group when grouping is enabled`() = runTest {
        // Given: Movies in specific group
        val movieEntities =
            listOf(
                MovieEntity(
                    streamId = 1,
                    name = "Action Movie",
                    categoryId = "action-category-1",
                    num = 1,
                    streamType = null,
                    streamIcon = "",
                    rating = null,
                    rating5Based = 0.0,
                    added = null,
                    containerExtension = "mp4",
                    customSid = null,
                    directSource = null,
                    categoryName = "Action",
                    lastUpdated = 0L,
                    sourceId = "test"
                )
            )

        coEvery { movieDao.searchByNameInGroup("movie", "Action-%") } returns movieEntities

        // When: Search in group with separator
        val context =
            SearchContext(
                contentType = SearchActivity.TYPE_MOVIES,
                groupName = "Action",
                groupingEnabled = true,
                groupingSeparator = "-"
            )
        viewModel.setSearchContext(context)
        viewModel.updateQuery("movie")

        advanceUntilIdle()

        // Then: Should search in group only
        viewModel.uiState.test {
            val state = awaitItem()
            val results = state.results as SearchResults.MovieList
            assertEquals(1, results.movies.size)
            assertEquals("Action Movie", results.movies[0].name)
        }
    }

    // ========== SEARCH IN CATEGORY ==========

    @Test
    fun `should search within category when category context is provided`() = runTest {
        // Given: Movies in specific category
        val movieEntities =
            listOf(
                MovieEntity(
                    streamId = 1,
                    name = "Category Movie",
                    categoryId = "cat-123",
                    num = 1,
                    streamType = null,
                    streamIcon = "",
                    rating = null,
                    rating5Based = 0.0,
                    added = null,
                    containerExtension = "mp4",
                    customSid = null,
                    directSource = null,
                    categoryName = "Test Category",
                    lastUpdated = 0L,
                    sourceId = "test"
                )
            )

        coEvery { movieDao.searchByNameInCategory("movie", "cat-123") } returns movieEntities

        // When: Search in category
        val context =
            SearchContext(
                contentType = SearchActivity.TYPE_MOVIES,
                categoryId = "cat-123",
                categoryName = "Test Category"
            )
        viewModel.setSearchContext(context)
        viewModel.updateQuery("movie")

        advanceUntilIdle()

        // Then: Should search in category only
        viewModel.uiState.test {
            val state = awaitItem()
            val results = state.results as SearchResults.MovieList
            assertEquals(1, results.movies.size)
            assertEquals("cat-123", results.movies[0].categoryId)
        }
    }

    // ========== EMPTY RESULTS ==========

    @Test
    fun `should show empty state when no results found`() = runTest {
        // Given: No matching data
        coEvery { movieDao.searchByName("nonexistent") } returns emptyList()
        coEvery { seriesDao.searchByName("nonexistent") } returns emptyList()

        // When: Search
        val context = SearchContext(contentType = SearchActivity.TYPE_ALL)
        viewModel.setSearchContext(context)
        viewModel.updateQuery("nonexistent")

        advanceUntilIdle()

        // Then: Should show empty state
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isEmpty)
            assertEquals("No results found", state.emptyMessage)
        }
    }

    // ========== QUERY VALIDATION ==========

    @Test
    fun `should require at least 2 characters to search`() = runTest {
        // When: Query too short
        viewModel.updateQuery("a")
        advanceUntilIdle()

        // Then: Should show validation message
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isEmpty)
            assertEquals("Type at least 2 characters to search", state.emptyMessage)
        }
    }
}
