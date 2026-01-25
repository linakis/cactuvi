package com.cactuvi.app.domain.usecase

import com.cactuvi.app.data.models.MovieDetails
import com.cactuvi.app.data.models.MovieInfo
import com.cactuvi.app.data.models.SeriesInfo
import com.cactuvi.app.domain.repository.ContentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GetMovieInfoUseCase and GetSeriesInfoUseCase.
 *
 * Tests cover:
 * - Successful info retrieval
 * - Error handling
 * - Parameter passing
 * - Result delegation
 */
class GetInfoUseCasesTest {

    private lateinit var mockRepository: ContentRepository
    private lateinit var getMovieInfoUseCase: GetMovieInfoUseCase
    private lateinit var getSeriesInfoUseCase: GetSeriesInfoUseCase

    @Before
    fun setup() {
        mockRepository = mockk()
        getMovieInfoUseCase = GetMovieInfoUseCase(mockRepository)
        getSeriesInfoUseCase = GetSeriesInfoUseCase(mockRepository)
    }

    // ========== GET MOVIE INFO USE CASE ==========

    @Test
    fun `GetMovieInfoUseCase returns success with movie info`() = runTest {
        val movieDetails =
            MovieDetails(
                name = "Test Movie",
                plot = "A test movie plot",
                cast = "Actor 1, Actor 2",
                director = "Director Name",
                genre = "Action",
                releaseDate = "2024-01-01",
                youtubeTrailer = "trailer123",
                rating = "8.5",
                durationSecs = 7200,
                duration = "2h 0min",
                tmdbId = "12345",
            )
        val movieInfo = MovieInfo(info = movieDetails, movieData = null)

        coEvery { mockRepository.getMovieInfo(123) } returns Result.success(movieInfo)

        val result = getMovieInfoUseCase(123)

        assertTrue(result.isSuccess)
        assertEquals("Test Movie", result.getOrNull()?.info?.name)
        assertEquals("A test movie plot", result.getOrNull()?.info?.plot)
    }

    @Test
    fun `GetMovieInfoUseCase returns failure on repository error`() = runTest {
        val exception = Exception("Network error")
        coEvery { mockRepository.getMovieInfo(456) } returns Result.failure(exception)

        val result = getMovieInfoUseCase(456)

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `GetMovieInfoUseCase passes vodId parameter correctly`() = runTest {
        val movieInfo = MovieInfo(info = null, movieData = null)
        coEvery { mockRepository.getMovieInfo(789) } returns Result.success(movieInfo)

        getMovieInfoUseCase(789)

        coVerify(exactly = 1) { mockRepository.getMovieInfo(789) }
    }

    @Test
    fun `GetMovieInfoUseCase can be called multiple times with different IDs`() = runTest {
        val movieInfo1 =
            MovieInfo(
                info =
                    MovieDetails(
                        name = "Movie 1",
                        plot = null,
                        cast = null,
                        director = null,
                        genre = null,
                        releaseDate = null,
                        youtubeTrailer = null,
                        rating = null,
                        durationSecs = null,
                        duration = null,
                        tmdbId = null
                    ),
                movieData = null,
            )
        val movieInfo2 =
            MovieInfo(
                info =
                    MovieDetails(
                        name = "Movie 2",
                        plot = null,
                        cast = null,
                        director = null,
                        genre = null,
                        releaseDate = null,
                        youtubeTrailer = null,
                        rating = null,
                        durationSecs = null,
                        duration = null,
                        tmdbId = null
                    ),
                movieData = null,
            )

        coEvery { mockRepository.getMovieInfo(1) } returns Result.success(movieInfo1)
        coEvery { mockRepository.getMovieInfo(2) } returns Result.success(movieInfo2)

        val result1 = getMovieInfoUseCase(1)
        val result2 = getMovieInfoUseCase(2)

        assertEquals("Movie 1", result1.getOrNull()?.info?.name)
        assertEquals("Movie 2", result2.getOrNull()?.info?.name)
    }

    @Test
    fun `GetMovieInfoUseCase handles null movie details`() = runTest {
        val movieInfo = MovieInfo(info = null, movieData = null)
        coEvery { mockRepository.getMovieInfo(100) } returns Result.success(movieInfo)

        val result = getMovieInfoUseCase(100)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()?.info)
    }

    // ========== GET SERIES INFO USE CASE ==========

    @Test
    fun `GetSeriesInfoUseCase returns success with series info`() = runTest {
        val seriesInfo =
            SeriesInfo(
                seasons = listOf(),
                info = null,
                episodes = mapOf(),
            )

        coEvery { mockRepository.getSeriesInfo(123) } returns Result.success(seriesInfo)

        val result = getSeriesInfoUseCase(123)

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `GetSeriesInfoUseCase returns failure on repository error`() = runTest {
        val exception = Exception("API timeout")
        coEvery { mockRepository.getSeriesInfo(456) } returns Result.failure(exception)

        val result = getSeriesInfoUseCase(456)

        assertTrue(result.isFailure)
        assertEquals("API timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `GetSeriesInfoUseCase passes seriesId parameter correctly`() = runTest {
        val seriesInfo = SeriesInfo(seasons = emptyList(), info = null, episodes = emptyMap())
        coEvery { mockRepository.getSeriesInfo(789) } returns Result.success(seriesInfo)

        getSeriesInfoUseCase(789)

        coVerify(exactly = 1) { mockRepository.getSeriesInfo(789) }
    }

    @Test
    fun `GetSeriesInfoUseCase can be called multiple times with different IDs`() = runTest {
        // Simplified test: just verify different results are returned
        val seriesInfo1 = SeriesInfo(seasons = null, info = null, episodes = emptyMap())
        val seriesInfo2 =
            SeriesInfo(seasons = null, info = null, episodes = mapOf("1" to emptyList()))

        coEvery { mockRepository.getSeriesInfo(1) } returns Result.success(seriesInfo1)
        coEvery { mockRepository.getSeriesInfo(2) } returns Result.success(seriesInfo2)

        val result1 = getSeriesInfoUseCase(1)
        val result2 = getSeriesInfoUseCase(2)

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        assertEquals(0, result1.getOrNull()?.episodes?.size)
        assertEquals(1, result2.getOrNull()?.episodes?.size)
    }

    // ========== ERROR PROPAGATION TESTS ==========

    @Test
    fun `GetMovieInfoUseCase propagates all exception types`() = runTest {
        val exceptions =
            listOf(
                Exception("Network error"),
                IllegalArgumentException("Invalid ID"),
                RuntimeException("Unexpected error"),
            )

        exceptions.forEach { exception ->
            coEvery { mockRepository.getMovieInfo(any()) } returns Result.failure(exception)

            val result = getMovieInfoUseCase(123)

            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `GetSeriesInfoUseCase propagates all exception types`() = runTest {
        val exceptions =
            listOf(
                Exception("Database error"),
                IllegalStateException("Not found"),
                NullPointerException("Null data"),
            )

        exceptions.forEach { exception ->
            coEvery { mockRepository.getSeriesInfo(any()) } returns Result.failure(exception)

            val result = getSeriesInfoUseCase(456)

            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }
    }
}
