package com.cactuvi.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cactuvi.app.data.models.ContentState
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.NavigationResult
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.utils.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
     * UI state derived reactively from navigation position and ContentState. ContentState combines
     * sync state + database state at Repository layer (root level only). For nested navigation
     * (Group/Category), we query database separately. Automatically updates when data changes or
     * user navigates.
     */
    val uiState: StateFlow<ContentUiState> =
        _navigationPosition
            .flatMapLatest { position ->
                when (position) {
                    is NavigationPosition.Root -> {
                        // Root level: Use unified ContentState from repository
                        getContentStateFlow().map { contentState ->
                            mapContentStateToUi(contentState)
                        }
                    }
                    is NavigationPosition.Group -> {
                        // Group level: Query database for categories in this group
                        observeGroupNavigation(position)
                    }
                    is NavigationPosition.Category -> {
                        // Category level: Query database for children or show content items
                        observeCategoryNavigation(position)
                    }
                }
            }
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

    /** Get the unified ContentState flow based on content type. */
    private fun getContentStateFlow(): StateFlow<ContentState<NavigationResult>> {
        return when (getContentType()) {
            ContentType.MOVIES -> repository.moviesContentState
            ContentType.SERIES -> repository.seriesContentState
            ContentType.LIVE -> repository.liveContentState
        }
    }

    /** Map ContentState (root level only) to UI-specific ContentUiState. */
    private fun mapContentStateToUi(contentState: ContentState<NavigationResult>): ContentUiState {
        return when (contentState) {
            is ContentState.Initial -> {
                // No sync started, no data
                ContentUiState.Syncing(null)
            }
            is ContentState.SyncingFirstTime -> {
                // First-time sync in progress - show blocking progress
                ContentUiState.Syncing(contentState.progress)
            }
            is ContentState.Ready -> {
                // Data available at root level - map to groups or categories
                // Note: backgroundSync is ignored per requirements (silent background sync)
                when (contentState.data) {
                    is NavigationResult.Groups -> {
                        ContentUiState.Content.Groups(
                            groups = contentState.data.groups,
                            breadcrumbPath = emptyList()
                        )
                    }
                    is NavigationResult.Categories -> {
                        ContentUiState.Content.Categories(
                            categories = contentState.data.categories,
                            breadcrumbPath = emptyList()
                        )
                    }
                }
            }
            is ContentState.Error -> {
                // Fatal error, no cached data
                ContentUiState.Error(contentState.error.message ?: "Sync failed")
            }
            is ContentState.ErrorWithCache -> {
                // Error but cached data available - show cached data (silent error)
                when (contentState.data) {
                    is NavigationResult.Groups -> {
                        ContentUiState.Content.Groups(
                            groups = contentState.data.groups,
                            breadcrumbPath = emptyList()
                        )
                    }
                    is NavigationResult.Categories -> {
                        ContentUiState.Content.Categories(
                            categories = contentState.data.categories,
                            breadcrumbPath = emptyList()
                        )
                    }
                }
            }
        }
    }

    /** Observe group navigation (categories within a group). */
    private fun observeGroupNavigation(position: NavigationPosition.Group): Flow<ContentUiState> {
        val contentType = getContentType()
        val (groupingEnabled, separator) = getGroupingSettings(preferencesManager, contentType)

        return repository.observeTopLevelNavigation(contentType, groupingEnabled, separator).map {
            result ->
            when (result) {
                is NavigationResult.Groups -> {
                    val categories = result.groups[position.groupName] ?: emptyList()
                    ContentUiState.Content.Categories(
                        categories = categories,
                        breadcrumbPath =
                            listOf(BreadcrumbItem(null, position.groupName, isGroup = true))
                    )
                }
                is NavigationResult.Categories -> {
                    // Grouping was disabled - show all categories
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
        position: NavigationPosition.Category
    ): Flow<ContentUiState> {
        val contentType = getContentType()

        return repository.observeChildCategories(contentType, position.categoryId).map { result ->
            when (result) {
                is NavigationResult.Categories -> {
                    val children = result.categories

                    if (children.isEmpty() || children.first().isLeaf) {
                        // Leaf level - show content items
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
                    ContentUiState.Loading
                }
            }
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
