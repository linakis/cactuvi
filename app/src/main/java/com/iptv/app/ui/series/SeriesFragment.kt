package com.iptv.app.ui.series

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.iptv.app.R
import com.iptv.app.data.models.Category
import com.iptv.app.data.models.Series
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.data.sync.ContentDiff
import com.iptv.app.data.sync.ReactiveUpdateManager
import com.iptv.app.ui.common.CategoryTreeAdapter
import com.iptv.app.ui.common.GroupAdapter
import com.iptv.app.ui.common.ModernToolbar
import com.iptv.app.ui.common.SeriesPagingAdapter
import com.iptv.app.data.models.ContentFilterSettings
import com.iptv.app.utils.CategoryGrouper
import com.iptv.app.utils.CategoryGrouper.GroupNode
import com.iptv.app.utils.CategoryGrouper.NavigationTree
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.SourceManager
import com.iptv.app.utils.IdleDetectionHelper
import com.iptv.app.utils.PerformanceLogger
import com.iptv.app.utils.PreferencesManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SeriesFragment : Fragment() {
    
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
    
    // Data
    private lateinit var repository: ContentRepository
    private lateinit var database: com.iptv.app.data.db.AppDatabase
    private var navigationTree: NavigationTree? = null
    private var categories: List<Category> = emptyList()
    
    // Navigation State
    private var currentLevel: NavigationLevel = NavigationLevel.GROUPS
    private var selectedGroup: GroupNode? = null
    private var selectedCategory: Category? = null
    
    enum class NavigationLevel {
        GROUPS,      // Show all groups
        CATEGORIES,  // Show categories in group
        CONTENT      // Show filtered content
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
        
        repository = ContentRepository(
            SourceManager.getInstance(requireContext()),
            requireContext()
        )
        
        database = com.iptv.app.data.db.AppDatabase.getInstance(requireContext())
        
        setupToolbar()
        setupRecyclerView()
        setupReactiveUpdates()
        setupLoadingStateObserver()
        loadData()
    }
    
    private fun setupToolbar() {
        modernToolbar.title = "Series"
        modernToolbar.onBackClick = {
            val handled = handleBackPress()
            if (!handled) {
                // At top level, finish activity
                requireActivity().finish()
            }
        }
        modernToolbar.onActionClick = {
            val intent = Intent(requireContext(), com.iptv.app.ui.search.SearchActivity::class.java).apply {
                putExtra(com.iptv.app.ui.search.SearchActivity.EXTRA_CONTENT_TYPE, 
                         com.iptv.app.ui.search.SearchActivity.TYPE_SERIES)
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
            onGroupSelected(group)
        }
        
        categoryAdapter = CategoryTreeAdapter { category ->
            onCategorySelected(category)
        }
        
        // contentAdapter is already initialized as lazy property
        
        // Start with groups (linear layout)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = groupAdapter
        
        // Attach idle detection
        IdleDetectionHelper.attach(recyclerView)
    }
    
    private fun setupReactiveUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ReactiveUpdateManager.getInstance().contentDiffs.collect { diffs ->
                    handleContentDiffs(diffs)
                }
            }
        }
    }
    
    /**
     * Observe loading state from repository and show/hide progress indicator.
     */
    private fun setupLoadingStateObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.seriesLoading.collectLatest { isLoading ->
                    if (isLoading) {
                        showLoadingState()
                    } else {
                        hideLoadingState()
                    }
                }
            }
        }
    }
    
    /**
     * Show loading indicator when background data load is in progress.
     */
    private fun showLoadingState() {
        progressBar.visibility = View.VISIBLE
        PerformanceLogger.log("SeriesFragment: Showing loading state")
    }
    
    /**
     * Hide loading indicator when background data load completes.
     */
    private fun hideLoadingState() {
        progressBar.visibility = View.GONE
        PerformanceLogger.log("SeriesFragment: Hiding loading state")
    }
    
    private fun handleContentDiffs(diffs: List<ContentDiff>) {
        val seriesDiffs = diffs.filter { diff ->
            when (diff) {
                is ContentDiff.GroupAdded -> diff.contentType == "series"
                is ContentDiff.GroupRemoved -> diff.contentType == "series"
                is ContentDiff.GroupCountChanged -> diff.contentType == "series"
                is ContentDiff.ItemsAddedToCategory -> diff.contentType == "series"
                is ContentDiff.ItemsRemovedFromCategory -> diff.contentType == "series"
            }
        }
        
        if (seriesDiffs.isEmpty()) return
        
        lifecycleScope.launch {
            val updatedTree = repository.getCachedSeriesNavigationTree()
            if (updatedTree != null) {
                navigationTree = updatedTree
                applyDiffsToUI(seriesDiffs)
            }
        }
    }
    
    private fun applyDiffsToUI(diffs: List<ContentDiff>) {
        when (currentLevel) {
            NavigationLevel.GROUPS -> {
                navigationTree?.let { tree ->
                    groupAdapter.updateGroups(tree.groups)
                }
            }
            NavigationLevel.CATEGORIES -> {
                selectedGroup?.let { group ->
                    val updatedGroup = navigationTree?.findGroup(group.name)
                    if (updatedGroup != null && updatedGroup.count != group.count) {
                        selectedGroup = updatedGroup
                        val categoriesWithDepth = updatedGroup.categories.map { it to 0 }
                        categoryAdapter.updateCategories(categoriesWithDepth)
                    }
                }
            }
            NavigationLevel.CONTENT -> {
                val hasRelevantChanges = diffs.any { diff ->
                    when (diff) {
                        is ContentDiff.ItemsAddedToCategory -> 
                            diff.categoryId == selectedCategory?.categoryId
                        is ContentDiff.ItemsRemovedFromCategory -> 
                            diff.categoryId == selectedCategory?.categoryId
                        else -> false
                    }
                }
                
                if (hasRelevantChanges) {
                    contentAdapter.refresh()
                }
            }
        }
    }
    
    private fun openSeriesDetail(series: Series) {
        // Navigate to series detail screen
        val intent = Intent(requireContext(), com.iptv.app.ui.detail.SeriesDetailActivity::class.java).apply {
            putExtra("SERIES_ID", series.seriesId)
            putExtra("TITLE", series.name)
            putExtra("COVER_URL", series.cover)
        }
        startActivity(intent)
    }
    
    private fun loadData() {
        android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] START")
        showLoading(true)
        
        lifecycleScope.launch {
            // Check if already loading
            if (repository.seriesLoading.value) {
                android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Already loading - waiting")
                showLoadingWithMessage("Loading series data...")
                
                // Wait for current load to complete
                repository.seriesLoading.first { !it }
                android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Load completed - checking cache")
                
                // Check cache again after load completes
                val newMetadata = database.cacheMetadataDao().get("series")
                val nowHasCache = (newMetadata?.itemCount ?: 0) > 0
                
                if (!nowHasCache) {
                    // Still no cache after waiting - something went wrong
                    android.util.Log.e("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Still no cache after waiting")
                    showError("Failed to load series data")
                    return@launch
                }
                
                // Fall through to load UI with cached data
                android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Cache now exists - loading UI")
            }
            
            // Check if cache exists
            val metadata = database.cacheMetadataDao().get("series")
            val hasCache = (metadata?.itemCount ?: 0) > 0
            android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Cache check: hasCache=$hasCache, itemCount=${metadata?.itemCount ?: 0}")
            
            if (!hasCache) {
                // No cache and not loading - trigger new load
                android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] No cache - triggering background load")
                showLoadingWithMessage("Loading series for the first time...")
                
                // Trigger background load
                lifecycleScope.launch {
                    val result = repository.getSeries(forceRefresh = true)
                    if (result.isSuccess) {
                        android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Background load SUCCESS - reloading UI")
                        // Reload data now that cache exists
                        loadData()
                    } else {
                        android.util.Log.e("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Background load FAILED: ${result.exceptionOrNull()?.message}")
                        showError("Failed to load series: ${result.exceptionOrNull()?.message}")
                    }
                }
                return@launch
            }
            
            android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Cache exists - loading categories")
            val preferencesManager = PreferencesManager.getInstance(requireContext())
            
            // Get filter settings
            val groupingEnabled = preferencesManager.isGroupingEnabled(ContentFilterSettings.ContentType.SERIES)
            val separator = preferencesManager.getCustomSeparator(ContentFilterSettings.ContentType.SERIES)
            val hiddenGroups = preferencesManager.getHiddenGroups(ContentFilterSettings.ContentType.SERIES)
            val hiddenCategories = preferencesManager.getHiddenCategories(ContentFilterSettings.ContentType.SERIES)
            val filterMode = preferencesManager.getFilterMode(ContentFilterSettings.ContentType.SERIES)
            
            // Try to load cached navigation tree first
            navigationTree = repository.getCachedSeriesNavigationTree()
            
            // Load categories
            val categoriesResult = repository.getSeriesCategories()
            android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Categories result: success=${categoriesResult.isSuccess}")
            if (categoriesResult.isSuccess) {
                categories = categoriesResult.getOrNull() ?: emptyList()
                android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Categories loaded: count=${categories.size}")
                if (categories.size > 0) {
                    android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadData] Sample categories: ${categories.take(3).map { "${it.categoryId}:${it.categoryName}" }}")
                }
                
                // Build navigation tree with hierarchical filter settings if cache miss
                if (navigationTree == null) {
                    navigationTree = CategoryGrouper.buildSeriesNavigationTree(
                        categories,
                        groupingEnabled,
                        separator,
                        hiddenGroups,
                        hiddenCategories,
                        filterMode
                    )
                }
            }
            
            // Show groups (no need to load all series)
            showGroups()
            showLoading(false)
        }
    }
    
    private fun showLoadingWithMessage(message: String) {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.VISIBLE
        emptyText.text = message
        recyclerView.visibility = View.GONE
        errorText.visibility = View.GONE
    }
    
    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        errorText.visibility = View.VISIBLE
        errorText.text = message
        recyclerView.visibility = View.GONE
        emptyText.visibility = View.GONE
    }
    
    private fun showGroups() {
        currentLevel = NavigationLevel.GROUPS
        selectedGroup = null
        selectedCategory = null
        
        // Update UI
        updateBreadcrumb()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = groupAdapter
        
        // Update adapter data
        val groups = navigationTree?.groups ?: emptyList()
        groupAdapter.updateGroups(groups)
        
        // Update visibility
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    private fun showCategories(group: GroupNode) {
        android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.showCategories] START - group=${group.name}, categories=${group.categories.size}")
        currentLevel = NavigationLevel.CATEGORIES
        selectedGroup = group
        selectedCategory = null
        
        // Update UI
        updateBreadcrumb()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = categoryAdapter
        
        // Update adapter data - get counts from DB instead of loading all series
        lifecycleScope.launch {
            // DEBUG: Check total series count first
            val totalSeriesCount = database.seriesDao().getCount()
            android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.showCategories] Total series in DB: $totalSeriesCount")
            
            val categoriesWithCounts = group.categories.map { category ->
                val count = database.seriesDao().getCountByCategory(category.categoryId)
                android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.showCategories] Category: ${category.categoryName} (id=${category.categoryId}) -> count=$count")
                Pair(category, count)
            }
            categoryAdapter.updateCategories(categoriesWithCounts)
        }
        
        // Update visibility
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    private fun showContent(category: Category) {
        currentLevel = NavigationLevel.CONTENT
        selectedCategory = category
        
        // Update UI
        updateBreadcrumb()
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        recyclerView.adapter = contentAdapter
        
        // Load paged series for this category
        lifecycleScope.launch {
            repository.getSeriesPaged(categoryId = category.categoryId)
                .collectLatest { pagingData ->
                    contentAdapter.submitData(pagingData)
                }
        }
        
        // Show content area
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    private fun updateBreadcrumb() {
        when (currentLevel) {
            NavigationLevel.GROUPS -> {
                // Title updated by active source flow observer
                breadcrumbScroll.visibility = View.GONE
                breadcrumbChips.removeAllViews()
            }
            NavigationLevel.CATEGORIES -> {
                // Title updated by active source flow observer
                breadcrumbScroll.visibility = View.VISIBLE
                breadcrumbChips.removeAllViews()
                addBreadcrumbChip(selectedGroup?.name ?: "")
            }
            NavigationLevel.CONTENT -> {
                // Title updated by active source flow observer
                breadcrumbScroll.visibility = View.VISIBLE
                breadcrumbChips.removeAllViews()
                addBreadcrumbChip(selectedGroup?.name ?: "")
                addBreadcrumbChip(selectedCategory?.categoryName ?: "")
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
            setTextColor(requireContext().getColor(R.color.brand_orange))
            setOnClickListener { handleBackPress() }
        }
        breadcrumbChips.addView(chip)
    }
    
    private fun handleBackPress(): Boolean {
        // Navigate back in tree
        return when (currentLevel) {
            NavigationLevel.GROUPS -> false // Let activity handle back
            NavigationLevel.CATEGORIES -> {
                showGroups()
                true
            }
            NavigationLevel.CONTENT -> {
                selectedGroup?.let { showCategories(it) }
                true
            }
        }
    }
    
    private fun onGroupSelected(group: GroupNode) {
        showCategories(group)
    }
    
    private fun onCategorySelected(category: Category) {
        showContent(category)
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        errorText.visibility = View.GONE
    }
    
    private fun showError() {
        progressBar.visibility = View.GONE
        recyclerView.visibility = View.GONE
        errorText.visibility = View.VISIBLE
    }
    
    // Handle back press from activity
    fun onBackPressed(): Boolean {
        return handleBackPress()
    }
}
