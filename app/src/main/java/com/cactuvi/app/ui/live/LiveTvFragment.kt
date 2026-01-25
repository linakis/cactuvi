package com.cactuvi.app.ui.live

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.cactuvi.app.R
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.ui.common.CategoryTreeAdapter
import com.cactuvi.app.ui.common.LiveChannelPagingAdapter
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.utils.IdleDetectionHelper
import com.cactuvi.app.utils.SourceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LiveTvFragment : Fragment() {
    
    private val viewModel: LiveTvViewModel by viewModels()
    
    @Inject
    lateinit var repository: ContentRepository
    
    @Inject
    lateinit var database: com.cactuvi.app.data.db.AppDatabase
    
    // UI Components
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var emptyText: TextView
    private lateinit var modernToolbar: ModernToolbar
    private lateinit var breadcrumbScroll: HorizontalScrollView
    private lateinit var breadcrumbChips: ChipGroup
    
    // Adapters
    private lateinit var categoryAdapter: CategoryTreeAdapter
    private val contentAdapter: LiveChannelPagingAdapter by lazy {
        LiveChannelPagingAdapter { channel -> playChannel(channel) }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_content_tree, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        errorText = view.findViewById(R.id.errorText)
        emptyText = view.findViewById(R.id.emptyText)
        modernToolbar = view.findViewById(R.id.modernToolbar)
        breadcrumbScroll = view.findViewById(R.id.breadcrumbScroll)
        breadcrumbChips = view.findViewById(R.id.breadcrumbChips)
        
        setupToolbar()
        setupRecyclerView()
        
        // Observe ViewModel state
        observeUiState()
    }
    
    private fun setupToolbar() {
        modernToolbar.title = "Live TV"
        modernToolbar.onBackClick = {
            val handled = viewModel.navigateBack()
            if (!handled) {
                requireActivity().finish()
            }
        }
        modernToolbar.onActionClick = {
            val intent = Intent(requireContext(), com.cactuvi.app.ui.search.SearchActivity::class.java).apply {
                putExtra(com.cactuvi.app.ui.search.SearchActivity.EXTRA_CONTENT_TYPE,
                         com.cactuvi.app.ui.search.SearchActivity.TYPE_LIVE)
            }
            startActivity(intent)
        }
        
        // Observe active source and update title
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SourceManager.getInstance(requireContext()).getActiveSourceFlow().collectLatest { source ->
                    val sourceName = source?.nickname ?: "No Source"
                    modernToolbar.title = "Live TV • $sourceName"
                }
            }
        }
    }
    
    private fun setupRecyclerView() {
        // Initialize adapters
        categoryAdapter = CategoryTreeAdapter { category ->
            viewModel.selectCategory(category.categoryId)
        }
        
        // contentAdapter is already initialized as lazy property
        
        // Start with categories (linear layout)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = categoryAdapter
        
        // Attach idle detection
        IdleDetectionHelper.attach(recyclerView)
    }
    
    /**
     * Observe ViewModel UiState - single source of truth.
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    renderUiState(state)
                }
            }
        }
    }
    
    /**
     * Render UI based on current state.
     * Pure UI rendering - no business logic.
     */
    private fun renderUiState(state: LiveTvUiState) {
        // Update loading/error visibility
        progressBar.isVisible = state.showLoading
        errorText.isVisible = state.showError
        recyclerView.isVisible = state.showContent
        
        if (state.showError) {
            errorText.text = "${state.error}\n\nTap to retry"
            errorText.setOnClickListener {
                viewModel.refresh()
            }
            return
        }
        
        // Render content based on whether category is selected
        if (state.isViewingCategory) {
            state.selectedCategoryId?.let { categoryId ->
                showChannelsView(categoryId)
            }
        } else {
            showCategoriesView(state.categories)
        }
        
        // Update breadcrumb
        updateBreadcrumb(state)
    }
    
    private fun showCategoriesView(categories: List<com.cactuvi.app.data.models.Category>) {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = categoryAdapter
        
        // Get counts from DB
        lifecycleScope.launch {
            val categoriesWithCounts = categories.map { category ->
                val count = database.liveChannelDao().getCountByCategory(category.categoryId)
                Pair(category, count)
            }
            categoryAdapter.updateCategories(categoriesWithCounts)
        }
    }
    
    private fun showChannelsView(categoryId: String) {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        recyclerView.adapter = contentAdapter
        
        // Load paged channels for this category
        lifecycleScope.launch {
            repository.getLiveStreamsPaged(categoryId = categoryId)
                .collectLatest { pagingData ->
                    contentAdapter.submitData(pagingData)
                }
        }
    }
    
    private fun updateBreadcrumb(state: LiveTvUiState) {
        if (state.isViewingCategory) {
            breadcrumbScroll.visibility = View.VISIBLE
            breadcrumbChips.removeAllViews()
            addBreadcrumbChip(state.selectedCategory?.categoryName ?: "")
        } else {
            breadcrumbScroll.visibility = View.GONE
            breadcrumbChips.removeAllViews()
        }
    }
    
    private fun addBreadcrumbChip(text: String) {
        val chip = Chip(requireContext()).apply {
            this.text = text
            isClickable = true
            chipBackgroundColor = ColorStateList.valueOf(
                requireContext().getColor(R.color.surface_elevated)
            )
            setTextColor(requireContext().getColor(R.color.brand_green))
            setOnClickListener { viewModel.navigateBack() }
        }
        breadcrumbChips.addView(chip)
    }
    
    private fun playChannel(channel: LiveChannel) {
        // Navigate to player
        val intent = Intent(requireContext(), com.cactuvi.app.ui.player.PlayerActivity::class.java).apply {
            putExtra("STREAM_ID", channel.streamId)
            putExtra("STREAM_TYPE", "live")
            putExtra("TITLE", channel.name)
            putExtra("STREAM_ICON", channel.streamIcon)
            putExtra("EPG_CHANNEL_ID", channel.epgChannelId)
            putExtra("CATEGORY_ID", channel.categoryId)
        }
        startActivity(intent)
    }
    
    // Handle back press from activity
    fun onBackPressed(): Boolean {
        return viewModel.navigateBack()
    }
}
