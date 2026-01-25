package com.cactuvi.app.ui.continuewatching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cactuvi.app.domain.usecase.DeleteWatchHistoryItemUseCase
import com.cactuvi.app.domain.usecase.ObserveWatchHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContinueWatchingViewModel @Inject constructor(
    private val observeWatchHistoryUseCase: ObserveWatchHistoryUseCase,
    private val deleteWatchHistoryItemUseCase: DeleteWatchHistoryItemUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ContinueWatchingUiState())
    val uiState: StateFlow<ContinueWatchingUiState> = _uiState.asStateFlow()
    
    init {
        loadWatchHistory()
    }
    
    fun loadWatchHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = observeWatchHistoryUseCase(limit = 50)
            if (result.isSuccess) {
                _uiState.update { it.copy(
                    isLoading = false,
                    watchHistory = result.getOrNull() ?: emptyList()
                ) }
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load watch history"
                ) }
            }
        }
    }
    
    fun deleteItem(contentId: String) {
        viewModelScope.launch {
            val result = deleteWatchHistoryItemUseCase(contentId)
            if (result.isSuccess) {
                // Reload history after deletion
                loadWatchHistory()
            }
        }
    }
}
