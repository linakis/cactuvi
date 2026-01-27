package com.cactuvi.app.ui.series

import android.content.Intent
import androidx.fragment.app.viewModels
import androidx.paging.PagingDataAdapter
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.ui.common.ContentNavigationFragment
import com.cactuvi.app.ui.common.ContentViewModel
import com.cactuvi.app.ui.common.SeriesPagingAdapter
import com.cactuvi.app.utils.IdleDetectionHelper
import com.cactuvi.app.utils.SourceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@AndroidEntryPoint
class SeriesFragment : ContentNavigationFragment<Series>() {

    private val viewModel: SeriesViewModel by viewModels()

    @Inject lateinit var database: com.cactuvi.app.data.db.AppDatabase
    @Inject lateinit var _sourceManager: SourceManager
    @Inject lateinit var _idleDetectionHelper: IdleDetectionHelper

    override val sourceManager: SourceManager
        get() = _sourceManager

    override val idleDetectionHelper: IdleDetectionHelper
        get() = _idleDetectionHelper

    override val contentAdapter: PagingDataAdapter<Series, *> by lazy {
        SeriesPagingAdapter { series -> onContentItemClick(series) }
    }

    // ========== ABSTRACT METHOD IMPLEMENTATIONS ==========

    override fun getViewModel(): ContentViewModel<Series> = viewModel

    override fun getPagedContentFlow(): Flow<androidx.paging.PagingData<Series>> =
        viewModel.pagedContent

    override fun getContentTitle(): String = "Series"

    override fun getSearchType(): String = com.cactuvi.app.ui.search.SearchActivity.TYPE_SERIES

    override suspend fun getCategoryItemCount(categoryId: String): Int =
        database.seriesDao().getCountByCategory(categoryId)

    override fun onContentItemClick(item: Series) {
        // Navigate to series detail screen
        val intent =
            Intent(
                    requireContext(),
                    com.cactuvi.app.ui.detail.SeriesDetailActivity::class.java,
                )
                .apply {
                    putExtra("SERIES_ID", item.seriesId)
                    putExtra("TITLE", item.name)
                    putExtra("POSTER_URL", item.cover)
                }
        startActivity(intent)
    }
}
