package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.repository.ContentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for RefreshXXXUseCase classes.
 *
 * Tests cover:
 * - Delegation to repository refresh methods
 * - Suspend function execution
 * - Multiple refresh calls
 */
class RefreshUseCasesTest {

    private lateinit var mockRepository: ContentRepository
    private lateinit var refreshMoviesUseCase: RefreshMoviesUseCase
    private lateinit var refreshSeriesUseCase: RefreshSeriesUseCase
    private lateinit var refreshLiveUseCase: RefreshLiveUseCase

    @Before
    fun setup() {
        mockRepository = mockk(relaxed = true)
        refreshMoviesUseCase = RefreshMoviesUseCase(mockRepository)
        refreshSeriesUseCase = RefreshSeriesUseCase(mockRepository)
        refreshLiveUseCase = RefreshLiveUseCase(mockRepository)
    }

    // ========== REFRESH MOVIES USE CASE ==========

    @Test
    fun `RefreshMoviesUseCase calls repository refreshMovies`() = runTest {
        coEvery { mockRepository.refreshMovies() } returns Unit

        refreshMoviesUseCase()

        coVerify(exactly = 1) { mockRepository.refreshMovies() }
    }

    @Test
    fun `RefreshMoviesUseCase can be called multiple times`() = runTest {
        coEvery { mockRepository.refreshMovies() } returns Unit

        refreshMoviesUseCase()
        refreshMoviesUseCase()
        refreshMoviesUseCase()

        coVerify(exactly = 3) { mockRepository.refreshMovies() }
    }

    @Test
    fun `RefreshMoviesUseCase propagates repository exceptions`() = runTest {
        val exception = Exception("Network error")
        coEvery { mockRepository.refreshMovies() } throws exception

        try {
            refreshMoviesUseCase()
            throw AssertionError("Should have thrown exception")
        } catch (e: Exception) {
            assert(e.message == "Network error")
        }
    }

    // ========== REFRESH SERIES USE CASE ==========

    @Test
    fun `RefreshSeriesUseCase calls repository refreshSeries`() = runTest {
        coEvery { mockRepository.refreshSeries() } returns Unit

        refreshSeriesUseCase()

        coVerify(exactly = 1) { mockRepository.refreshSeries() }
    }

    @Test
    fun `RefreshSeriesUseCase can be called multiple times`() = runTest {
        coEvery { mockRepository.refreshSeries() } returns Unit

        refreshSeriesUseCase()
        refreshSeriesUseCase()

        coVerify(exactly = 2) { mockRepository.refreshSeries() }
    }

    @Test
    fun `RefreshSeriesUseCase propagates repository exceptions`() = runTest {
        val exception = Exception("API timeout")
        coEvery { mockRepository.refreshSeries() } throws exception

        try {
            refreshSeriesUseCase()
            throw AssertionError("Should have thrown exception")
        } catch (e: Exception) {
            assert(e.message == "API timeout")
        }
    }

    // ========== REFRESH LIVE USE CASE ==========

    @Test
    fun `RefreshLiveUseCase calls repository refreshLive`() = runTest {
        coEvery { mockRepository.refreshLive() } returns Unit

        refreshLiveUseCase()

        coVerify(exactly = 1) { mockRepository.refreshLive() }
    }

    @Test
    fun `RefreshLiveUseCase can be called multiple times`() = runTest {
        coEvery { mockRepository.refreshLive() } returns Unit

        refreshLiveUseCase()
        refreshLiveUseCase()
        refreshLiveUseCase()
        refreshLiveUseCase()

        coVerify(exactly = 4) { mockRepository.refreshLive() }
    }

    @Test
    fun `RefreshLiveUseCase propagates repository exceptions`() = runTest {
        val exception = Exception("Database error")
        coEvery { mockRepository.refreshLive() } throws exception

        try {
            refreshLiveUseCase()
            throw AssertionError("Should have thrown exception")
        } catch (e: Exception) {
            assert(e.message == "Database error")
        }
    }

    // ========== CONCURRENT REFRESH TESTS ==========

    @Test
    fun `Multiple refresh UseCases can be called in sequence`() = runTest {
        coEvery { mockRepository.refreshMovies() } returns Unit
        coEvery { mockRepository.refreshSeries() } returns Unit
        coEvery { mockRepository.refreshLive() } returns Unit

        refreshMoviesUseCase()
        refreshSeriesUseCase()
        refreshLiveUseCase()

        coVerify(exactly = 1) { mockRepository.refreshMovies() }
        coVerify(exactly = 1) { mockRepository.refreshSeries() }
        coVerify(exactly = 1) { mockRepository.refreshLive() }
    }
}
