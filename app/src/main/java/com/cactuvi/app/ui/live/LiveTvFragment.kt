package com.cactuvi.app.ui.live

import android.content.Intent
import androidx.fragment.app.viewModels
import androidx.paging.PagingDataAdapter
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.ui.common.ContentNavigationFragment
import com.cactuvi.app.ui.common.ContentViewModel
import com.cactuvi.app.ui.common.LiveChannelPagingAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@AndroidEntryPoint
class LiveTvFragment : ContentNavigationFragment<LiveChannel>() {

    private val viewModel: LiveTvViewModel by viewModels()

    @Inject lateinit var database: com.cactuvi.app.data.db.AppDatabase

    override val contentAdapter: PagingDataAdapter<LiveChannel, *> by lazy {
        LiveChannelPagingAdapter { channel -> onContentItemClick(channel) }
    }

    // ========== ABSTRACT METHOD IMPLEMENTATIONS ==========

    override fun getViewModel(): ContentViewModel<LiveChannel> = viewModel

    override fun getPagedContentFlow(): Flow<androidx.paging.PagingData<LiveChannel>> =
        viewModel.pagedContent

    override fun getContentTitle(): String = "Live TV"

    override fun getSearchType(): String = com.cactuvi.app.ui.search.SearchActivity.TYPE_LIVE

    override suspend fun getCategoryItemCount(categoryId: String): Int =
        database.liveChannelDao().getCountByCategory(categoryId)

    override fun onContentItemClick(item: LiveChannel) {
        // Navigate to player
        val intent =
            Intent(
                    requireContext(),
                    com.cactuvi.app.ui.player.PlayerActivity::class.java,
                )
                .apply {
                    putExtra("STREAM_ID", item.streamId)
                    putExtra("STREAM_TYPE", "live")
                    putExtra("TITLE", item.name)
                    putExtra("STREAM_ICON", item.streamIcon)
                    putExtra("EPG_CHANNEL_ID", item.epgChannelId)
                    putExtra("CATEGORY_ID", item.categoryId)
                }
        startActivity(intent)
    }
}
