package com.cactuvi.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cactuvi.app.domain.model.ContentCategory
import com.cactuvi.app.domain.model.GroupNode
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.usecase.ObserveMoviesUseCase
import com.cactuvi.app.domain.usecase.RefreshMoviesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val refreshMoviesUseCase: RefreshMoviesUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()
    
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
                                navigationTree = resource.data,
                                error = null
                            )
                            is Resource.Success -> state.copy(
                                isLoading = false,
                                navigationTree = resource.data,
                                error = null
                            )
                            is Resource.Error -> state.copy(
                                isLoading = false,
                                navigationTree = resource.data,
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
    
    fun selectGroup(group: GroupNode) {
        _uiState.update { it.copy(
            currentLevel = NavigationLevel.CATEGORIES,
            selectedGroup = group
        ) }
    }
    
    fun selectCategory(category: ContentCategory) {
        _uiState.update { it.copy(
            currentLevel = NavigationLevel.CONTENT,
            selectedCategory = category
        ) }
    }
    
    fun navigateBack(): Boolean {
        return when (_uiState.value.currentLevel) {
            NavigationLevel.GROUPS -> false
            NavigationLevel.CATEGORIES -> {
                _uiState.update { it.copy(currentLevel = NavigationLevel.GROUPS) }
                true
            }
            NavigationLevel.CONTENT -> {
                _uiState.update { it.copy(currentLevel = NavigationLevel.CATEGORIES) }
                true
            }
        }
    }
}
