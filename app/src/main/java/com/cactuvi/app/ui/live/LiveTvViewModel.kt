package com.cactuvi.app.ui.live

import androidx.paging.PagingData
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.ui.common.ContentViewModel
import com.cactuvi.app.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** ViewModel for Live TV screen with dynamic level-by-level navigation. */
@HiltViewModel
class LiveTvViewModel
@Inject
constructor(
    repository: ContentRepository,
    preferencesManager: PreferencesManager,
) : ContentViewModel<LiveChannel>(repository, preferencesManager) {

    override fun getContentType(): ContentType = ContentType.LIVE

    override fun getPagedContent(categoryId: String): Flow<PagingData<LiveChannel>> {
        return repository.getLiveStreamsPaged(categoryId)
    }
}
