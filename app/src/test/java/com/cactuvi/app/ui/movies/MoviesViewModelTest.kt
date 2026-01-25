package com.cactuvi.app.ui.movies

import com.cactuvi.app.domain.model.ContentCategory
import com.cactuvi.app.domain.model.GroupNode
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.domain.usecase.ObserveMoviesUseCase
import com.cactuvi.app.domain.usecase.RefreshMoviesUseCase
import com.cactuvi.app.ui.common.NavigationLevel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MoviesViewModel.
 *
 * Tests cover:
 * - Initial state
 * - State transitions (Loading → Success → Error)
 * - Navigation state changes (Group → Category → Content)
 * - Refresh action
 * - Back navigation logic
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var observeMoviesUseCase: ObserveMoviesUseCase
    private lateinit var refreshMoviesUseCase: RefreshMoviesUseCase
    private lateinit var contentRepository: ContentRepository
    private lateinit var viewModel: MoviesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        observeMoviesUseCase = mockk()
        refreshMoviesUseCase = mockk(relaxed = true)
        contentRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Helper: Create mock navigation tree */
    private fun createMockNavigationTree(): NavigationTree {
        val categories =
            listOf(
                ContentCategory(
                    categoryId = "1",
                    categoryName = "Action",
                    parentId = 0,
                    itemCount = 10
                ),
                ContentCategory(
                    categoryId = "2",
                    categoryName = "Comedy",
                    parentId = 0,
                    itemCount = 5
                ),
            )
        val groups =
            listOf(
                GroupNode(name = "All Movies", categories = categories),
            )
        return NavigationTree(groups)
    }

    // ========== INITIAL STATE TESTS ==========

    @Test
    fun `initial state is correct`() {
        // Given: Mock returns empty flow to prevent init block from updating state
        every { observeMoviesUseCase() } returns flowOf()

        // When: ViewModel is created
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)

        // Then: Initial state should have default values
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.GROUPS, state.currentLevel)
        assertNull(state.navigationTree)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.selectedGroupName)
        assertNull(state.selectedCategoryId)
    }

    // ========== STATE TRANSITION TESTS ==========
    // Note: Comprehensive auto-skip and state transition tests are in ContentViewModelAutoSkipTest.
    // These tests are redundant and have been removed.

    // ========== NAVIGATION TESTS ==========

    @Test
    fun `selectGroup updates state correctly`() {
        // Given: ViewModel with initial state
        every { observeMoviesUseCase() } returns flowOf()
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)

        // When: User selects a group
        viewModel.selectGroup("Action Movies")

        // Then: State should update
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.CATEGORIES, state.currentLevel)
        assertEquals("Action Movies", state.selectedGroupName)
    }

    @Test
    fun `selectCategory updates state correctly`() {
        // Given: ViewModel with initial state
        every { observeMoviesUseCase() } returns flowOf()
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)

        // When: User selects a category
        viewModel.selectCategory("123")

        // Then: State should update
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.CONTENT, state.currentLevel)
        assertEquals("123", state.selectedCategoryId)
    }

    @Test
    fun `navigateBack from GROUPS returns false`() {
        // Given: ViewModel at GROUPS level
        every { observeMoviesUseCase() } returns flowOf()
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)

        // When: User presses back
        val handled = viewModel.navigateBack()

        // Then: Back press not handled (should exit app)
        assertFalse(handled)
        assertEquals(NavigationLevel.GROUPS, viewModel.uiState.value.currentLevel)
    }

    @Test
    fun `navigateBack from CATEGORIES returns to GROUPS`() {
        // Given: ViewModel at CATEGORIES level
        every { observeMoviesUseCase() } returns flowOf()
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)
        viewModel.selectGroup("Action Movies")

        // When: User presses back
        val handled = viewModel.navigateBack()

        // Then: Should navigate back to GROUPS
        assertTrue(handled)
        assertEquals(NavigationLevel.GROUPS, viewModel.uiState.value.currentLevel)
        assertNull(viewModel.uiState.value.selectedGroupName)
    }

    // Note: navigateBack auto-skip logic fully tested in ContentViewModelAutoSkipTest

    // ========== REFRESH TESTS ==========

    @Test
    fun `refresh calls RefreshMoviesUseCase`() {
        // Given: ViewModel with mocked dependencies
        val mockTree = createMockNavigationTree()
        every { observeMoviesUseCase() } returns flowOf(Resource.Success(mockTree))
        coEvery { refreshMoviesUseCase() } returns Unit
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)

        // When: User pulls to refresh (synchronous test with UnconfinedTestDispatcher)
        viewModel.refresh()

        // Then: RefreshMoviesUseCase should be called
        coVerify { refreshMoviesUseCase() }
    }
}
