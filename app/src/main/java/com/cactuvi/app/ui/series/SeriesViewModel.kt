package com.cactuvi.app.ui.series

import androidx.paging.PagingData
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.domain.usecase.ObserveSeriesUseCase
import com.cactuvi.app.domain.usecase.RefreshSeriesUseCase
import com.cactuvi.app.ui.common.ContentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel for Series screen. Handles series data and navigation state using MVVM + UDF pattern.
 *
 * Extends ContentViewModel to reuse shared navigation and state management logic.
 */
@HiltViewModel
class SeriesViewModel
@Inject
constructor(
    private val observeSeriesUseCase: ObserveSeriesUseCase,
    private val refreshSeriesUseCase: RefreshSeriesUseCase,
    private val contentRepository: ContentRepository,
) : ContentViewModel<Series>() {

    override fun getPagedContent(categoryId: String): Flow<PagingData<Series>> {
        return contentRepository.getSeriesPaged(categoryId)
    }

    override fun observeContent(): Flow<Resource<NavigationTree>> {
        return observeSeriesUseCase()
    }

    override suspend fun refreshContent() {
        refreshSeriesUseCase()
    }
}
