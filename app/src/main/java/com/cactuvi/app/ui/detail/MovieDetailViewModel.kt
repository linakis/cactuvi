package com.cactuvi.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cactuvi.app.domain.usecase.AddToFavoritesUseCase
import com.cactuvi.app.domain.usecase.GetMovieInfoUseCase
import com.cactuvi.app.domain.usecase.ObserveIsFavoriteUseCase
import com.cactuvi.app.domain.usecase.ObserveWatchHistoryUseCase
import com.cactuvi.app.domain.usecase.RemoveFromFavoritesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Movie Detail screen.
 * Handles movie info, favorites, and watch history using MVVM + UDF pattern.
 */
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val getMovieInfoUseCase: GetMovieInfoUseCase,
    private val observeIsFavoriteUseCase: ObserveIsFavoriteUseCase,
    private val addToFavoritesUseCase: AddToFavoritesUseCase,
    private val removeFromFavoritesUseCase: RemoveFromFavoritesUseCase,
    private val observeWatchHistoryUseCase: ObserveWatchHistoryUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val vodId: Int = savedStateHandle.get<Int>("VOD_ID") ?: 0
    private val streamId: Int = savedStateHandle.get<Int>("STREAM_ID") ?: 0
    private val movieTitle: String = savedStateHandle.get<String>("TITLE") ?: ""
    private val posterUrl: String? = savedStateHandle.get<String>("POSTER_URL")
    
    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()
    
    init {
        loadMovieInfo()
        checkFavoriteStatus()
        loadResumePosition()
    }
    
    private fun loadMovieInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = getMovieInfoUseCase(vodId)
            if (result.isSuccess) {
                val info = result.getOrNull()
                _uiState.update { it.copy(
                    isLoading = false,
                    movieInfo = info,
                    duration = info?.info?.duration?.toLongOrNull()?.times(1000) ?: 0
                ) }
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load movie info"
                ) }
            }
        }
    }
    
    private fun checkFavoriteStatus() {
        viewModelScope.launch {
            val result = observeIsFavoriteUseCase(streamId.toString(), "movie")
            if (result.isSuccess) {
                _uiState.update { it.copy(isFavorite = result.getOrNull() ?: false) }
            }
        }
    }
    
    private fun loadResumePosition() {
        viewModelScope.launch {
            val result = observeWatchHistoryUseCase(limit = 100)
            if (result.isSuccess) {
                val history = result.getOrNull() ?: emptyList()
                val item = history.find { it.contentId == streamId.toString() }
                _uiState.update { it.copy(resumePosition = item?.resumePosition ?: 0) }
            }
        }
    }
    
    fun toggleFavorite() {
        viewModelScope.launch {
            val result = if (_uiState.value.isFavorite) {
                removeFromFavoritesUseCase(streamId.toString(), "movie")
            } else {
                addToFavoritesUseCase(
                    streamId.toString(),
                    "movie",
                    movieTitle,
                    posterUrl
                )
            }
            
            if (result.isSuccess) {
                _uiState.update { it.copy(isFavorite = !it.isFavorite) }
            }
        }
    }
}
