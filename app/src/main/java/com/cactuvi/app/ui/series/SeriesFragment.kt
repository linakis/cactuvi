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
import androidx.fragment.app.Fragment
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
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.data.repository.SeriesEffect
import com.cactuvi.app.data.sync.ContentDiff
import com.cactuvi.app.data.sync.ReactiveUpdateManager
import com.cactuvi.app.ui.common.CategoryTreeAdapter
import com.cactuvi.app.ui.common.GroupAdapter
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.ui.common.SeriesPagingAdapter
import com.cactuvi.app.data.models.ContentFilterSettings
import com.cactuvi.app.utils.CategoryGrouper
import com.cactuvi.app.utils.CategoryGrouper.GroupNode
import com.cactuvi.app.utils.CategoryGrouper.NavigationTree
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.IdleDetectionHelper
import com.cactuvi.app.utils.PerformanceLogger
import com.cactuvi.app.utils.PreferencesManager
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
    private lateinit var database: com.cactuvi.app.data.db.AppDatabase
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
        
        repository = ContentRepository.getInstance(requireContext())
        
        database = com.cactuvi.app.data.db.AppDatabase.getInstance(requireContext())
        
        setupToolbar()
        setupRecyclerView()
        setupReactiveUpdates()
        
        // NEW: Observe reactive state + effects
        observeSeriesState()
        observeSeriesEffects()
        
        // Trigger initial load ONCE
        triggerDataLoad()
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
    /**
     * Observe series state reactively (FRP pattern).
     * Fragment NEVER calls loadSeries() directly after initial trigger.
     * All UI updates driven by state changes from repository.
     */
    private fun observeSeriesState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.seriesState.collectLatest { state ->
                    handleSeriesState(state)
                }
            }
        }
    }
    
    /**
     * Handle series state changes reactively.
     */
    private fun handleSeriesState(state: com.cactuvi.app.data.models.DataState<Unit>) {
        when (state) {
            is com.cactuvi.app.data.models.DataState.Loading -> {
                lifecycleScope.launch {
                    val hasCache = (database.cacheMetadataDao().get("series")?.itemCount ?: 0) > 0
                    if (!hasCache) {
                        // First load - show full-screen spinner
                        showLoadingWithMessage("Loading series for the first time...")
                    } else {
                        // Has data - show refresh indicator with progress
                        progressBar.visibility = View.VISIBLE
                        state.progress?.let { percent ->
                            progressBar.isIndeterminate = false
                            progressBar.progress = percent
                        } ?: run {
                            progressBar.isIndeterminate = true
                        }
                    }
                }
            }
            is com.cactuvi.app.data.models.DataState.Success -> {
                // Data loaded - refresh UI from cache
                refreshUIFromCache()
            }
            is com.cactuvi.app.data.models.DataState.PartialSuccess -> {
                // Silent partial success - just show the data we got
                android.util.Log.w(
                    "SeriesFragment",
                    "Partial success: ${state.successCount} items loaded, ${state.failedCount} failed - ${state.error.message}"
                )
                progressBar.visibility = View.GONE
                refreshUIFromCache()
            }
            is com.cactuvi.app.data.models.DataState.Error -> {
                if (state.cachedData != null) {
                    // Has cache - show cached data, silent error (logged via effects)
                    refreshUIFromCache()
                } else {
                    // No cache - show error screen with retry
                    showErrorWithRetry("Failed to load series: ${state.error.message}")
                }
            }
        }
    }
    
    /**
     * Observe series effects (one-time actions like logs, analytics).
     * Background refresh errors with cache are silent (log only).
     */
    private fun observeSeriesEffects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.seriesEffects.collect { effect ->
                    when (effect) {
                        is SeriesEffect.LoadSuccess -> {
                            val source = if (effect.fromCache) "cache" else "API"
                            android.util.Log.d("SeriesFragment", 
                                "Series loaded successfully: ${effect.itemCount} items from $source in ${effect.durationMs}ms")
                            
                            // Performance monitoring
                            if (effect.durationMs > 5000 && !effect.fromCache) {
                                android.util.Log.w("SeriesFragment", 
                                    "SLOW LOAD: Series API fetch took ${effect.durationMs}ms (threshold: 5000ms)")
                            }
                            
                            // Analytics: Track successful loads with performance metrics
                            // Analytics.log("series_load_success", mapOf(
                            //     "item_count" to effect.itemCount,
                            //     "duration_ms" to effect.durationMs,
                            //     "from_cache" to effect.fromCache
                            // ))
                        }
                        is SeriesEffect.LoadPartialSuccess -> {
                            android.util.Log.w(
                                "SeriesFragment",
                                "Partial load: ${effect.successCount} items loaded, ${effect.failedCount} failed - ${effect.message}"
                            )
                            // Silent - no user notification
                        }
                        is SeriesEffect.LoadError -> {
                            if (effect.hasCachedData) {
                                // Background refresh failed, cache exists - SILENT
                                android.util.Log.w("SeriesFragment", 
                                    "Background refresh failed (cache exists) after ${effect.durationMs}ms: ${effect.message}")
                            } else {
                                // First load failed, no cache - error already shown in handleSeriesState
                                android.util.Log.e("SeriesFragment", 
                                    "Initial load failed after ${effect.durationMs}ms: ${effect.message}")
                                
                                // Analytics: Track failures
                                // Analytics.log("series_load_error", mapOf(
                                //     "duration_ms" to effect.durationMs,
                                //     "error_message" to effect.message,
                                //     "has_cache" to effect.hasCachedData
                                // ))
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Trigger data load from repository.
     * Safe to call multiple times - repository handles deduplication.
     */
    private fun triggerDataLoad() {
        lifecycleScope.launch {
            repository.loadSeries(forceRefresh = false)
        }
    }
    
    /**
     * Refresh UI from cached data without calling loadSeries().
     */
    private fun refreshUIFromCache() {
        progressBar.visibility = View.GONE
        errorText.visibility = View.GONE
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        
        // Load groups from cache (not API)
        lifecycleScope.launch {
            loadGroupsFromCache()
        }
    }
    
    /**
     * Show error with retry button.
     */
    private fun showErrorWithRetry(message: String) {
        progressBar.visibility = View.GONE
        errorText.visibility = View.VISIBLE
        errorText.text = "$message\n\nTap to retry"
        recyclerView.visibility = View.GONE
        
        errorText.setOnClickListener {
            triggerDataLoad()
        }
    }
    
    /**
     * Load categories/groups from cache (database only, no API call).
     */
    private suspend fun loadGroupsFromCache() {
        android.util.Log.d("IPTV_PERF", "[DEBUG SeriesFragment.loadGroupsFromCache] START")
        
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
        android.util.Log.d("IPTV_PERF", "[DEBUG loadGroupsFromCache] Categories result: success=${categoriesResult.isSuccess}")
        if (categoriesResult.isSuccess) {
            categories = categoriesResult.getOrNull() ?: emptyList()
            android.util.Log.d("IPTV_PERF", "[DEBUG loadGroupsFromCache] Categories loaded: count=${categories.size}")
            
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
        
        // Show groups
        showGroups()
        android.util.Log.d("IPTV_PERF", "[DEBUG loadGroupsFromCache] END")
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
        val intent = Intent(requireContext(), com.cactuvi.app.ui.detail.SeriesDetailActivity::class.java).apply {
            putExtra("SERIES_ID", series.seriesId)
            putExtra("TITLE", series.name)
            putExtra("COVER_URL", series.cover)
        }
        startActivity(intent)
    }
    
    @Deprecated("Replaced by reactive pattern - use triggerDataLoad() and observe seriesState")
    private fun loadData() {
        // Legacy method - now handled by reactive state management
        triggerDataLoad()
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
            // Small delay to ensure database has flushed all pending writes
            kotlinx.coroutines.delay(100)
            
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
            setTextColor(requireContext().getColor(R.color.brand_green))
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
