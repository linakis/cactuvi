package com.cactuvi.app.ui.movies

import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.NavigationResult
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.utils.PreferencesManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MoviesViewModel.
 *
 * Tests cover:
 * - Initial state
 * - Navigation state changes
 * - Refresh action
 * - Back navigation logic
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockRepository: ContentRepository
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var viewModel: MoviesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockRepository = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)

        // Default grouping settings
        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns true
        every { mockPreferencesManager.getMoviesGroupingSeparator() } returns "-"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Helper: Create mock groups as Map (new API) */
    private fun createMockGroupsMap(): Map<String, List<Category>> {
        return mapOf(
            "US" to
                listOf(
                    Category(categoryId = "1", categoryName = "Action", parentId = 0),
                    Category(categoryId = "2", categoryName = "Comedy", parentId = 0),
                ),
            "UK" to
                listOf(
                    Category(categoryId = "3", categoryName = "Drama", parentId = 0),
                ),
        )
    }

    // ========== INITIAL STATE TESTS ==========

    @Test
    fun `initial state has correct defaults`() {
        // Given: Repository returns empty navigation
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Categories(emptyList())

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Initial state should have correct defaults
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isLeafLevel)
        assertTrue(state.breadcrumbPath.isEmpty())
    }

    // ========== NAVIGATION TESTS ==========

    @Test
    fun `loadRoot loads groups when grouping enabled`() {
        // Given: Repository returns groups as Map
        val groups = createMockGroupsMap()
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Groups(groups)

        // When: ViewModel is created (loadRoot called in init)
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Groups should be loaded
        val state = viewModel.uiState.value
        assertNotNull(state.currentGroups)
        assertEquals(2, state.currentGroups?.size)
        assertTrue(state.currentGroups?.containsKey("US") == true)
        assertTrue(state.currentGroups?.containsKey("UK") == true)
    }

    @Test
    fun `loadRoot loads categories when grouping disabled`() {
        // Given: Grouping disabled
        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns false

        val categories =
            listOf(
                Category(categoryId = "1", categoryName = "Action", parentId = 0),
                Category(categoryId = "2", categoryName = "Comedy", parentId = 0),
            )
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Categories(categories)

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: Categories should be loaded directly
        val state = viewModel.uiState.value
        assertEquals(2, state.currentCategories.size)
    }

    @Test
    fun `navigateToGroup navigates to group categories`() {
        // Given: Groups loaded
        val groups = createMockGroupsMap()
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Groups(groups)
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // When: Navigate to first group
        viewModel.navigateToGroup("US")

        // Then: Should show categories of that group
        val state = viewModel.uiState.value
        assertEquals(2, state.currentCategories.size)
        assertEquals(1, state.breadcrumbPath.size)
        assertEquals("US", state.breadcrumbPath.first().displayName)
    }

    @Test
    fun `navigateToCategory navigates to child categories or content`() {
        // Given: Groups loaded
        val groups = createMockGroupsMap()
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Groups(groups)
        coEvery { mockRepository.getChildCategories(any(), any()) } returns
            NavigationResult.Categories(emptyList())
        coEvery { mockRepository.getCategoryById(any(), any()) } returns
            Category(categoryId = "1", categoryName = "Action", parentId = 0)

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)
        viewModel.navigateToGroup("US")

        // When: Navigate to a category
        viewModel.navigateToCategory("1", "US")

        // Then: Should navigate to content (no children = leaf)
        val state = viewModel.uiState.value
        assertTrue(state.isLeafLevel)
    }

    // ========== BACK NAVIGATION TESTS ==========

    @Test
    fun `navigateBack from root returns false`() {
        // Given: At root level
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Categories(emptyList())
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // When: Navigate back
        val handled = viewModel.navigateBack()

        // Then: Should not handle (exit activity)
        assertFalse(handled)
    }

    @Test
    fun `navigateBack from group level returns to root`() {
        // Given: At group level
        val groups = createMockGroupsMap()
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Groups(groups)
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)
        viewModel.navigateToGroup("US")

        // When: Navigate back
        val handled = viewModel.navigateBack()

        // Then: Should return to root
        assertTrue(handled)
    }

    // ========== REFRESH TESTS ==========

    @Test
    fun `refresh reloads current level`() {
        // Given: ViewModel initialized
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Categories(emptyList())
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // When: Refresh is called
        viewModel.refresh()

        // Then: Should reload root (called twice - init + refresh)
        coVerify(atLeast = 2) { mockRepository.getTopLevelNavigation(any(), any(), any()) }
    }

    // ========== UI STATE HELPER TESTS ==========

    @Test
    fun `isViewingGroups returns true when groups are loaded`() {
        // Given: Groups loaded
        val groups = createMockGroupsMap()
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Groups(groups)

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: isViewingGroups should be true
        assertTrue(viewModel.uiState.value.isViewingGroups)
        assertFalse(viewModel.uiState.value.isViewingCategories)
        assertFalse(viewModel.uiState.value.isViewingContent)
    }

    @Test
    fun `isViewingCategories returns true when categories are loaded`() {
        // Given: Categories loaded (no grouping)
        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns false
        val categories =
            listOf(
                Category(categoryId = "1", categoryName = "Action", parentId = 0),
            )
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Categories(categories)

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: isViewingCategories should be true
        assertFalse(viewModel.uiState.value.isViewingGroups)
        assertTrue(viewModel.uiState.value.isViewingCategories)
        assertFalse(viewModel.uiState.value.isViewingContent)
    }

    @Test
    fun `showContent returns true when not loading and no error`() {
        // Given: Repository returns categories
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Categories(emptyList())

        // When: ViewModel is created
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        // Then: showContent should be true
        assertTrue(viewModel.uiState.value.showContent)
        assertFalse(viewModel.uiState.value.showLoading)
        assertFalse(viewModel.uiState.value.showError)
    }
}
