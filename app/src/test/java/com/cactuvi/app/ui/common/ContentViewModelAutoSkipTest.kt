package com.cactuvi.app.ui.common

import androidx.paging.PagingData
import com.cactuvi.app.domain.model.ContentCategory
import com.cactuvi.app.domain.model.GroupNode
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for ContentViewModel auto-skip navigation logic.
 * 
 * Requirements:
 * 1. Auto-skip groups when only 1 group exists → Jump to CATEGORIES
 * 2. Auto-skip categories when only 1 category in group → Jump to CONTENT
 * 3. Auto-skip both when 1 group with 1 category → Jump directly to CONTENT
 * 4. navigateBack() returns to last real screen shown (skips auto-skipped levels)
 * 5. Breadcrumb shows skipped levels for context
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContentViewModelAutoSkipTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var contentRepository: ContentRepository
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        contentRepository = mockk(relaxed = true)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    // ========== SCENARIO 1: AUTO-SKIP GROUPS (1 GROUP, 2+ CATEGORIES) ==========
    
    @Test
    fun `auto-skips to CATEGORIES when only 1 group with multiple categories`() = runTest {
        // Given: 1 group with 2 categories
        val tree = createTree(
            listOf("GR" to listOf("GR | Action", "GR | Drama"))
        )
        
        // When: ViewModel loads
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Then: Should auto-skip to CATEGORIES
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.CATEGORIES, state.currentLevel)
        assertEquals("GR", state.selectedGroupName)
        assertNull(state.selectedCategoryId)
    }
    
    @Test
    fun `breadcrumb shows skipped group`() = runTest {
        val tree = createTree(listOf("GR" to listOf("GR | Action", "GR | Drama")))
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(listOf("GR"), state.breadcrumbPath)
    }
    
    @Test
    fun `navigateBack from CATEGORIES exits when groups auto-skipped`() = runTest {
        val tree = createTree(listOf("GR" to listOf("GR | Action", "GR | Drama")))
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // When: Back from CATEGORIES
        val handled = viewModel.navigateBack()
        
        // Then: Should exit (groups were never shown)
        assertFalse(handled)
    }
    
    // ========== SCENARIO 2: AUTO-SKIP CATEGORIES (MULTIPLE GROUPS, 1 CATEGORY IN SELECTED GROUP) ==========
    
    @Test
    fun `selectGroup auto-skips to CONTENT when group has 1 category`() = runTest {
        // Given: 3 groups, IT has only 1 category
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama"),
            "DE" to listOf("DE | Action", "DE | Drama"),
            "IT" to listOf("IT | Action")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // When: Select IT group (has 1 category)
        viewModel.selectGroup("IT")
        
        // Then: Should auto-skip to CONTENT
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.CONTENT, state.currentLevel)
        assertEquals("IT", state.selectedGroupName)
        assertEquals("5", state.selectedCategoryId)  // IT|Action is 5th category
    }
    
    @Test
    fun `selectGroup does NOT auto-skip when group has multiple categories`() = runTest {
        val tree = createTree(listOf("GR" to listOf("GR | Action", "GR | Drama")))
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Re-select GR (has 2 categories)
        viewModel.selectGroup("GR")
        
        // Then: Should stay at CATEGORIES
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.CATEGORIES, state.currentLevel)
        assertNull(state.selectedCategoryId)
    }
    
    @Test
    fun `navigateBack from CONTENT goes to GROUPS when categories auto-skipped`() = runTest {
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama"),
            "IT" to listOf("IT | Action")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Select IT (auto-skips to CONTENT)
        viewModel.selectGroup("IT")
        
        // When: Back from CONTENT
        val handled = viewModel.navigateBack()
        
        // Then: Should go to GROUPS (categories were skipped)
        assertTrue(handled)
        assertEquals(NavigationLevel.GROUPS, viewModel.uiState.value.currentLevel)
    }
    
    // ========== SCENARIO 3: AUTO-SKIP BOTH (1 GROUP, 1 CATEGORY) ==========
    
    @Test
    fun `auto-skips to CONTENT when 1 group with 1 category`() = runTest {
        val tree = createTree(listOf("GR" to listOf("GR | Action")))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Then: Should skip both levels
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.CONTENT, state.currentLevel)
        assertEquals("GR", state.selectedGroupName)
        assertEquals("1", state.selectedCategoryId)
    }
    
    @Test
    fun `breadcrumb shows both skipped levels`() = runTest {
        val tree = createTree(listOf("GR" to listOf("GR | Action")))
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(listOf("GR", "GR | Action"), state.breadcrumbPath)
    }
    
    @Test
    fun `navigateBack from CONTENT exits when both levels auto-skipped`() = runTest {
        val tree = createTree(listOf("GR" to listOf("GR | Action")))
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // When: Back from CONTENT
        val handled = viewModel.navigateBack()
        
        // Then: Should exit (both levels were skipped)
        assertFalse(handled)
    }
    
    // ========== SCENARIO 4: NORMAL NAVIGATION (NO AUTO-SKIP) ==========
    
    @Test
    fun `no auto-skip when multiple groups exist`() = runTest {
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action"),
            "DE" to listOf("DE | Action")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Then: Should stay at GROUPS
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.GROUPS, state.currentLevel)
        assertNull(state.selectedGroupName)
    }
    
    @Test
    fun `navigateBack full path when no auto-skip`() = runTest {
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama"),
            "DE" to listOf("DE | Action")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Navigate: GROUPS → CATEGORIES → CONTENT
        viewModel.selectGroup("GR")
        viewModel.selectCategory("1")
        
        // Back: CONTENT → CATEGORIES
        assertTrue(viewModel.navigateBack())
        assertEquals(NavigationLevel.CATEGORIES, viewModel.uiState.value.currentLevel)
        
        // Back: CATEGORIES → GROUPS
        assertTrue(viewModel.navigateBack())
        assertEquals(NavigationLevel.GROUPS, viewModel.uiState.value.currentLevel)
        
        // Back: GROUPS → Exit
        assertFalse(viewModel.navigateBack())
    }
    
    // ========== ADDITIONAL EDGE CASES ==========
    
    @Test
    fun `breadcrumb shows group and category when both shown normally`() = runTest {
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Navigate to content normally (no auto-skip since we manually navigate)
        viewModel.selectGroup("GR")  // At CATEGORIES now
        assertEquals(listOf("GR"), viewModel.uiState.value.breadcrumbPath)
        
        viewModel.selectCategory("1")  // At CONTENT now
        assertEquals(listOf("GR", "GR | Action"), viewModel.uiState.value.breadcrumbPath)
    }
    
    @Test
    fun `selectCategory updates state to CONTENT`() = runTest {
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Navigate to categories
        viewModel.selectGroup("GR")
        
        // Select category
        viewModel.selectCategory("1")
        
        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.CONTENT, state.currentLevel)
        assertEquals("1", state.selectedCategoryId)
    }
    
    @Test
    fun `multiple selectGroup calls with different auto-skip behaviors`() = runTest {
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama"),
            "DE" to listOf("DE | Action", "DE | Drama"),
            "IT" to listOf("IT | Action")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Select GR (2 categories, no auto-skip)
        viewModel.selectGroup("GR")
        assertEquals(NavigationLevel.CATEGORIES, viewModel.uiState.value.currentLevel)
        assertNull(viewModel.uiState.value.selectedCategoryId)
        
        // Navigate back to groups
        viewModel.navigateBack()
        
        // Select IT (1 category, auto-skip to content)
        viewModel.selectGroup("IT")
        assertEquals(NavigationLevel.CONTENT, viewModel.uiState.value.currentLevel)
        assertEquals("5", viewModel.uiState.value.selectedCategoryId)
    }
    
    @Test
    fun `refresh triggers content reload`() = runTest {
        val tree = createTree(listOf("GR" to listOf("GR | Action")))
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Refresh should not throw
        viewModel.refresh()
        advanceUntilIdle()
        
        // State should remain stable
        val state = viewModel.uiState.value
        assertNotNull(state.navigationTree)
    }
    
    @Test
    fun `state helpers return correct values`() = runTest {
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama"),
            "DE" to listOf("DE | Action")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // At GROUPS
        assertTrue(viewModel.uiState.value.isViewingGroups)
        assertFalse(viewModel.uiState.value.isViewingCategories)
        assertFalse(viewModel.uiState.value.isViewingContent)
        
        // Navigate to CATEGORIES
        viewModel.selectGroup("GR")
        assertFalse(viewModel.uiState.value.isViewingGroups)
        assertTrue(viewModel.uiState.value.isViewingCategories)
        assertFalse(viewModel.uiState.value.isViewingContent)
        
        // Navigate to CONTENT
        viewModel.selectCategory("1")
        assertFalse(viewModel.uiState.value.isViewingGroups)
        assertFalse(viewModel.uiState.value.isViewingCategories)
        assertTrue(viewModel.uiState.value.isViewingContent)
    }
    
    @Test
    fun `selectedGroup helper returns correct group node`() = runTest {
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama"),
            "DE" to listOf("DE | Action")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // No group selected initially
        assertNull(viewModel.uiState.value.selectedGroup)
        
        // Select GR
        viewModel.selectGroup("GR")
        val selectedGroup = viewModel.uiState.value.selectedGroup
        assertNotNull(selectedGroup)
        assertEquals("GR", selectedGroup?.name)
        assertEquals(2, selectedGroup?.categories?.size)
    }
    
    @Test
    fun `auto-skip detection helpers work correctly`() = runTest {
        // Test shouldAutoSkipGroups
        val tree1 = createTree(listOf("GR" to listOf("GR | Action", "GR | Drama")))
        val vm1 = createViewModel(tree1)
        advanceUntilIdle()
        assertTrue(vm1.uiState.value.shouldAutoSkipGroups)
        
        // Test shouldAutoSkipBoth
        val tree2 = createTree(listOf("GR" to listOf("GR | Action")))
        val vm2 = createViewModel(tree2)
        advanceUntilIdle()
        assertTrue(vm2.uiState.value.shouldAutoSkipBoth)
        
        // Test no auto-skip
        val tree3 = createTree(listOf(
            "GR" to listOf("GR | Action"),
            "DE" to listOf("DE | Action")
        ))
        val vm3 = createViewModel(tree3)
        advanceUntilIdle()
        assertFalse(vm3.uiState.value.shouldAutoSkipGroups)
        assertFalse(vm3.uiState.value.shouldAutoSkipBoth)
    }
    
    @Test
    fun `complex navigation scenario with mixed auto-skip`() = runTest {
        // Scenario: User has filtered content leaving:
        // - GR with 2 categories
        // - IT with 1 category
        val tree = createTree(listOf(
            "GR" to listOf("GR | Action", "GR | Drama"),
            "IT" to listOf("IT | Action")
        ))
        
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        // Start at GROUPS (2 groups, no auto-skip)
        assertEquals(NavigationLevel.GROUPS, viewModel.uiState.value.currentLevel)
        
        // Select GR → CATEGORIES (2 categories, no auto-skip)
        viewModel.selectGroup("GR")
        assertEquals(NavigationLevel.CATEGORIES, viewModel.uiState.value.currentLevel)
        
        // Select GR|Action → CONTENT
        viewModel.selectCategory("1")
        assertEquals(NavigationLevel.CONTENT, viewModel.uiState.value.currentLevel)
        
        // Back → CATEGORIES
        assertTrue(viewModel.navigateBack())
        assertEquals(NavigationLevel.CATEGORIES, viewModel.uiState.value.currentLevel)
        
        // Back → GROUPS
        assertTrue(viewModel.navigateBack())
        assertEquals(NavigationLevel.GROUPS, viewModel.uiState.value.currentLevel)
        
        // Now select IT → CONTENT (auto-skip categories)
        viewModel.selectGroup("IT")
        assertEquals(NavigationLevel.CONTENT, viewModel.uiState.value.currentLevel)
        assertEquals("3", viewModel.uiState.value.selectedCategoryId)
        
        // Back → GROUPS (skips categories since they were never shown)
        assertTrue(viewModel.navigateBack())
        assertEquals(NavigationLevel.GROUPS, viewModel.uiState.value.currentLevel)
    }
    
    @Test
    fun `showLoading showError showContent helpers`() = runTest {
        val tree = createTree(listOf("GR" to listOf("GR | Action")))
        val viewModel = createViewModel(tree)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.showLoading)
        assertFalse(state.showError)
        assertTrue(state.showContent)
    }
    
    // ========== HELPER METHODS ==========
    
    private fun createTree(groupsData: List<Pair<String, List<String>>>): NavigationTree {
        var categoryId = 1
        val groups = groupsData.map { (groupName, categoryNames) ->
            val categories = categoryNames.map { catName ->
                ContentCategory(
                    categoryId = (categoryId++).toString(),
                    categoryName = catName,
                    parentId = 0,
                    itemCount = 10
                )
            }
            GroupNode(name = groupName, categories = categories)
        }
        return NavigationTree(groups)
    }
    
    private fun createViewModel(tree: NavigationTree): TestContentViewModel {
        return TestContentViewModel(
            observeContentFlow = flowOf(Resource.Success(tree)),
            contentRepository = contentRepository
        )
    }
    
    // Test ViewModel implementation
    private class TestContentViewModel(
        private val observeContentFlow: Flow<Resource<NavigationTree>>,
        private val contentRepository: ContentRepository
    ) : ContentViewModel<String>() {
        
        override fun getPagedContent(categoryId: String): Flow<PagingData<String>> {
            return flowOf(PagingData.empty())
        }
        
        override fun observeContent(): Flow<Resource<NavigationTree>> {
            return observeContentFlow
        }
        
        override suspend fun refreshContent() {
            // No-op
        }
    }
}
