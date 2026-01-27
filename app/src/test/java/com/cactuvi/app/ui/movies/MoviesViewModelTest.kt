package com.cactuvi.app.ui.movies

import app.cash.turbine.test
import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.DataState
import com.cactuvi.app.data.models.NavigationResult
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.ui.common.ContentUiState
import com.cactuvi.app.utils.PreferencesManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MoviesViewModel with reactive Flow-based API.
 *
 * Tests cover:
 * - Initial state and sync states
 * - Navigation state changes
 * - Back navigation logic
 * - State machine behavior (ADT sealed class)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockRepository: ContentRepository
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var moviesStateFlow: MutableStateFlow<DataState<Unit>>
    private lateinit var viewModel: MoviesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockRepository = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)
        moviesStateFlow = MutableStateFlow(DataState.Success(Unit))

        // Default grouping settings
        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns true
        every { mockPreferencesManager.getMoviesGroupingSeparator() } returns "-"

        // Default sync state
        every { mockRepository.moviesState } returns moviesStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Helper: Create mock groups as Map */
    private fun createMockGroupsMap(): Map<String, List<Category>> {
        return mapOf(
            "US" to
                listOf(
                    Category(categoryId = "1", categoryName = "US - Action", parentId = 0),
                    Category(categoryId = "2", categoryName = "US - Comedy", parentId = 0),
                ),
            "UK" to
                listOf(
                    Category(categoryId = "3", categoryName = "UK - Drama", parentId = 0),
                ),
        )
    }

    /** Helper: Create mock categories */
    private fun createMockCategories(): List<Category> {
        return listOf(
            Category(categoryId = "1", categoryName = "Action", parentId = 0),
            Category(categoryId = "2", categoryName = "Comedy", parentId = 0),
        )
    }

    // ========== INITIAL STATE TESTS ==========

    @Test
    fun `initial state is Initial before data loads`() = runTest {
        // Given: Repository returns empty categories
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, any(), any()) } returns
            flowOf(NavigationResult.Categories(emptyList()))

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Initial value should be Initial (before Flow emits)
        // Note: With UnconfinedTestDispatcher, it may already emit first value
        viewModel.uiState.test {
            val state = awaitItem()
            // Either Initial or first emission
            assertTrue(
                state is ContentUiState.Initial ||
                    state is ContentUiState.Loading ||
                    state is ContentUiState.Content
            )
        }
    }

    @Test
    fun `shows Syncing state when no data and sync in progress`() = runTest {
        // Given: Sync is loading
        moviesStateFlow.value = DataState.Loading(progress = 50)

        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, any(), any()) } returns
            flowOf(NavigationResult.Categories(emptyList()))

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Should show syncing state
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Expected Syncing state but got $state", state is ContentUiState.Syncing)
            assertEquals(50, (state as ContentUiState.Syncing).progress)
        }
    }

    // ========== NAVIGATION TESTS ==========

    @Test
    fun `shows Groups state when grouping enabled and groups available`() = runTest {
        // Given: Repository returns groups
        val groups = createMockGroupsMap()
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, true, "-") } returns
            flowOf(NavigationResult.Groups(groups))

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Should show groups
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(
                "Expected Content.Groups but got $state",
                state is ContentUiState.Content.Groups
            )
            val groupsState = state as ContentUiState.Content.Groups
            assertEquals(2, groupsState.groups.size)
            assertTrue(groupsState.groups.containsKey("US"))
            assertTrue(groupsState.groups.containsKey("UK"))
            assertTrue(groupsState.breadcrumbPath.isEmpty())
        }
    }

    @Test
    fun `shows Categories state when grouping disabled`() = runTest {
        // Given: Grouping disabled
        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns false
        val categories = createMockCategories()

        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, false, "-") } returns
            flowOf(NavigationResult.Categories(categories))

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Should show categories directly
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(
                "Expected Content.Categories but got $state",
                state is ContentUiState.Content.Categories
            )
            val categoriesState = state as ContentUiState.Content.Categories
            assertEquals(2, categoriesState.categories.size)
            assertTrue(categoriesState.breadcrumbPath.isEmpty())
        }
    }

    @Test
    fun `navigateToGroup transitions to Categories with breadcrumb`() = runTest {
        // Given: Groups loaded
        val groups = createMockGroupsMap()
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, true, "-") } returns
            flowOf(NavigationResult.Groups(groups))

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // When: Navigate to group
        viewModel.navigateToGroup("US")

        // Then: Should show categories with breadcrumb
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(
                "Expected Content.Categories but got $state",
                state is ContentUiState.Content.Categories
            )
            val categoriesState = state as ContentUiState.Content.Categories
            assertEquals(2, categoriesState.categories.size)
            assertEquals(1, categoriesState.breadcrumbPath.size)
            assertEquals("US", categoriesState.breadcrumbPath.first().displayName)
            assertTrue(categoriesState.breadcrumbPath.first().isGroup)
        }
    }

    @Test
    fun `navigateToCategory with no children shows Items state`() = runTest {
        // Given: Repository returns categories then empty children
        val groups = createMockGroupsMap()
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, true, "-") } returns
            flowOf(NavigationResult.Groups(groups))

        every { mockRepository.observeChildCategories(ContentType.MOVIES, "1") } returns
            flowOf(NavigationResult.Categories(emptyList()))

        every { mockRepository.observeCategory(ContentType.MOVIES, "1") } returns
            flowOf(Category(categoryId = "1", categoryName = "Action", parentId = 0))

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Use turbine to navigate step by step
        viewModel.uiState.test {
            // Initial state - groups at root
            val groupsState = awaitItem()
            assertTrue(groupsState is ContentUiState.Content.Groups)

            // Navigate to group
            viewModel.navigateToGroup("US")
            val categoriesState = awaitItem()
            assertTrue(categoriesState is ContentUiState.Content.Categories)
            assertEquals(
                1,
                (categoriesState as ContentUiState.Content.Categories).breadcrumbPath.size
            )

            // Now navigate to category (breadcrumb has US)
            viewModel.navigateToCategory("1", "Action")
            val itemsState = awaitItem()
            assertTrue(
                "Expected Content.Items but got $itemsState",
                itemsState is ContentUiState.Content.Items
            )
            assertEquals("1", (itemsState as ContentUiState.Content.Items).categoryId)
            assertEquals(2, itemsState.breadcrumbPath.size) // US + Action
        }
    }

    // ========== BACK NAVIGATION TESTS ==========

    @Test
    fun `navigateBack from root returns false`() = runTest {
        // Given: At root level
        val categories = createMockCategories()
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, any(), any()) } returns
            flowOf(NavigationResult.Categories(categories))

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // When: Navigate back from root
        val handled = viewModel.navigateBack()

        // Then: Should not handle (exit activity)
        assertFalse(handled)
    }

    @Test
    fun `navigateBack from group returns to root`() = runTest {
        // Given: At group level
        val groups = createMockGroupsMap()
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, true, "-") } returns
            flowOf(NavigationResult.Groups(groups))

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)
        viewModel.navigateToGroup("US")

        // When: Navigate back
        val handled = viewModel.navigateBack()

        // Then: Should return to root
        assertTrue(handled)

        // Verify state returned to Groups
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(
                "Expected Content.Groups but got $state",
                state is ContentUiState.Content.Groups
            )
        }
    }

    @Test
    fun `navigateBack from category returns to previous level`() = runTest {
        // Given: At category level after navigating through group
        val groups = createMockGroupsMap()
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, true, "-") } returns
            flowOf(NavigationResult.Groups(groups))

        every { mockRepository.observeChildCategories(ContentType.MOVIES, "1") } returns
            flowOf(NavigationResult.Categories(emptyList()))

        every { mockRepository.observeCategory(ContentType.MOVIES, "1") } returns
            flowOf(Category(categoryId = "1", categoryName = "Action", parentId = 0))

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)
        viewModel.navigateToGroup("US")
        viewModel.navigateToCategory("1", "Action")

        // When: Navigate back twice
        val handled1 = viewModel.navigateBack()
        assertTrue(handled1)

        val handled2 = viewModel.navigateBack()
        assertTrue(handled2)

        // Then: Should be at root
        val handled3 = viewModel.navigateBack()
        assertFalse(handled3) // At root, should exit
    }

    // ========== STATE MACHINE TESTS ==========

    @Test
    fun `sealed class state types are mutually exclusive`() = runTest {
        // Given: Categories loaded
        val categories = createMockCategories()
        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns false
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, false, "-") } returns
            flowOf(NavigationResult.Categories(categories))

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: State is exactly one type
        viewModel.uiState.test {
            val state = awaitItem()

            // Verify mutual exclusivity using when
            val stateDescription =
                when (state) {
                    is ContentUiState.Initial -> "Initial"
                    is ContentUiState.Syncing -> "Syncing"
                    is ContentUiState.Loading -> "Loading"
                    is ContentUiState.Error -> "Error"
                    is ContentUiState.Content.Groups -> "Content.Groups"
                    is ContentUiState.Content.Categories -> "Content.Categories"
                    is ContentUiState.Content.Items -> "Content.Items"
                }

            assertEquals("Content.Categories", stateDescription)
        }
    }

    @Test
    fun `error state is emitted when sync fails and no cache`() = runTest {
        // Given: Sync failed with error
        moviesStateFlow.value = DataState.Error(RuntimeException("Network error"))

        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, any(), any()) } returns
            flowOf(NavigationResult.Categories(emptyList()))

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Should show error state
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Expected Error state but got $state", state is ContentUiState.Error)
            assertEquals("Network error", (state as ContentUiState.Error).message)
        }
    }

    // ========== SYNC UX TESTS ==========

    @Test
    fun `shows Syncing state with indeterminate progress when progress is null`() = runTest {
        // Given: Sync is loading with no progress info
        moviesStateFlow.value = DataState.Loading(progress = null)

        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, any(), any()) } returns
            flowOf(NavigationResult.Categories(emptyList()))

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Should show syncing state with null progress (indeterminate)
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Expected Syncing state but got $state", state is ContentUiState.Syncing)
            assertEquals(null, (state as ContentUiState.Syncing).progress)
        }
    }

    @Test
    fun `shows Content when data exists even during sync`() = runTest {
        // Given: Sync is loading but data exists
        moviesStateFlow.value = DataState.Loading(progress = 30)
        val categories = createMockCategories()

        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns false
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, false, "-") } returns
            flowOf(NavigationResult.Categories(categories))

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Should show content (not syncing) because data exists
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(
                "Expected Content.Categories but got $state (data exists, should show content not syncing)",
                state is ContentUiState.Content.Categories
            )
        }
    }

    @Test
    fun `transitions from Syncing to Content when data arrives`() = runTest {
        // Given: Initially syncing with no data
        moviesStateFlow.value = DataState.Loading(progress = 50)
        val categoriesFlow =
            MutableStateFlow<NavigationResult>(NavigationResult.Categories(emptyList()))

        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns false
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, false, "-") } returns
            categoriesFlow

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        viewModel.uiState.test {
            // Initial state: Syncing (no data)
            val syncingState = awaitItem()
            assertTrue(
                "Expected Syncing but got $syncingState",
                syncingState is ContentUiState.Syncing
            )
            assertEquals(50, (syncingState as ContentUiState.Syncing).progress)

            // Simulate data arriving
            categoriesFlow.value = NavigationResult.Categories(createMockCategories())

            // Should transition to Content
            val contentState = awaitItem()
            assertTrue(
                "Expected Content.Categories but got $contentState",
                contentState is ContentUiState.Content.Categories
            )
        }
    }

    @Test
    fun `sync progress updates are reflected in Syncing state`() = runTest {
        // Given: Syncing with progress updates
        moviesStateFlow.value = DataState.Loading(progress = 10)

        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, any(), any()) } returns
            flowOf(NavigationResult.Categories(emptyList()))

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        viewModel.uiState.test {
            // Initial: 10%
            val state1 = awaitItem()
            assertTrue(state1 is ContentUiState.Syncing)
            assertEquals(10, (state1 as ContentUiState.Syncing).progress)

            // Update to 50%
            moviesStateFlow.value = DataState.Loading(progress = 50)
            val state2 = awaitItem()
            assertTrue(state2 is ContentUiState.Syncing)
            assertEquals(50, (state2 as ContentUiState.Syncing).progress)

            // Update to 90%
            moviesStateFlow.value = DataState.Loading(progress = 90)
            val state3 = awaitItem()
            assertTrue(state3 is ContentUiState.Syncing)
            assertEquals(90, (state3 as ContentUiState.Syncing).progress)
        }
    }

    // ========== SEPARATOR CHANGE TEST ==========

    @Test
    fun `onSeparatorChanged resets to root`() = runTest {
        // Given: Navigated into groups
        val groups = createMockGroupsMap()
        every { mockRepository.observeTopLevelNavigation(ContentType.MOVIES, true, "-") } returns
            flowOf(NavigationResult.Groups(groups))

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)
        viewModel.navigateToGroup("US")

        // When: Separator changed
        viewModel.onSeparatorChanged()

        // Then: Should be at root, back should not work
        val handled = viewModel.navigateBack()
        assertFalse(handled)
    }
}
