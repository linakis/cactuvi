package com.cactuvi.app.ui.live

import android.content.Intent
import androidx.fragment.app.viewModels
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.ui.common.ContentNavigationFragment
import com.cactuvi.app.ui.common.ContentViewModel
import com.cactuvi.app.ui.common.LiveChannelPagingAdapter
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.IdleDetectionHelper
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.StreamUrlBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@AndroidEntryPoint
class LiveTvFragment : ContentNavigationFragment<LiveChannel>() {

    private val viewModel: LiveTvViewModel by viewModels()

    @Inject lateinit var database: com.cactuvi.app.data.db.AppDatabase
    @Inject lateinit var credentialsManager: CredentialsManager
    @Inject lateinit var _sourceManager: SourceManager
    @Inject lateinit var _idleDetectionHelper: IdleDetectionHelper
    @Inject lateinit var _preferencesManager: com.cactuvi.app.utils.PreferencesManager

    override val sourceManager: SourceManager
        get() = _sourceManager

    override val idleDetectionHelper: IdleDetectionHelper
        get() = _idleDetectionHelper

    override val preferencesManager: com.cactuvi.app.utils.PreferencesManager
        get() = _preferencesManager

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
        // Build stream URL
        val server = credentialsManager.getServer()
        val username = credentialsManager.getUsername()
        val password = credentialsManager.getPassword()

        // Live streams typically use .ts extension
        val streamUrl =
            StreamUrlBuilder.buildLiveUrl(
                server = server,
                username = username,
                password = password,
                streamId = item.streamId,
                extension = "ts"
            )

        // Navigate to player
        val intent =
            Intent(
                    requireContext(),
                    com.cactuvi.app.ui.player.PlayerActivity::class.java,
                )
                .apply {
                    putExtra("STREAM_URL", streamUrl)
                    putExtra("STREAM_ID", item.streamId)
                    putExtra("STREAM_TYPE", "live")
                    putExtra("TITLE", item.name)
                    putExtra("STREAM_ICON", item.streamIcon)
                    putExtra("EPG_CHANNEL_ID", item.epgChannelId)
                    putExtra("CATEGORY_ID", item.categoryId)
                    putExtra("CONTENT_ID", item.streamId.toString())
                    putExtra("CONTENT_TYPE", "live")
                }
        startActivity(intent)
    }

    // Override to use LinearLayoutManager for list display (not grid)
    override fun getContentLayoutManager(): RecyclerView.LayoutManager {
        return LinearLayoutManager(requireContext())
    }
}
