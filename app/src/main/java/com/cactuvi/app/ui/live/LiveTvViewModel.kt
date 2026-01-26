package com.cactuvi.app.ui.live

import android.content.Context
import androidx.paging.PagingData
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.data.repository.ContentRepositoryImpl
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.ui.common.ContentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** ViewModel for Live TV screen with dynamic level-by-level navigation. */
@HiltViewModel
class LiveTvViewModel
@Inject
constructor(
    repository: ContentRepository,
    @ApplicationContext context: Context,
) : ContentViewModel<LiveChannel>(repository as ContentRepositoryImpl, context) {

    override fun getContentType(): ContentType = ContentType.LIVE

    override fun getPagedContent(categoryId: String): Flow<PagingData<LiveChannel>> {
        return (repository as ContentRepositoryImpl).getLiveStreamsPaged(categoryId)
    }
}
