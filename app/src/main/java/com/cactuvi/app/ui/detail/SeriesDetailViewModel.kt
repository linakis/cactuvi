package com.cactuvi.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cactuvi.app.domain.usecase.AddToFavoritesUseCase
import com.cactuvi.app.domain.usecase.GetSeriesInfoUseCase
import com.cactuvi.app.domain.usecase.ObserveIsFavoriteUseCase
import com.cactuvi.app.domain.usecase.RemoveFromFavoritesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SeriesDetailViewModel
@Inject
constructor(
    private val getSeriesInfoUseCase: GetSeriesInfoUseCase,
    private val observeIsFavoriteUseCase: ObserveIsFavoriteUseCase,
    private val addToFavoritesUseCase: AddToFavoritesUseCase,
    private val removeFromFavoritesUseCase: RemoveFromFavoritesUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: Int = savedStateHandle.get<Int>("SERIES_ID") ?: 0
    private val seriesTitle: String = savedStateHandle.get<String>("TITLE") ?: ""
    private val coverUrl: String? = savedStateHandle.get<String>("COVER_URL")

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        loadSeriesInfo()
        checkFavoriteStatus()
    }

    private fun loadSeriesInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = getSeriesInfoUseCase(seriesId)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        seriesInfo = result.getOrNull(),
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to load series info",
                    )
                }
            }
        }
    }

    private fun checkFavoriteStatus() {
        viewModelScope.launch {
            val result = observeIsFavoriteUseCase(seriesId.toString(), "series")
            if (result.isSuccess) {
                _uiState.update { it.copy(isFavorite = result.getOrNull() ?: false) }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val result =
                if (_uiState.value.isFavorite) {
                    removeFromFavoritesUseCase(seriesId.toString(), "series")
                } else {
                    addToFavoritesUseCase(
                        seriesId.toString(),
                        "series",
                        seriesTitle,
                        coverUrl,
                    )
                }

            if (result.isSuccess) {
                _uiState.update { it.copy(isFavorite = !it.isFavorite) }
            }
        }
    }
}
