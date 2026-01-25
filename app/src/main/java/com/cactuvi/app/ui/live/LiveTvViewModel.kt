package com.cactuvi.app.ui.live

import androidx.paging.PagingData
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.domain.usecase.ObserveLiveUseCase
import com.cactuvi.app.domain.usecase.RefreshLiveUseCase
import com.cactuvi.app.ui.common.ContentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * ViewModel for Live TV screen.
 * Handles live channel data and navigation state using MVVM + UDF pattern.
 * 
 * Extends ContentViewModel to reuse shared navigation and state management logic.
 * Supports grouping by language/region using "|" separator (configurable in settings).
 */
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val observeLiveUseCase: ObserveLiveUseCase,
    private val refreshLiveUseCase: RefreshLiveUseCase,
    private val contentRepository: ContentRepository
) : ContentViewModel<LiveChannel>() {
    
    override fun getPagedContent(categoryId: String): Flow<PagingData<LiveChannel>> {
        return contentRepository.getLiveStreamsPaged(categoryId)
    }
    
    override fun observeContent(): Flow<Resource<NavigationTree>> {
        return observeLiveUseCase()
    }
    
    override suspend fun refreshContent() {
        refreshLiveUseCase()
    }
}
