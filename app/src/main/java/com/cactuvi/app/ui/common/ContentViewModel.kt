package com.cactuvi.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.utils.CategoryGrouper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base ViewModel for content screens (Movies, Series, Live TV). Eliminates code duplication by
 * providing shared navigation and state management.
 *
 * Subclasses only need to:
 * 1. Inject their specific UseCases
 * 2. Implement getPagedContent() to return their specific paged data
 *
 * @param T The content type (Movie, Series, or LiveChannel)
 */
abstract class ContentViewModel<T : Any> : ViewModel() {

    private val _uiState = MutableStateFlow(ContentUiState())
    val uiState: StateFlow<ContentUiState> = _uiState.asStateFlow()

    /**
     * Paged content for the selected category. Automatically updates when selectedCategoryId
     * changes. Subclasses implement this to provide their specific paged data.
     */
    val pagedContent: StateFlow<PagingData<T>> by lazy {
        uiState
            .flatMapLatest { state ->
                state.selectedCategoryId?.let { categoryId -> getPagedContent(categoryId) }
                    ?: flowOf(PagingData.empty())
            }
            .cachedIn(viewModelScope)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = PagingData.empty(),
            )
    }

    /**
     * Subclasses implement this to provide paged data for their content type. Called when a
     * category is selected.
     */
    protected abstract fun getPagedContent(categoryId: String): Flow<PagingData<T>>

    /**
     * Subclasses implement this to observe their content navigation tree. Called once in init
     * block.
     */
    protected abstract fun observeContent(): Flow<Resource<NavigationTree>>

    /** Subclasses implement this to trigger a refresh of their content. */
    protected abstract suspend fun refreshContent()

    init {
        observeContentInternal()
    }

    private fun observeContentInternal() {
        viewModelScope.launch {
            observeContent().collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = true,
                                navigationTree = resource.data?.toUtilNavigationTree(),
                                error = null,
                            )
                        }
                    }
                    is Resource.Success -> {
                        val tree = resource.data.toUtilNavigationTree()
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                navigationTree = tree,
                                error = null,
                            )
                        }
                        // Perform auto-navigation after state update
                        performAutoNavigation(tree)
                    }
                    is Resource.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                navigationTree = resource.data?.toUtilNavigationTree(),
                                error = if (resource.data == null) resource.error.message else null,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Perform auto-navigation based on navigation tree structure. Auto-skips levels when only 1
     * item exists.
     *
     * Auto-skip scenarios:
     * 1. Only 1 group → skip to CATEGORIES level, auto-select group
     * 2. Only 1 category in selected group → skip to CONTENT level, auto-select category
     * 3. Only 1 group with 1 category → skip directly to CONTENT level
     */
    private fun performAutoNavigation(tree: CategoryGrouper.NavigationTree) {
        when {
            // Scenario 3: Skip both levels (1 group with 1 category)
            tree.shouldSkipBothLevels -> {
                val singleGroup = tree.singleGroup!!
                val singleCategory = singleGroup.categories.first()
                _uiState.update {
                    it.copy(
                        currentLevel = NavigationLevel.CONTENT,
                        selectedGroupName = singleGroup.name,
                        selectedCategoryId = singleCategory.categoryId,
                    )
                }
            }
            // Scenario 1: Skip groups level (only 1 group)
            tree.shouldSkipGroups -> {
                val singleGroup = tree.singleGroup!!
                // Check if this single group also has only 1 category
                if (singleGroup.categories.size == 1) {
                    // Skip to content
                    val singleCategory = singleGroup.categories.first()
                    _uiState.update {
                        it.copy(
                            currentLevel = NavigationLevel.CONTENT,
                            selectedGroupName = singleGroup.name,
                            selectedCategoryId = singleCategory.categoryId,
                        )
                    }
                } else {
                    // Skip to categories
                    _uiState.update {
                        it.copy(
                            currentLevel = NavigationLevel.CATEGORIES,
                            selectedGroupName = singleGroup.name,
                        )
                    }
                }
            }
            // No auto-skip: show groups normally
            else -> {
                _uiState.update { it.copy(currentLevel = NavigationLevel.GROUPS) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshContent() }
    }

    /**
     * Select a group and navigate to its categories. Auto-skips to content level if group has only
     * 1 category.
     */
    fun selectGroup(groupName: String) {
        val tree = _uiState.value.navigationTree
        val group = tree?.findGroup(groupName)

        if (group != null && group.categories.size == 1) {
            // Auto-skip categories: jump directly to content
            val singleCategory = group.categories.first()
            _uiState.update {
                it.copy(
                    currentLevel = NavigationLevel.CONTENT,
                    selectedGroupName = groupName,
                    selectedCategoryId = singleCategory.categoryId,
                )
            }
        } else {
            // Normal navigation to categories
            _uiState.update {
                it.copy(
                    currentLevel = NavigationLevel.CATEGORIES,
                    selectedGroupName = groupName,
                )
            }
        }
    }

    fun selectCategory(categoryId: String) {
        _uiState.update {
            it.copy(
                currentLevel = NavigationLevel.CONTENT,
                selectedCategoryId = categoryId,
            )
        }
    }

    /**
     * Navigate back to previous level. Returns true if navigation handled, false if should exit to
     * home.
     *
     * Smart back navigation:
     * - Only navigates to levels that were actually shown to the user
     * - Skips auto-skipped levels (e.g., if categories were auto-skipped, back from content goes to
     *   groups)
     * - If groups were auto-skipped, back from any level exits to home
     */
    fun navigateBack(): Boolean {
        val state = _uiState.value
        val tree = state.navigationTree

        return when (state.currentLevel) {
            NavigationLevel.GROUPS -> false // Already at top, exit to home
            NavigationLevel.CATEGORIES -> {
                // Go back to groups only if groups level was shown (not auto-skipped)
                if (tree?.shouldSkipGroups == true) {
                    // Groups were auto-skipped, exit to home
                    false
                } else {
                    // Navigate back to groups
                    _uiState.update {
                        it.copy(
                            currentLevel = NavigationLevel.GROUPS,
                            selectedGroupName = null,
                        )
                    }
                    true
                }
            }
            NavigationLevel.CONTENT -> {
                // Go back to categories if they were shown
                val selectedGroupName = state.selectedGroupName
                val categoriesWereShown =
                    selectedGroupName != null &&
                        tree?.shouldSkipCategories(selectedGroupName) == false

                if (categoriesWereShown) {
                    // Navigate back to categories
                    _uiState.update {
                        it.copy(
                            currentLevel = NavigationLevel.CATEGORIES,
                            selectedCategoryId = null,
                        )
                    }
                    true
                } else {
                    // Categories were auto-skipped, check if groups were shown
                    if (tree?.shouldSkipGroups == true) {
                        // Both groups and categories were auto-skipped, exit to home
                        false
                    } else {
                        // Navigate back to groups (categories were skipped)
                        _uiState.update {
                            it.copy(
                                currentLevel = NavigationLevel.GROUPS,
                                selectedGroupName = null,
                                selectedCategoryId = null,
                            )
                        }
                        true
                    }
                }
            }
        }
    }

    /**
     * Convert domain NavigationTree to CategoryGrouper.NavigationTree. Temporary mapping until
     * adapters are refactored.
     */
    private fun NavigationTree.toUtilNavigationTree(): CategoryGrouper.NavigationTree {
        val utilGroups =
            this.groups.map { domainGroup ->
                CategoryGrouper.GroupNode(
                    name = domainGroup.name,
                    categories =
                        domainGroup.categories.map { domainCategory ->
                            com.cactuvi.app.data.models.Category(
                                categoryId = domainCategory.categoryId,
                                categoryName = domainCategory.categoryName,
                                parentId = domainCategory.parentId,
                            )
                        },
                )
            }
        return CategoryGrouper.NavigationTree(utilGroups)
    }
}
