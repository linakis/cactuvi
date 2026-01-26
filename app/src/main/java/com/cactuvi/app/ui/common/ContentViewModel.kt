package com.cactuvi.app.ui.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.NavigationResult
import com.cactuvi.app.data.repository.ContentRepositoryImpl
import com.cactuvi.app.utils.CategoryTreeBuilder
import com.cactuvi.app.utils.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base ViewModel for content screens with dynamic level-by-level navigation.
 *
 * Handles:
 * - Loading root level (groups or categories)
 * - Navigating to child categories
 * - Auto-skipping single-child levels
 * - Breadcrumb tracking
 * - Navigation stack management
 *
 * @param T The content type (Movie, Series, or LiveChannel)
 */
abstract class ContentViewModel<T : Any>(
    protected val repository: ContentRepositoryImpl,
    protected val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentUiState())
    val uiState: StateFlow<ContentUiState> = _uiState.asStateFlow()

    // Navigation stack (categoryIds, null = root)
    private val navigationStack = mutableListOf<String?>()

    /**
     * Paged content for the current leaf category. Automatically updates when navigating to a leaf
     * level.
     */
    val pagedContent: StateFlow<PagingData<T>> by lazy {
        uiState
            .flatMapLatest { state ->
                if (state.isLeafLevel && state.currentCategoryId != null) {
                    getPagedContent(state.currentCategoryId)
                } else {
                    flowOf(PagingData.empty())
                }
            }
            .cachedIn(viewModelScope)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = PagingData.empty(),
            )
    }

    /** Subclasses provide their content type */
    protected abstract fun getContentType(): ContentType

    /** Subclasses provide paged data for a category */
    protected abstract fun getPagedContent(categoryId: String): Flow<PagingData<T>>

    init {
        loadRoot()
    }

    /** Load root level (groups or top-level categories). */
    fun loadRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val prefsManager = PreferencesManager.getInstance(context)
                val contentType = getContentType()
                val (groupingEnabled, separator) = getGroupingSettings(prefsManager, contentType)

                when (
                    val result =
                        repository.getTopLevelNavigation(contentType, groupingEnabled, separator)
                ) {
                    is NavigationResult.Groups -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentGroups = result.groups,
                                currentCategories = emptyList(),
                                currentCategoryId = null,
                                breadcrumbPath = emptyList(),
                                isLeafLevel = false,
                                error = null,
                            )
                        }
                    }
                    is NavigationResult.Categories -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentCategories = result.categories,
                                currentGroups = null,
                                currentCategoryId = null,
                                breadcrumbPath = emptyList(),
                                isLeafLevel = false,
                                error = null,
                            )
                        }
                    }
                }

                navigationStack.clear()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load content",
                    )
                }
            }
        }
    }

    /** Navigate to a group (when showing groups at root level). */
    fun navigateToGroup(groupName: String) {
        val groups = _uiState.value.currentGroups ?: return
        val categories = groups[groupName] ?: return

        // Apply prefix stripping
        val prefsManager = PreferencesManager.getInstance(context)
        val separator =
            when (getContentType()) {
                ContentType.MOVIES -> prefsManager.getMoviesGroupingSeparator()
                ContentType.SERIES -> prefsManager.getSeriesGroupingSeparator()
                ContentType.LIVE -> prefsManager.getLiveGroupingSeparator()
            }

        val strippedCategories =
            categories.map { category ->
                category.copy(
                    categoryName =
                        CategoryTreeBuilder.stripGroupPrefix(category.categoryName, separator)
                )
            }

        // Check for single-child skip (auto-navigate if only 1 category that's not a leaf)
        if (strippedCategories.size == 1 && !strippedCategories.first().isLeaf) {
            navigationStack.add(null) // Mark that we came from root
            navigateToCategory(strippedCategories.first().categoryId, groupName)
            return
        }

        navigationStack.add(null) // Mark that we came from root
        _uiState.update {
            it.copy(
                currentCategories = strippedCategories,
                currentGroups = null,
                breadcrumbPath = listOf(BreadcrumbItem(null, groupName, isGroup = true)),
                isLeafLevel = false,
            )
        }
    }

    /** Navigate to a category. */
    fun navigateToCategory(categoryId: String, groupName: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val category = repository.getCategoryById(getContentType(), categoryId)
                val result = repository.getChildCategories(getContentType(), categoryId)

                when (result) {
                    is NavigationResult.Categories -> {
                        val children = result.categories

                        if (children.isEmpty() || children.first().isLeaf) {
                            // Leaf level - show content
                            navigationStack.add(categoryId)
                            val breadcrumbs = buildBreadcrumbs(groupName, category)

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    currentCategoryId = categoryId,
                                    isLeafLevel = true,
                                    breadcrumbPath = breadcrumbs,
                                    currentCategories = emptyList(),
                                )
                            }
                        } else {
                            // Has children - show categories
                            navigationStack.add(categoryId)
                            val breadcrumbs = buildBreadcrumbs(groupName, category)

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    currentCategories = children,
                                    isLeafLevel = false,
                                    breadcrumbPath = breadcrumbs,
                                )
                            }
                        }
                    }
                    is NavigationResult.Groups -> {
                        // Shouldn't happen at this level
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load category",
                    )
                }
            }
        }
    }

    /** Navigate back. Returns true if handled, false if should exit. */
    fun navigateBack(): Boolean {
        if (navigationStack.isEmpty()) return false

        navigationStack.removeLastOrNull()

        if (navigationStack.isEmpty()) {
            loadRoot()
        } else {
            val previousCategoryId = navigationStack.lastOrNull()
            if (previousCategoryId == null) {
                loadRoot()
            } else {
                navigateToCategory(previousCategoryId)
            }
        }

        return true
    }

    /** Handle separator change (reload root with new grouping). */
    fun onSeparatorChanged() {
        navigationStack.clear()
        loadRoot()
    }

    /** Refresh current content. */
    fun refresh() {
        // For now, just reload root
        // TODO: Could be smarter and reload current level
        loadRoot()
    }

    private fun buildBreadcrumbs(groupName: String?, category: Category?): List<BreadcrumbItem> {
        val breadcrumbs = _uiState.value.breadcrumbPath.toMutableList()

        // Add group if not already in breadcrumbs
        if (groupName != null && breadcrumbs.none { it.isGroup }) {
            breadcrumbs.add(BreadcrumbItem(null, groupName, isGroup = true))
        }

        // Add category if provided
        category?.let {
            breadcrumbs.add(BreadcrumbItem(it.categoryId, it.categoryName, isGroup = false))
        }

        return breadcrumbs
    }

    private fun getGroupingSettings(
        prefsManager: PreferencesManager,
        contentType: ContentType
    ): Pair<Boolean, String> {
        return when (contentType) {
            ContentType.MOVIES -> {
                Pair(
                    prefsManager.isMoviesGroupingEnabled(),
                    prefsManager.getMoviesGroupingSeparator()
                )
            }
            ContentType.SERIES -> {
                Pair(
                    prefsManager.isSeriesGroupingEnabled(),
                    prefsManager.getSeriesGroupingSeparator()
                )
            }
            ContentType.LIVE -> {
                Pair(prefsManager.isLiveGroupingEnabled(), prefsManager.getLiveGroupingSeparator())
            }
        }
    }
}
