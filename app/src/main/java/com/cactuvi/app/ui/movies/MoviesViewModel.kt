package com.cactuvi.app.ui.movies

import androidx.paging.PagingData
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.domain.usecase.ObserveMoviesUseCase
import com.cactuvi.app.domain.usecase.RefreshMoviesUseCase
import com.cactuvi.app.ui.common.ContentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel for Movies screen. Handles movies data and navigation state using MVVM + UDF pattern.
 *
 * Extends ContentViewModel to reuse shared navigation and state management logic.
 */
@HiltViewModel
class MoviesViewModel
@Inject
constructor(
    private val observeMoviesUseCase: ObserveMoviesUseCase,
    private val refreshMoviesUseCase: RefreshMoviesUseCase,
    private val contentRepository: ContentRepository,
) : ContentViewModel<Movie>() {

    override fun getPagedContent(categoryId: String): Flow<PagingData<Movie>> {
        return contentRepository.getMoviesPaged(categoryId)
    }

    override fun observeContent(): Flow<Resource<NavigationTree>> {
        return observeMoviesUseCase()
    }

    override suspend fun refreshContent() {
        refreshMoviesUseCase()
    }
}
