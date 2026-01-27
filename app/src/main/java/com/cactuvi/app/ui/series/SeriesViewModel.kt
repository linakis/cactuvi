package com.cactuvi.app.ui.series

import androidx.paging.PagingData
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.ui.common.ContentViewModel
import com.cactuvi.app.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** ViewModel for Series screen with dynamic level-by-level navigation. */
@HiltViewModel
class SeriesViewModel
@Inject
constructor(
    repository: ContentRepository,
    preferencesManager: PreferencesManager,
) : ContentViewModel<Series>(repository, preferencesManager) {

    override fun getContentType(): ContentType = ContentType.SERIES

    override fun getPagedContent(categoryId: String): Flow<PagingData<Series>> {
        return repository.getSeriesPaged(categoryId)
    }
}
