package com.cactuvi.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.domain.usecase.ObserveMoviesUseCase
import com.cactuvi.app.domain.usecase.RefreshMoviesUseCase
import com.cactuvi.app.utils.CategoryGrouper
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

/**
 * ViewModel for Movies screen.
 * Handles movies data and navigation state using MVVM + UDF pattern.
 */
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val observeMoviesUseCase: ObserveMoviesUseCase,
    private val refreshMoviesUseCase: RefreshMoviesUseCase,
    private val contentRepository: ContentRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()
    
    /**
     * Paged movies for the selected category.
     * Automatically updates when selectedCategoryId changes.
     */
    val pagedMovies: StateFlow<PagingData<Movie>> = uiState
        .flatMapLatest { state ->
            state.selectedCategoryId?.let { categoryId ->
                contentRepository.getMoviesPaged(categoryId)
            } ?: flowOf(PagingData.empty())
        }
        .cachedIn(viewModelScope)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = PagingData.empty()
        )
    
    init {
        observeMovies()
    }
    
    private fun observeMovies() {
        viewModelScope.launch {
            observeMoviesUseCase()
                .collectLatest { resource ->
                    _uiState.update { state ->
                        when (resource) {
                            is Resource.Loading -> state.copy(
                                isLoading = true,
                                navigationTree = resource.data?.toUtilNavigationTree(),
                                error = null
                            )
                            is Resource.Success -> state.copy(
                                isLoading = false,
                                navigationTree = resource.data.toUtilNavigationTree(),
                                error = null
                            )
                            is Resource.Error -> state.copy(
                                isLoading = false,
                                navigationTree = resource.data?.toUtilNavigationTree(),
                                error = if (resource.data == null) resource.error.message else null
                            )
                        }
                    }
                }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            refreshMoviesUseCase()
        }
    }
    
    fun selectGroup(groupName: String) {
        _uiState.update { it.copy(
            currentLevel = NavigationLevel.CATEGORIES,
            selectedGroupName = groupName
        ) }
    }
    
    fun selectCategory(categoryId: String) {
        _uiState.update { it.copy(
            currentLevel = NavigationLevel.CONTENT,
            selectedCategoryId = categoryId
        ) }
    }
    
    fun navigateBack(): Boolean {
        return when (_uiState.value.currentLevel) {
            NavigationLevel.GROUPS -> false
            NavigationLevel.CATEGORIES -> {
                _uiState.update { it.copy(
                    currentLevel = NavigationLevel.GROUPS,
                    selectedGroupName = null
                ) }
                true
            }
            NavigationLevel.CONTENT -> {
                _uiState.update { it.copy(
                    currentLevel = NavigationLevel.CATEGORIES,
                    selectedCategoryId = null
                ) }
                true
            }
        }
    }
    
    /**
     * Convert domain NavigationTree to CategoryGrouper.NavigationTree.
     * Temporary mapping until adapters are refactored in Phase 4.
     */
    private fun com.cactuvi.app.domain.model.NavigationTree.toUtilNavigationTree(): CategoryGrouper.NavigationTree {
        val utilGroups = this.groups.map { domainGroup ->
            CategoryGrouper.GroupNode(
                name = domainGroup.name,
                categories = domainGroup.categories.map { domainCategory ->
                    com.cactuvi.app.data.models.Category(
                        categoryId = domainCategory.categoryId,
                        categoryName = domainCategory.categoryName,
                        parentId = domainCategory.parentId
                    )
                }
            )
        }
        return CategoryGrouper.NavigationTree(utilGroups)
    }
}
