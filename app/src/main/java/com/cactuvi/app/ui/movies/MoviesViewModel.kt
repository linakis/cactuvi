package com.cactuvi.app.ui.movies

import android.content.Context
import androidx.paging.PagingData
import com.cactuvi.app.data.models.ContentType
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.repository.ContentRepositoryImpl
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.ui.common.ContentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** ViewModel for Movies screen with dynamic level-by-level navigation. */
@HiltViewModel
class MoviesViewModel
@Inject
constructor(
    repository: ContentRepository,
    @ApplicationContext context: Context,
) : ContentViewModel<Movie>(repository as ContentRepositoryImpl, context) {

    override fun getContentType(): ContentType = ContentType.MOVIES

    override fun getPagedContent(categoryId: String): Flow<PagingData<Movie>> {
        return (repository as ContentRepositoryImpl).getMoviesPaged(categoryId)
    }
}
