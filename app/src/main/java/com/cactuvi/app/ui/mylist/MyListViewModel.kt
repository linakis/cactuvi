package com.cactuvi.app.ui.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cactuvi.app.domain.usecase.ObserveFavoritesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyListViewModel @Inject constructor(
    private val observeFavoritesUseCase: ObserveFavoritesUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MyListUiState())
    val uiState: StateFlow<MyListUiState> = _uiState.asStateFlow()
    
    init {
        loadFavorites(contentType = null)
    }
    
    fun loadFavorites(contentType: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = observeFavoritesUseCase(contentType)
            if (result.isSuccess) {
                _uiState.update { it.copy(
                    isLoading = false,
                    favorites = result.getOrNull() ?: emptyList()
                ) }
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load favorites"
                ) }
            }
        }
    }
}
