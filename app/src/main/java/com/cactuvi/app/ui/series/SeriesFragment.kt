package com.cactuvi.app.ui.series

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
import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.Series
import com.cactuvi.app.ui.common.CategoryTreeAdapter
import com.cactuvi.app.ui.common.GroupAdapter
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.ui.common.SeriesPagingAdapter
import com.cactuvi.app.utils.CategoryGrouper.GroupNode
import com.cactuvi.app.utils.IdleDetectionHelper
import com.cactuvi.app.utils.PerformanceLogger
import com.cactuvi.app.utils.SourceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.cactuvi.app.ui.common.ContentUiState
import com.cactuvi.app.ui.common.NavigationLevel

@AndroidEntryPoint
class SeriesFragment : Fragment() {
    
    private val viewModel: SeriesViewModel by viewModels()
    
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
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var categoryAdapter: CategoryTreeAdapter
    private val contentAdapter: SeriesPagingAdapter by lazy {
        SeriesPagingAdapter { series -> openSeriesDetail(series) }
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
        observePagedSeries()
    }
    
    private fun setupToolbar() {
        modernToolbar.title = "Series"
        modernToolbar.onBackClick = {
            val handled = viewModel.navigateBack()
            if (!handled) {
                requireActivity().finish()
            }
        }
        modernToolbar.onActionClick = {
            val intent = Intent(requireContext(), com.cactuvi.app.ui.search.SearchActivity::class.java).apply {
                putExtra(com.cactuvi.app.ui.search.SearchActivity.EXTRA_CONTENT_TYPE,
                         com.cactuvi.app.ui.search.SearchActivity.TYPE_SERIES)
            }
            startActivity(intent)
        }
        
        // Observe active source and update title
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SourceManager.getInstance(requireContext()).getActiveSourceFlow().collectLatest { source ->
                    val sourceName = source?.nickname ?: "No Source"
                    modernToolbar.title = "Series • $sourceName"
                }
            }
        }
    }
    
    private fun setupRecyclerView() {
        // Initialize adapters
        groupAdapter = GroupAdapter { group ->
            viewModel.selectGroup(group.name)
        }
        
        categoryAdapter = CategoryTreeAdapter { category ->
            viewModel.selectCategory(category.categoryId)
        }
        
        // contentAdapter is already initialized as lazy property
        
        // Start with groups (linear layout)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = groupAdapter
        
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
    
    private fun observePagedSeries() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pagedContent.collectLatest { pagingData ->
                    contentAdapter.submitData(pagingData)
                }
            }
        }
    }
    
    /**
     * Render UI based on current state.
     * Pure UI rendering - no business logic.
     */
    private fun renderUiState(state: ContentUiState) {
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
        
        // Render content based on navigation level
        when (state.currentLevel) {
            NavigationLevel.GROUPS -> {
                showGroupsView(state.navigationTree)
            }
            NavigationLevel.CATEGORIES -> {
                state.selectedGroup?.let { group ->
                    showCategoriesView(group)
                }
            }
            NavigationLevel.CONTENT -> {
                state.selectedCategoryId?.let { categoryId ->
                    showContentView(categoryId)
                }
            }
        }
        
        // Update breadcrumb
        updateBreadcrumb(state)
    }
    
    private fun showGroupsView(navigationTree: com.cactuvi.app.utils.CategoryGrouper.NavigationTree?) {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = groupAdapter
        
        val groups = navigationTree?.groups ?: emptyList()
        groupAdapter.updateGroups(groups)
    }
    
    private fun showCategoriesView(group: GroupNode) {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = categoryAdapter
        
        // Get counts from DB
        lifecycleScope.launch {
            val countsStart = PerformanceLogger.start("Get category counts")
            kotlinx.coroutines.delay(100)
            val categoriesWithCounts = group.categories.map { category ->
                val count = database.seriesDao().getCountByCategory(category.categoryId)
                Pair(category, count)
            }
            PerformanceLogger.end("Get category counts", countsStart, "categories=${categoriesWithCounts.size}")
            categoryAdapter.updateCategories(categoriesWithCounts)
        }
    }
    
    private fun showContentView(categoryId: String) {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        recyclerView.adapter = contentAdapter
        // Paged data is automatically loaded via observePagedSeries()
    }
    
    private fun updateBreadcrumb(state: ContentUiState) {
        when (state.currentLevel) {
            NavigationLevel.GROUPS -> {
                breadcrumbScroll.visibility = View.GONE
                breadcrumbChips.removeAllViews()
            }
            NavigationLevel.CATEGORIES -> {
                breadcrumbScroll.visibility = View.VISIBLE
                breadcrumbChips.removeAllViews()
                addBreadcrumbChip(state.selectedGroupName ?: "")
            }
            NavigationLevel.CONTENT -> {
                breadcrumbScroll.visibility = View.VISIBLE
                breadcrumbChips.removeAllViews()
                addBreadcrumbChip(state.selectedGroupName ?: "")
                // Find category name from navigationTree
                val category = state.selectedGroup?.categories?.find { it.categoryId == state.selectedCategoryId }
                addBreadcrumbChip(category?.categoryName ?: "")
            }
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
    
    private fun openSeriesDetail(series: Series) {
        // Navigate to series detail screen
        val intent = Intent(requireContext(), com.cactuvi.app.ui.detail.SeriesDetailActivity::class.java).apply {
            putExtra("SERIES_ID", series.seriesId)
            putExtra("TITLE", series.name)
            putExtra("POSTER_URL", series.cover)
        }
        startActivity(intent)
    }
    
    // Handle back press from activity
    fun onBackPressed(): Boolean {
        return viewModel.navigateBack()
    }
}
