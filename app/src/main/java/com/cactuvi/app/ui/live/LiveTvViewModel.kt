package com.cactuvi.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.domain.usecase.ObserveLiveUseCase
import com.cactuvi.app.domain.usecase.RefreshLiveUseCase
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
 * ViewModel for Live TV screen.
 * Handles live channel data and navigation state using MVVM + UDF pattern.
 */
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val observeLiveUseCase: ObserveLiveUseCase,
    private val refreshLiveUseCase: RefreshLiveUseCase,
    private val contentRepository: ContentRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()
    
    /**
     * Paged live channels for the selected category.
     * Automatically updates when selectedCategoryId changes.
     */
    val pagedChannels: StateFlow<PagingData<LiveChannel>> = uiState
        .flatMapLatest { state ->
            state.selectedCategoryId?.let { categoryId ->
                contentRepository.getLiveStreamsPaged(categoryId)
            } ?: flowOf(PagingData.empty())
        }
        .cachedIn(viewModelScope)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = PagingData.empty()
        )
    
    init {
        observeLiveChannels()
    }
    
    private fun observeLiveChannels() {
        viewModelScope.launch {
            observeLiveUseCase()
                .collectLatest { resource ->
                    _uiState.update { state ->
                        when (resource) {
                            is Resource.Loading -> state.copy(
                                isLoading = true,
                                categories = resource.data ?: state.categories,
                                error = null
                            )
                            is Resource.Success -> state.copy(
                                isLoading = false,
                                categories = resource.data,
                                error = null
                            )
                            is Resource.Error -> state.copy(
                                isLoading = false,
                                categories = resource.data ?: state.categories,
                                error = if (resource.data == null || state.categories.isEmpty()) resource.error.message else null
                            )
                        }
                    }
                }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            refreshLiveUseCase()
        }
    }
    
    fun selectCategory(categoryId: String) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }
    
    fun navigateBack(): Boolean {
        return if (_uiState.value.isViewingCategory) {
            _uiState.update { it.copy(selectedCategoryId = null) }
            true
        } else {
            false
        }
    }
}
