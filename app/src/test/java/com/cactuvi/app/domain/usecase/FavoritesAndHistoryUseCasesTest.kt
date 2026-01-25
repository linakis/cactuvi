package com.cactuvi.app.domain.usecase

import com.cactuvi.app.data.db.entities.FavoriteEntity
import com.cactuvi.app.data.db.entities.WatchHistoryEntity
import com.cactuvi.app.domain.repository.ContentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Favorites and Watch History UseCases.
 *
 * Tests cover:
 * - Add/Remove favorites
 * - Observe favorites
 * - Observe isFavorite status
 * - Observe watch history
 * - Delete watch history items
 */
class FavoritesAndHistoryUseCasesTest {

    private lateinit var mockRepository: ContentRepository
    private lateinit var addToFavoritesUseCase: AddToFavoritesUseCase
    private lateinit var removeFromFavoritesUseCase: RemoveFromFavoritesUseCase
    private lateinit var observeFavoritesUseCase: ObserveFavoritesUseCase
    private lateinit var observeIsFavoriteUseCase: ObserveIsFavoriteUseCase
    private lateinit var observeWatchHistoryUseCase: ObserveWatchHistoryUseCase
    private lateinit var deleteWatchHistoryItemUseCase: DeleteWatchHistoryItemUseCase

    @Before
    fun setup() {
        mockRepository = mockk(relaxed = true)
        addToFavoritesUseCase = AddToFavoritesUseCase(mockRepository)
        removeFromFavoritesUseCase = RemoveFromFavoritesUseCase(mockRepository)
        observeFavoritesUseCase = ObserveFavoritesUseCase(mockRepository)
        observeIsFavoriteUseCase = ObserveIsFavoriteUseCase(mockRepository)
        observeWatchHistoryUseCase = ObserveWatchHistoryUseCase(mockRepository)
        deleteWatchHistoryItemUseCase = DeleteWatchHistoryItemUseCase(mockRepository)
    }

    // ========== ADD TO FAVORITES USE CASE ==========

    @Test
    fun `AddToFavoritesUseCase adds favorite successfully`() = runTest {
        coEvery {
            mockRepository.addToFavorites("123", "movie", "Test Movie", "icon.jpg", "8.5", "Action")
        } returns Result.success(Unit)

        val result =
            addToFavoritesUseCase(
                contentId = "123",
                contentType = "movie",
                contentName = "Test Movie",
                posterUrl = "icon.jpg",
                rating = "8.5",
                categoryName = "Action",
            )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            mockRepository.addToFavorites("123", "movie", "Test Movie", "icon.jpg", "8.5", "Action")
        }
    }

    @Test
    fun `AddToFavoritesUseCase passes all parameters correctly`() = runTest {
        coEvery { mockRepository.addToFavorites(any(), any(), any(), any(), any(), any()) } returns
            Result.success(Unit)

        addToFavoritesUseCase(
            contentId = "456",
            contentType = "series",
            contentName = "Test Series",
            posterUrl = null,
            rating = null,
            categoryName = "Drama",
        )

        coVerify {
            mockRepository.addToFavorites("456", "series", "Test Series", null, null, "Drama")
        }
    }

    @Test
    fun `AddToFavoritesUseCase returns failure on error`() = runTest {
        val exception = Exception("Database error")
        coEvery { mockRepository.addToFavorites(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(exception)

        val result = addToFavoritesUseCase("123", "movie", "Test", null)

        assertTrue(result.isFailure)
        assertEquals("Database error", result.exceptionOrNull()?.message)
    }

    // ========== REMOVE FROM FAVORITES USE CASE ==========

    @Test
    fun `RemoveFromFavoritesUseCase removes favorite successfully`() = runTest {
        coEvery { mockRepository.removeFromFavorites("123", "movie") } returns Result.success(Unit)

        val result = removeFromFavoritesUseCase("123", "movie")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockRepository.removeFromFavorites("123", "movie") }
    }

    @Test
    fun `RemoveFromFavoritesUseCase passes parameters correctly`() = runTest {
        coEvery { mockRepository.removeFromFavorites("456", "series") } returns Result.success(Unit)

        removeFromFavoritesUseCase("456", "series")

        coVerify { mockRepository.removeFromFavorites("456", "series") }
    }

    @Test
    fun `RemoveFromFavoritesUseCase returns failure on error`() = runTest {
        val exception = Exception("Not found")
        coEvery { mockRepository.removeFromFavorites(any(), any()) } returns
            Result.failure(exception)

        val result = removeFromFavoritesUseCase("123", "movie")

        assertTrue(result.isFailure)
    }

    // ========== OBSERVE FAVORITES USE CASE ==========

    @Test
    fun `ObserveFavoritesUseCase returns all favorites`() = runTest {
        val favorites =
            listOf(
                FavoriteEntity(
                    sourceId = "source1",
                    id = "movie_1",
                    contentId = "1",
                    contentType = "movie",
                    contentName = "Movie 1",
                    posterUrl = null,
                    rating = null,
                    categoryName = "Action",
                    addedAt = System.currentTimeMillis()
                ),
                FavoriteEntity(
                    sourceId = "source1",
                    id = "series_2",
                    contentId = "2",
                    contentType = "series",
                    contentName = "Series 1",
                    posterUrl = null,
                    rating = null,
                    categoryName = "Drama",
                    addedAt = System.currentTimeMillis()
                ),
            )
        coEvery { mockRepository.getFavorites(null) } returns Result.success(favorites)

        val result = observeFavoritesUseCase()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `ObserveFavoritesUseCase filters by content type`() = runTest {
        val favorites =
            listOf(
                FavoriteEntity(
                    sourceId = "source1",
                    id = "movie_1",
                    contentId = "1",
                    contentType = "movie",
                    contentName = "Movie 1",
                    posterUrl = null,
                    rating = null,
                    categoryName = "Action",
                    addedAt = System.currentTimeMillis()
                ),
            )
        coEvery { mockRepository.getFavorites("movie") } returns Result.success(favorites)

        val result = observeFavoritesUseCase("movie")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("movie", result.getOrNull()?.get(0)?.contentType)
    }

    @Test
    fun `ObserveFavoritesUseCase returns empty list when no favorites`() = runTest {
        coEvery { mockRepository.getFavorites(null) } returns Result.success(emptyList())

        val result = observeFavoritesUseCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }

    // ========== OBSERVE IS FAVORITE USE CASE ==========

    @Test
    fun `ObserveIsFavoriteUseCase returns true when favorite exists`() = runTest {
        coEvery { mockRepository.isFavorite("123", "movie") } returns Result.success(true)

        val result = observeIsFavoriteUseCase("123", "movie")

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `ObserveIsFavoriteUseCase returns false when favorite does not exist`() = runTest {
        coEvery { mockRepository.isFavorite("456", "series") } returns Result.success(false)

        val result = observeIsFavoriteUseCase("456", "series")

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
    }

    @Test
    fun `ObserveIsFavoriteUseCase passes parameters correctly`() = runTest {
        coEvery { mockRepository.isFavorite("789", "live") } returns Result.success(false)

        observeIsFavoriteUseCase("789", "live")

        coVerify { mockRepository.isFavorite("789", "live") }
    }

    // ========== OBSERVE WATCH HISTORY USE CASE ==========

    @Test
    fun `ObserveWatchHistoryUseCase returns watch history with default limit`() = runTest {
        val history =
            listOf(
                WatchHistoryEntity(
                    id = 1L,
                    sourceId = "source1",
                    contentId = "1",
                    contentType = "movie",
                    contentName = "Movie 1",
                    posterUrl = null,
                    resumePosition = 1000L,
                    duration = 5000L,
                    lastWatched = System.currentTimeMillis(),
                    isCompleted = false
                ),
                WatchHistoryEntity(
                    id = 2L,
                    sourceId = "source1",
                    contentId = "2",
                    contentType = "series",
                    contentName = "Series 1",
                    posterUrl = null,
                    resumePosition = 5000L,
                    duration = 10000L,
                    lastWatched = System.currentTimeMillis(),
                    isCompleted = false
                ),
            )
        coEvery { mockRepository.getWatchHistory(20) } returns Result.success(history)

        val result = observeWatchHistoryUseCase()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `ObserveWatchHistoryUseCase respects custom limit`() = runTest {
        val history =
            List(10) { i ->
                WatchHistoryEntity(
                    id = i.toLong(),
                    sourceId = "source1",
                    contentId = "$i",
                    contentType = "movie",
                    contentName = "Movie $i",
                    posterUrl = null,
                    resumePosition = 1000L,
                    duration = 5000L,
                    lastWatched = System.currentTimeMillis(),
                    isCompleted = false
                )
            }
        coEvery { mockRepository.getWatchHistory(10) } returns Result.success(history)

        val result = observeWatchHistoryUseCase(10)

        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.size)
        coVerify { mockRepository.getWatchHistory(10) }
    }

    @Test
    fun `ObserveWatchHistoryUseCase returns empty list when no history`() = runTest {
        coEvery { mockRepository.getWatchHistory(any()) } returns Result.success(emptyList())

        val result = observeWatchHistoryUseCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }

    // ========== DELETE WATCH HISTORY ITEM USE CASE ==========

    @Test
    fun `DeleteWatchHistoryItemUseCase deletes item successfully`() = runTest {
        coEvery { mockRepository.deleteWatchHistoryItem("123") } returns Result.success(Unit)

        val result = deleteWatchHistoryItemUseCase("123")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockRepository.deleteWatchHistoryItem("123") }
    }

    @Test
    fun `DeleteWatchHistoryItemUseCase passes parameters correctly`() = runTest {
        coEvery { mockRepository.deleteWatchHistoryItem("456") } returns Result.success(Unit)

        deleteWatchHistoryItemUseCase("456")

        coVerify { mockRepository.deleteWatchHistoryItem("456") }
    }

    @Test
    fun `DeleteWatchHistoryItemUseCase returns failure on error`() = runTest {
        val exception = Exception("Item not found")
        coEvery { mockRepository.deleteWatchHistoryItem(any()) } returns Result.failure(exception)

        val result = deleteWatchHistoryItemUseCase("123")

        assertTrue(result.isFailure)
        assertEquals("Item not found", result.exceptionOrNull()?.message)
    }

    // ========== INTEGRATION TESTS ==========

    @Test
    fun `Add and check favorite workflow`() = runTest {
        // Add favorite
        coEvery { mockRepository.addToFavorites("123", "movie", "Test", null, null, "") } returns
            Result.success(Unit)

        val addResult = addToFavoritesUseCase("123", "movie", "Test", null)
        assertTrue(addResult.isSuccess)

        // Check if favorite
        coEvery { mockRepository.isFavorite("123", "movie") } returns Result.success(true)

        val isFavResult = observeIsFavoriteUseCase("123", "movie")
        assertEquals(true, isFavResult.getOrNull())
    }

    @Test
    fun `Remove favorite and verify`() = runTest {
        // Remove favorite
        coEvery { mockRepository.removeFromFavorites("123", "movie") } returns Result.success(Unit)

        val removeResult = removeFromFavoritesUseCase("123", "movie")
        assertTrue(removeResult.isSuccess)

        // Verify not favorite
        coEvery { mockRepository.isFavorite("123", "movie") } returns Result.success(false)

        val isFavResult = observeIsFavoriteUseCase("123", "movie")
        assertEquals(false, isFavResult.getOrNull())
    }
}
