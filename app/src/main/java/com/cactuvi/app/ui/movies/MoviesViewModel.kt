package com.cactuvi.app.ui.movies

import androidx.paging.PagingData
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.ui.common.ContentViewModel
import com.cactuvi.app.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** ViewModel for Movies screen with dynamic level-by-level navigation. */
@HiltViewModel
class MoviesViewModel
@Inject
constructor(
    repository: ContentRepository,
    preferencesManager: PreferencesManager,
) : ContentViewModel<Movie>(repository, preferencesManager) {

    override fun getContentType(): ContentType = ContentType.MOVIES

    override fun getPagedContent(categoryId: String): Flow<PagingData<Movie>> {
        return repository.getMoviesPaged(categoryId)
    }
}
