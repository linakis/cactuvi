package com.cactuvi.app.ui.movies

import app.cash.turbine.test
import com.cactuvi.app.domain.model.ContentCategory
import com.cactuvi.app.domain.model.GroupNode
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.domain.usecase.ObserveMoviesUseCase
import com.cactuvi.app.domain.usecase.RefreshMoviesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
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
    
    /**
     * Helper: Create mock navigation tree
     */
    private fun createMockNavigationTree(): NavigationTree {
        val categories = listOf(
            ContentCategory(categoryId = "1", categoryName = "Action", parentId = 0, itemCount = 10),
            ContentCategory(categoryId = "2", categoryName = "Comedy", parentId = 0, itemCount = 5)
        )
        val groups = listOf(
            GroupNode(name = "All Movies", categories = categories)
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
    
    @Test
    fun `observes movies and emits loading then success`() = runTest {
        // Given: Mock UseCase emits Loading then Success
        val mockTree = createMockNavigationTree()
        every { observeMoviesUseCase() } returns flowOf(
            Resource.Loading(),
            Resource.Success(mockTree)
        )
        
        // When: ViewModel is created and collect states
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)
        
        // Then: Final state should be Success
        // Note: With UnconfinedTestDispatcher, emissions happen synchronously,
        // so we only check the final state after both emissions
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertNotNull(finalState.navigationTree)
        assertNull(finalState.error)
    }
    
    @Test
    fun `observes movies and emits error without cache`() = runTest {
        // Given: Mock UseCase emits Error without data
        val error = Exception("Network error")
        every { observeMoviesUseCase() } returns flowOf(
            Resource.Error(error, data = null)
        )
        
        // When: ViewModel is created
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)
        
        // Then: State should show error
        viewModel.uiState.test {
            val errorState = awaitItem()
            assertFalse(errorState.isLoading)
            assertNull(errorState.navigationTree)
            assertEquals("Network error", errorState.error)
        }
    }
    
    @Test
    fun `observes movies and emits error with cache`() = runTest {
        // Given: Mock UseCase emits Error with cached data
        val mockTree = createMockNavigationTree()
        val error = Exception("Network error")
        every { observeMoviesUseCase() } returns flowOf(
            Resource.Error(error, data = mockTree)
        )
        
        // When: ViewModel is created
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)
        
        // Then: State should show cached data without error message
        viewModel.uiState.test {
            val errorState = awaitItem()
            assertFalse(errorState.isLoading)
            assertNotNull(errorState.navigationTree) // Cache available
            assertNull(errorState.error) // Silent error
        }
    }
    
    @Test
    fun `loading state preserves cached data`() = runTest {
        // Given: Mock UseCase emits Loading with previous data
        val mockTree = createMockNavigationTree()
        every { observeMoviesUseCase() } returns flowOf(
            Resource.Loading(data = mockTree)
        )
        
        // When: ViewModel is created
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)
        
        // Then: Loading state should show spinner but keep cached data
        viewModel.uiState.test {
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            assertNotNull(loadingState.navigationTree) // Cached data preserved
        }
    }
    
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
    
    @Test
    fun `navigateBack from CONTENT returns to CATEGORIES`() {
        // Given: ViewModel at CONTENT level
        every { observeMoviesUseCase() } returns flowOf()
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)
        viewModel.selectCategory("123")
        
        // When: User presses back
        val handled = viewModel.navigateBack()
        
        // Then: Should navigate back to CATEGORIES
        assertTrue(handled)
        assertEquals(NavigationLevel.CATEGORIES, viewModel.uiState.value.currentLevel)
        assertNull(viewModel.uiState.value.selectedCategoryId)
    }
    
    // ========== REFRESH TESTS ==========
    
    @Test
    fun `refresh calls RefreshMoviesUseCase`() = runTest {
        // Given: ViewModel with mocked dependencies
        every { observeMoviesUseCase() } returns flowOf()
        coEvery { refreshMoviesUseCase() } returns Unit
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase, contentRepository)
        
        // When: User pulls to refresh
        viewModel.refresh()
        
        // Then: RefreshMoviesUseCase should be called
        coVerify(exactly = 1) { refreshMoviesUseCase() }
    }
}
