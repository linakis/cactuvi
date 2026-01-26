package com.cactuvi.app.ui.series

import android.content.Context
import androidx.paging.PagingData
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.data.repository.ContentRepositoryImpl
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.ui.common.ContentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** ViewModel for Series screen with dynamic level-by-level navigation. */
@HiltViewModel
class SeriesViewModel
@Inject
constructor(
    repository: ContentRepository,
    @ApplicationContext context: Context,
) : ContentViewModel<Series>(repository as ContentRepositoryImpl, context) {

    override fun getContentType(): ContentType = ContentType.SERIES

    override fun getPagedContent(categoryId: String): Flow<PagingData<Series>> {
        return (repository as ContentRepositoryImpl).getSeriesPaged(categoryId)
    }
}
