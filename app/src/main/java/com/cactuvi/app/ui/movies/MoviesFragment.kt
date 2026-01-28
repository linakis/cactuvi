package com.cactuvi.app.ui.movies

import android.content.Intent
import androidx.fragment.app.viewModels
import androidx.paging.PagingDataAdapter
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.ui.common.ContentNavigationFragment
import com.cactuvi.app.ui.common.ContentViewModel
import com.cactuvi.app.ui.common.MoviePagingAdapter
import com.cactuvi.app.utils.IdleDetectionHelper
import com.cactuvi.app.utils.SourceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@AndroidEntryPoint
class MoviesFragment : ContentNavigationFragment<Movie>() {

    private val viewModel: MoviesViewModel by viewModels()

    @Inject lateinit var database: com.cactuvi.app.data.db.AppDatabase
    @Inject lateinit var _sourceManager: SourceManager
    @Inject lateinit var _idleDetectionHelper: IdleDetectionHelper
    @Inject lateinit var _preferencesManager: com.cactuvi.app.utils.PreferencesManager

    override val sourceManager: SourceManager
        get() = _sourceManager

    override val idleDetectionHelper: IdleDetectionHelper
        get() = _idleDetectionHelper

    override val preferencesManager: com.cactuvi.app.utils.PreferencesManager
        get() = _preferencesManager

    override val contentAdapter: PagingDataAdapter<Movie, *> by lazy {
        MoviePagingAdapter { movie -> onContentItemClick(movie) }
    }

    // ========== ABSTRACT METHOD IMPLEMENTATIONS ==========

    override fun getViewModel(): ContentViewModel<Movie> = viewModel

    override fun getPagedContentFlow(): Flow<androidx.paging.PagingData<Movie>> =
        viewModel.pagedContent

    override fun getContentTitle(): String = "Movies"

    override fun getSearchType(): String = com.cactuvi.app.ui.search.SearchActivity.TYPE_MOVIES

    override suspend fun getCategoryItemCount(categoryId: String): Int =
        database.movieDao().getCountByCategory(categoryId)

    override fun onContentItemClick(item: Movie) {
        // Navigate to movie detail screen
        val intent =
            Intent(
                    requireContext(),
                    com.cactuvi.app.ui.detail.MovieDetailActivity::class.java,
                )
                .apply {
                    putExtra("VOD_ID", item.streamId)
                    putExtra("STREAM_ID", item.streamId)
                    putExtra("TITLE", item.name)
                    putExtra("POSTER_URL", item.streamIcon)
                    putExtra("CONTAINER_EXTENSION", item.containerExtension)
                }
        startActivity(intent)
    }
}
