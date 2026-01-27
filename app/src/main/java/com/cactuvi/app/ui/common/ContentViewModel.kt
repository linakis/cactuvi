package com.cactuvi.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.DataState
import com.cactuvi.app.data.models.NavigationResult
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.utils.CategoryTreeBuilder
import com.cactuvi.app.utils.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Base ViewModel for content screens with reactive navigation.
 *
 * Uses Flow-based navigation that automatically updates when:
 * - Database content changes (after sync)
 * - User navigates to different levels
 *
 * No manual refresh needed - UI reacts to data changes automatically.
 *
 * @param T The content type (Movie, Series, or LiveChannel)
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ContentViewModel<T : Any>(
    protected val repository: ContentRepository,
    protected val preferencesManager: PreferencesManager,
) : ViewModel() {

    // Current navigation position
    private val _navigationPosition = MutableStateFlow<NavigationPosition>(NavigationPosition.Root)

    // Navigation stack for back navigation
    private val navigationStack = mutableListOf<NavigationPosition>()

    /**
     * UI state derived reactively from navigation position and database. Automatically updates when
     * data changes.
     */
    val uiState: StateFlow<ContentUiState> =
        combine(_navigationPosition, getSyncStateFlow()) { position, syncState ->
                Pair(position, syncState)
            }
            .flatMapLatest { (position, syncState) -> observeNavigationState(position, syncState) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ContentUiState.Initial
            )

    /**
     * Paged content for the current leaf category. Automatically updates when navigating to a leaf
     * level.
     */
    val pagedContent: StateFlow<PagingData<T>> by lazy {
        uiState
            .flatMapLatest { state ->
                when (state) {
                    is ContentUiState.Content.Items -> getPagedContent(state.categoryId)
                    else -> flowOf(PagingData.empty())
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

    /** Get the appropriate sync state flow based on content type. */
    private fun getSyncStateFlow(): StateFlow<DataState<Unit>> {
        return when (getContentType()) {
            ContentType.MOVIES -> repository.moviesState
            ContentType.SERIES -> repository.seriesState
            ContentType.LIVE -> repository.liveState
        }
    }

    /**
     * Observe navigation state reactively. Returns a Flow that emits UI state based on current
     * position and sync state.
     */
    private fun observeNavigationState(
        position: NavigationPosition,
        syncState: DataState<Unit>
    ): Flow<ContentUiState> {
        return when (position) {
            is NavigationPosition.Root -> observeRootNavigation(syncState)
            is NavigationPosition.Group -> observeGroupNavigation(position, syncState)
            is NavigationPosition.Category -> observeCategoryNavigation(position, syncState)
        }
    }

    /** Observe root level navigation (groups or categories). */
    private fun observeRootNavigation(syncState: DataState<Unit>): Flow<ContentUiState> {
        val contentType = getContentType()
        val (groupingEnabled, separator) = getGroupingSettings(preferencesManager, contentType)

        return repository.observeTopLevelNavigation(contentType, groupingEnabled, separator).map {
            result ->
            when (result) {
                is NavigationResult.Groups -> {
                    if (result.groups.isEmpty()) {
                        // Empty - check sync state
                        mapEmptyToSyncState(syncState)
                    } else {
                        ContentUiState.Content.Groups(
                            groups = result.groups,
                            breadcrumbPath = emptyList()
                        )
                    }
                }
                is NavigationResult.Categories -> {
                    if (result.categories.isEmpty()) {
                        mapEmptyToSyncState(syncState)
                    } else {
                        ContentUiState.Content.Categories(
                            categories = result.categories,
                            breadcrumbPath = emptyList()
                        )
                    }
                }
            }
        }
    }

    /** Observe group navigation (categories within a group). */
    private fun observeGroupNavigation(
        position: NavigationPosition.Group,
        syncState: DataState<Unit>
    ): Flow<ContentUiState> {
        val contentType = getContentType()
        val (groupingEnabled, separator) = getGroupingSettings(preferencesManager, contentType)

        return repository.observeTopLevelNavigation(contentType, groupingEnabled, separator).map {
            result ->
            when (result) {
                is NavigationResult.Groups -> {
                    val categories = result.groups[position.groupName] ?: emptyList()
                    if (categories.isEmpty()) {
                        mapEmptyToSyncState(syncState)
                    } else {
                        // Strip group prefix from category names
                        val strippedCategories =
                            categories.map { category ->
                                category.copy(
                                    categoryName =
                                        CategoryTreeBuilder.stripGroupPrefix(
                                            category.categoryName,
                                            separator
                                        )
                                )
                            }
                        ContentUiState.Content.Categories(
                            categories = strippedCategories,
                            breadcrumbPath =
                                listOf(BreadcrumbItem(null, position.groupName, isGroup = true))
                        )
                    }
                }
                is NavigationResult.Categories -> {
                    // Grouping was disabled - go back to root
                    ContentUiState.Content.Categories(
                        categories = result.categories,
                        breadcrumbPath = emptyList()
                    )
                }
            }
        }
    }

    /** Observe category navigation (children of a category). */
    private fun observeCategoryNavigation(
        position: NavigationPosition.Category,
        syncState: DataState<Unit>
    ): Flow<ContentUiState> {
        val contentType = getContentType()

        return combine(
            repository.observeCategory(contentType, position.categoryId),
            repository.observeChildCategories(contentType, position.categoryId)
        ) { category, result ->
            when (result) {
                is NavigationResult.Categories -> {
                    val children = result.categories

                    if (children.isEmpty()) {
                        // Leaf level - show content items
                        ContentUiState.Content.Items(
                            categoryId = position.categoryId,
                            breadcrumbPath = position.breadcrumbPath
                        )
                    } else if (children.first().isLeaf) {
                        // Children are leaves - show content items
                        ContentUiState.Content.Items(
                            categoryId = position.categoryId,
                            breadcrumbPath = position.breadcrumbPath
                        )
                    } else {
                        // Has non-leaf children - show categories
                        ContentUiState.Content.Categories(
                            categories = children,
                            breadcrumbPath = position.breadcrumbPath
                        )
                    }
                }
                is NavigationResult.Groups -> {
                    // Shouldn't happen at category level
                    mapEmptyToSyncState(syncState)
                }
            }
        }
    }

    /** Map empty results to appropriate UI state based on sync state. */
    private fun mapEmptyToSyncState(syncState: DataState<Unit>): ContentUiState {
        return when (syncState) {
            is DataState.Loading -> ContentUiState.Syncing(syncState.progress)
            is DataState.Error -> ContentUiState.Error(syncState.error.message ?: "Sync failed")
            else -> ContentUiState.Loading
        }
    }

    /** Navigate to a group (when showing groups at root level). */
    fun navigateToGroup(groupName: String) {
        navigationStack.add(_navigationPosition.value)
        _navigationPosition.value = NavigationPosition.Group(groupName)
    }

    /** Navigate to a category. */
    fun navigateToCategory(categoryId: String, categoryName: String? = null) {
        val currentBreadcrumbs =
            when (val state = uiState.value) {
                is ContentUiState.Content -> state.breadcrumbPath.toMutableList()
                else -> mutableListOf()
            }

        // Add category to breadcrumbs
        if (categoryName != null) {
            currentBreadcrumbs.add(BreadcrumbItem(categoryId, categoryName, isGroup = false))
        }

        navigationStack.add(_navigationPosition.value)
        _navigationPosition.value =
            NavigationPosition.Category(
                categoryId = categoryId,
                breadcrumbPath = currentBreadcrumbs
            )
    }

    /** Navigate back. Returns true if handled, false if should exit. */
    fun navigateBack(): Boolean {
        if (navigationStack.isEmpty()) return false

        _navigationPosition.value = navigationStack.removeAt(navigationStack.lastIndex)
        return true
    }

    /** Handle separator change (reload root with new grouping). */
    fun onSeparatorChanged() {
        navigationStack.clear()
        _navigationPosition.value = NavigationPosition.Root
    }

    /** Refresh current content (no-op for reactive - data auto-updates). */
    fun refresh() {
        // With reactive Flows, UI auto-updates when data changes.
        // This is kept for API compatibility.
        // Could trigger a repository refresh if needed.
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

/** Navigation position sealed class. Represents where the user is in the navigation hierarchy. */
sealed class NavigationPosition {
    /** At root level (groups or top-level categories) */
    data object Root : NavigationPosition()

    /** Viewing categories within a specific group */
    data class Group(val groupName: String) : NavigationPosition()

    /** Viewing a specific category (children or content) */
    data class Category(val categoryId: String, val breadcrumbPath: List<BreadcrumbItem>) :
        NavigationPosition()
}
