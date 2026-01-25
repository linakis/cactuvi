package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.model.ContentCategory
import com.cactuvi.app.domain.model.GroupNode
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ObserveXXXUseCase classes.
 *
 * Tests cover:
 * - Flow delegation to repository
 * - Loading states propagation
 * - Success with navigation tree
 * - Error handling with/without cache
 * - Multiple emissions
 */
class ObserveContentUseCasesTest {

    private lateinit var mockRepository: ContentRepository
    private lateinit var observeMoviesUseCase: ObserveMoviesUseCase
    private lateinit var observeSeriesUseCase: ObserveSeriesUseCase
    private lateinit var observeLiveUseCase: ObserveLiveUseCase

    @Before
    fun setup() {
        mockRepository = mockk()
        observeMoviesUseCase = ObserveMoviesUseCase(mockRepository)
        observeSeriesUseCase = ObserveSeriesUseCase(mockRepository)
        observeLiveUseCase = ObserveLiveUseCase(mockRepository)
    }

    /** Helper: Create mock navigation tree */
    private fun createMockNavigationTree(groupName: String = "Test Group"): NavigationTree {
        val categories =
            listOf(
                ContentCategory(
                    categoryId = "1",
                    categoryName = "Category 1",
                    parentId = 0,
                    itemCount = 10
                ),
                ContentCategory(
                    categoryId = "2",
                    categoryName = "Category 2",
                    parentId = 0,
                    itemCount = 5
                ),
            )
        val groups = listOf(GroupNode(name = groupName, categories = categories))
        return NavigationTree(groups)
    }

    // ========== OBSERVE MOVIES USE CASE ==========

    @Test
    fun `ObserveMoviesUseCase delegates to repository`() = runTest {
        val mockTree = createMockNavigationTree("Movies")
        val resource = Resource.Success(mockTree)

        every { mockRepository.observeMovies() } returns flowOf(resource)

        val result = observeMoviesUseCase().first()

        assertTrue(result is Resource.Success)
        assertEquals("Movies", (result as Resource.Success).data.groups[0].name)
    }

    @Test
    fun `ObserveMoviesUseCase emits Loading state`() = runTest {
        val loadingResource = Resource.Loading<NavigationTree>()

        every { mockRepository.observeMovies() } returns flowOf(loadingResource)

        val result = observeMoviesUseCase().first()

        assertTrue(result is Resource.Loading)
        assertNull((result as Resource.Loading).data)
    }

    @Test
    fun `ObserveMoviesUseCase emits Success with data`() = runTest {
        val mockTree = createMockNavigationTree()
        val successResource = Resource.Success(mockTree)

        every { mockRepository.observeMovies() } returns flowOf(successResource)

        val result = observeMoviesUseCase().first()

        assertTrue(result is Resource.Success)
        assertNotNull((result as Resource.Success).data)
        assertEquals(1, result.data.groups.size)
        assertEquals(
            2,
            result.data.groups[0].categories.size,
        )
    }

    @Test
    fun `ObserveMoviesUseCase emits Error without cache`() = runTest {
        val error = Exception("Network error")
        val errorResource = Resource.Error<NavigationTree>(error = error)

        every { mockRepository.observeMovies() } returns flowOf(errorResource)

        val result = observeMoviesUseCase().first()

        assertTrue(result is Resource.Error)
        assertEquals("Network error", (result as Resource.Error).error.message)
        assertNull(result.data)
    }

    @Test
    fun `ObserveMoviesUseCase emits Error with cached data`() = runTest {
        val mockTree = createMockNavigationTree()
        val error = Exception("API timeout")
        val errorResource = Resource.Error(error = error, data = mockTree)

        every { mockRepository.observeMovies() } returns flowOf(errorResource)

        val result = observeMoviesUseCase().first()

        assertTrue(result is Resource.Error)
        assertNotNull((result as Resource.Error).data)
        assertEquals(1, result.data?.groups?.size)
    }

    @Test
    fun `ObserveMoviesUseCase propagates multiple emissions`() = runTest {
        val mockTree = createMockNavigationTree()
        val emissions =
            listOf(
                Resource.Loading<NavigationTree>(),
                Resource.Success(mockTree),
            )

        every { mockRepository.observeMovies() } returns flowOf(*emissions.toTypedArray())

        val results = mutableListOf<Resource<NavigationTree>>()
        observeMoviesUseCase().collect { results.add(it) }

        assertEquals(2, results.size)
        assertTrue(results[0] is Resource.Loading)
        assertTrue(results[1] is Resource.Success)
    }

    // ========== OBSERVE SERIES USE CASE ==========

    @Test
    fun `ObserveSeriesUseCase delegates to repository`() = runTest {
        val mockTree = createMockNavigationTree("Series")
        val resource = Resource.Success(mockTree)

        every { mockRepository.observeSeries() } returns flowOf(resource)

        val result = observeSeriesUseCase().first()

        assertTrue(result is Resource.Success)
        assertEquals("Series", (result as Resource.Success).data.groups[0].name)
    }

    @Test
    fun `ObserveSeriesUseCase emits Loading state`() = runTest {
        val loadingResource = Resource.Loading<NavigationTree>()

        every { mockRepository.observeSeries() } returns flowOf(loadingResource)

        val result = observeSeriesUseCase().first()

        assertTrue(result is Resource.Loading)
    }

    @Test
    fun `ObserveSeriesUseCase propagates repository errors`() = runTest {
        val error = Exception("Database error")
        val errorResource = Resource.Error<NavigationTree>(error = error)

        every { mockRepository.observeSeries() } returns flowOf(errorResource)

        val result = observeSeriesUseCase().first()

        assertTrue(result is Resource.Error)
        assertEquals("Database error", (result as Resource.Error).error.message)
    }

    // ========== OBSERVE LIVE USE CASE ==========

    @Test
    fun `ObserveLiveUseCase delegates to repository`() = runTest {
        val mockTree = createMockNavigationTree("Live Channels")
        val resource = Resource.Success(mockTree)

        every { mockRepository.observeLive() } returns flowOf(resource)

        val result = observeLiveUseCase().first()

        assertTrue(result is Resource.Success)
        assertEquals("Live Channels", (result as Resource.Success).data.groups[0].name)
    }

    @Test
    fun `ObserveLiveUseCase emits Loading state`() = runTest {
        val loadingResource = Resource.Loading<NavigationTree>()

        every { mockRepository.observeLive() } returns flowOf(loadingResource)

        val result = observeLiveUseCase().first()

        assertTrue(result is Resource.Loading)
    }

    @Test
    fun `ObserveLiveUseCase handles multiple groups`() = runTest {
        val cat1 = ContentCategory(categoryId = "1", categoryName = "News", parentId = 0)
        val cat2 = ContentCategory(categoryId = "2", categoryName = "Sports", parentId = 0)
        val group1 = GroupNode(name = "News", categories = listOf(cat1))
        val group2 = GroupNode(name = "Sports", categories = listOf(cat2))
        val tree = NavigationTree(groups = listOf(group1, group2))
        val successResource = Resource.Success(tree)

        every { mockRepository.observeLive() } returns flowOf(successResource)

        val result = observeLiveUseCase().first()

        assertTrue(result is Resource.Success)
        assertEquals(2, (result as Resource.Success).data.groups.size)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `Empty navigation tree is valid`() = runTest {
        val emptyTree = NavigationTree(groups = emptyList())
        val successResource = Resource.Success(emptyTree)

        every { mockRepository.observeMovies() } returns flowOf(successResource)

        val result = observeMoviesUseCase().first()

        assertTrue(result is Resource.Success)
        assertEquals(0, (result as Resource.Success).data.groups.size)
    }

    @Test
    fun `Group with no categories is valid`() = runTest {
        val emptyGroup = GroupNode(name = "Empty", categories = emptyList())
        val tree = NavigationTree(groups = listOf(emptyGroup))
        val successResource = Resource.Success(tree)

        every { mockRepository.observeSeries() } returns flowOf(successResource)

        val result = observeSeriesUseCase().first()

        assertTrue(result is Resource.Success)
        assertEquals(1, (result as Resource.Success).data.groups.size)
        assertEquals(
            0,
            result.data.groups[0].categories.size,
        )
    }

    @Test
    fun `Category with special characters in name`() = runTest {
        val category =
            ContentCategory(
                categoryId = "100",
                categoryName = "Action & Adventure | Sci-Fi",
                parentId = 0,
                itemCount = 50,
            )
        val group = GroupNode(name = "US", categories = listOf(category))
        val tree = NavigationTree(groups = listOf(group))
        val successResource = Resource.Success(tree)

        every { mockRepository.observeLive() } returns flowOf(successResource)

        val result = observeLiveUseCase().first()

        assertTrue(result is Resource.Success)
        assertEquals(
            "Action & Adventure | Sci-Fi",
            (result as Resource.Success).data.groups[0].categories[0].categoryName,
        )
    }

    // ========== FLOW COLLECTION TESTS ==========

    @Test
    fun `UseCase returns Flow that can be collected multiple times`() = runTest {
        val mockTree = createMockNavigationTree()
        val successResource = Resource.Success(mockTree)

        every { mockRepository.observeMovies() } returns flowOf(successResource)

        // Collect first time
        val result1 = observeMoviesUseCase().first()
        assertTrue(result1 is Resource.Success)

        // Collect second time
        val result2 = observeMoviesUseCase().first()
        assertTrue(result2 is Resource.Success)

        // Repository should be called twice (no caching in UseCase)
        coVerify(exactly = 2) { mockRepository.observeMovies() }
    }
}
