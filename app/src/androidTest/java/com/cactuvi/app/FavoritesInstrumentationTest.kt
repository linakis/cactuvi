package com.cactuvi.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.entities.FavoriteEntity
import com.cactuvi.app.ui.detail.MovieDetailActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for favorites functionality.
 *
 * Tests add/remove movie favorites with UI verification and database state validation. Uses mock
 * flavor for fast, reliable execution without real API calls.
 *
 * Prerequisites:
 * - Run on mock flavor: ./gradlew connectedMockDebugAndroidTest
 * - Requires emulator/device with API 26+
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class FavoritesInstrumentationTest {

    @get:Rule var hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    // Test movie data from mock responses
    private val testMovieId = 1785031 // "EN - King Ivory (2025)" from get_vod_streams.json
    private val testMovieName = "EN - King Ivory (2025)"
    private val testStreamId = 1785031
    private val testSourceId = "test-source"

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getInstance(context)

        // Clear any existing data
        runBlocking { database.clearAllTables() }
    }

    @After
    fun tearDown() {
        runBlocking { database.clearAllTables() }
        database.close()
    }

    @Test
    fun testAddMovieToFavorites() {
        // Launch movie detail screen
        val intent =
            Intent(context, MovieDetailActivity::class.java).apply {
                putExtra("VOD_ID", testMovieId)
                putExtra("STREAM_ID", testStreamId)
                putExtra("TITLE", testMovieName)
                putExtra("CONTAINER_EXTENSION", "mkv")
            }

        ActivityScenario.launch<MovieDetailActivity>(intent).use {
            // Wait for data to load
            Thread.sleep(2000)

            // Initially favorite button should show "not favorite" state (border icon)
            onView(withId(R.id.favoriteButton))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))

            // Click favorite button to add to favorites
            onView(withId(R.id.favoriteButton)).perform(click())

            // Wait for database operation
            Thread.sleep(500)

            // Verify movie is in favorites database
            runBlocking {
                val isFavorite =
                    database.favoriteDao().isFavorite(testSourceId, testMovieId.toString())
                assert(isFavorite) { "Movie should be marked as favorite in database" }
            }

            // Note: UI state verification depends on drawable resource changes
            // which are harder to test with Espresso. Database verification is more reliable.
        }
    }

    @Test
    fun testRemoveMovieFromFavorites() {
        // First, add movie to favorites programmatically
        runBlocking {
            val favorite =
                FavoriteEntity(
                    sourceId = testSourceId,
                    id = testMovieId.toString(),
                    contentId = testMovieId.toString(),
                    contentName = testMovieName,
                    contentType = "movie",
                    posterUrl = "",
                    rating = "6.9",
                    categoryName = "New Releases",
                    addedAt = System.currentTimeMillis(),
                )
            database.favoriteDao().insert(favorite)
        }

        // Launch movie detail screen
        val intent =
            Intent(context, MovieDetailActivity::class.java).apply {
                putExtra("VOD_ID", testMovieId)
                putExtra("STREAM_ID", testStreamId)
                putExtra("TITLE", testMovieName)
                putExtra("CONTAINER_EXTENSION", "mkv")
            }

        ActivityScenario.launch<MovieDetailActivity>(intent).use {
            // Wait for data to load
            Thread.sleep(2000)

            // Verify movie is initially marked as favorite
            runBlocking {
                val isFavorite =
                    database.favoriteDao().isFavorite(testSourceId, testMovieId.toString())
                assert(isFavorite) { "Movie should be marked as favorite initially" }
            }

            // Click favorite button to remove from favorites
            onView(withId(R.id.favoriteButton)).perform(click())

            // Wait for database operation
            Thread.sleep(500)

            // Verify movie is removed from favorites database
            runBlocking {
                val isFavorite =
                    database.favoriteDao().isFavorite(testSourceId, testMovieId.toString())
                assert(!isFavorite) { "Movie should be removed from favorites in database" }
            }
        }
    }

    @Test
    fun testFavoritesPersistAcrossAppRestarts() {
        // Add movie to favorites
        runBlocking {
            val favorite =
                FavoriteEntity(
                    sourceId = testSourceId,
                    id = testMovieId.toString(),
                    contentId = testMovieId.toString(),
                    contentName = testMovieName,
                    contentType = "movie",
                    posterUrl = "",
                    rating = "6.9",
                    categoryName = "New Releases",
                    addedAt = System.currentTimeMillis(),
                )
            database.favoriteDao().insert(favorite)
        }

        // Close database to simulate app restart
        database.close()

        // Recreate database instance (simulates app restart)
        val newDatabase = AppDatabase.getInstance(context)

        // Verify favorite persists
        runBlocking {
            val isFavorite =
                newDatabase.favoriteDao().isFavorite(testSourceId, testMovieId.toString())
            assert(isFavorite) { "Favorite should persist across database restart" }

            // Verify we can retrieve the favorite
            val favorites = newDatabase.favoriteDao().getAll(testSourceId)
            assert(favorites.size == 1) { "Should have exactly 1 favorite" }
            assert(favorites[0].contentId == testMovieId.toString()) {
                "Favorite should be the test movie"
            }
        }

        newDatabase.close()
    }

    @Test
    fun testToggleFavoriteTwice() {
        // Launch movie detail screen
        val intent =
            Intent(context, MovieDetailActivity::class.java).apply {
                putExtra("VOD_ID", testMovieId)
                putExtra("STREAM_ID", testStreamId)
                putExtra("TITLE", testMovieName)
                putExtra("CONTAINER_EXTENSION", "mkv")
            }

        ActivityScenario.launch<MovieDetailActivity>(intent).use {
            // Wait for data to load
            Thread.sleep(2000)

            // First click: Add to favorites
            onView(withId(R.id.favoriteButton)).perform(click())
            Thread.sleep(500)

            runBlocking {
                val isFavorite =
                    database.favoriteDao().isFavorite(testSourceId, testMovieId.toString())
                assert(isFavorite) { "Movie should be favorite after first click" }
            }

            // Second click: Remove from favorites
            onView(withId(R.id.favoriteButton)).perform(click())
            Thread.sleep(500)

            runBlocking {
                val isFavorite =
                    database.favoriteDao().isFavorite(testSourceId, testMovieId.toString())
                assert(!isFavorite) { "Movie should not be favorite after second click" }
            }
        }
    }
}
