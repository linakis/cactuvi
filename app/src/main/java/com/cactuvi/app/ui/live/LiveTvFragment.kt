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
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.cactuvi.app.R
import com.cactuvi.app.data.models.Category
import com.cactuvi.app.data.models.LiveChannel
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.data.repository.LiveEffect
import com.cactuvi.app.data.sync.ContentDiff
import com.cactuvi.app.data.sync.ReactiveUpdateManager
import com.cactuvi.app.ui.common.CategoryTreeAdapter
import com.cactuvi.app.ui.common.GroupAdapter
import com.cactuvi.app.ui.common.LiveChannelPagingAdapter
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.ui.player.PlayerActivity
import com.cactuvi.app.data.models.ContentFilterSettings
import com.cactuvi.app.utils.CategoryGrouper
import com.cactuvi.app.utils.CategoryGrouper.GroupNode
import com.cactuvi.app.utils.CategoryGrouper.NavigationTree
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.IdleDetectionHelper
import com.cactuvi.app.utils.PerformanceLogger
import com.cactuvi.app.utils.PreferencesManager
import com.cactuvi.app.utils.StreamUrlBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LiveTvFragment : Fragment() {
    
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
    private val contentAdapter: LiveChannelPagingAdapter by lazy {
        LiveChannelPagingAdapter { channel -> playChannel(channel) }
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
        
        repository = ContentRepository(
            SourceManager.getInstance(requireContext()),
            requireContext()
        )
        
        database = com.cactuvi.app.data.db.AppDatabase.getInstance(requireContext())
        
        setupToolbar()
        setupRecyclerView()
        setupReactiveUpdates()
        
        // NEW: Observe reactive state + effects
        observeLiveState()
        observeLiveEffects()
        
        // Trigger initial load ONCE
        triggerDataLoad()
    }
    
    private fun setupToolbar() {
        modernToolbar.title = "Live TV"
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
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Initialize adapters
        groupAdapter = GroupAdapter { group ->
            onGroupSelected(group)
        }
        
        categoryAdapter = CategoryTreeAdapter { category ->
            onCategorySelected(category)
        }
        
        // contentAdapter is already initialized as lazy property
        
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
     * Observe live state reactively (FRP pattern).
     */
    private fun observeLiveState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.liveState.collectLatest { state ->
                    handleLiveState(state)
                }
            }
        }
    }
    
    /**
     * Handle live state changes reactively.
     */
    private fun handleLiveState(state: com.cactuvi.app.data.models.DataState<Unit>) {
        when (state) {
            is com.cactuvi.app.data.models.DataState.Loading -> {
                lifecycleScope.launch {
                    val hasCache = (database.cacheMetadataDao().get("live")?.itemCount ?: 0) > 0
                    if (!hasCache) {
                        showLoadingWithMessage("Loading live channels for the first time...")
                    } else {
                        progressBar.visibility = View.VISIBLE
                    }
                }
            }
            is com.cactuvi.app.data.models.DataState.Success -> {
                refreshUIFromCache()
            }
            is com.cactuvi.app.data.models.DataState.Error -> {
                if (state.cachedData != null) {
                    refreshUIFromCache()
                } else {
                    showErrorWithRetry("Failed to load live channels: ${state.error.message}")
                }
            }
        }
    }
    
    /**
     * Observe live effects (one-time actions).
     */
    private fun observeLiveEffects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.liveEffects.collect { effect ->
                    when (effect) {
                        is LiveEffect.LoadSuccess -> {
                            android.util.Log.d("LiveTvFragment", "Live channels loaded successfully: ${effect.itemCount} items")
                        }
                        is LiveEffect.LoadError -> {
                            if (effect.hasCachedData) {
                                android.util.Log.w("LiveTvFragment", "Background refresh failed (cache exists): ${effect.message}")
                            } else {
                                android.util.Log.e("LiveTvFragment", "Initial load failed: ${effect.message}")
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Trigger data load from repository.
     */
    private fun triggerDataLoad() {
        lifecycleScope.launch {
            repository.loadLive(forceRefresh = false)
        }
    }
    
    /**
     * Refresh UI from cached data.
     */
    private fun refreshUIFromCache() {
        progressBar.visibility = View.GONE
        errorText.visibility = View.GONE
        
        lifecycleScope.launch {
            loadCategoriesFromCache()
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
     * Load categories from cache.
     */
    private suspend fun loadCategoriesFromCache() {
        android.util.Log.d("IPTV_PERF", "[DEBUG LiveTvFragment.loadCategoriesFromCache] START")
        
        val preferencesManager = PreferencesManager.getInstance(requireContext())
        val groupingEnabled = preferencesManager.isGroupingEnabled(ContentFilterSettings.ContentType.LIVE_TV)
        val separator = preferencesManager.getCustomSeparator(ContentFilterSettings.ContentType.LIVE_TV)
        val hiddenGroups = preferencesManager.getHiddenGroups(ContentFilterSettings.ContentType.LIVE_TV)
        val hiddenCategories = preferencesManager.getHiddenCategories(ContentFilterSettings.ContentType.LIVE_TV)
        val filterMode = preferencesManager.getFilterMode(ContentFilterSettings.ContentType.LIVE_TV)
        
        navigationTree = repository.getCachedLiveNavigationTree()
        
        val categoriesResult = repository.getLiveCategories()
        if (categoriesResult.isSuccess) {
            categories = categoriesResult.getOrNull() ?: emptyList()
            
            if (navigationTree == null) {
                navigationTree = CategoryGrouper.buildLiveNavigationTree(
                    categories,
                    groupingEnabled,
                    separator,
                    hiddenGroups,
                    hiddenCategories,
                    filterMode
                )
            }
        }
        
        showGroups()
        android.util.Log.d("IPTV_PERF", "[DEBUG LiveTvFragment.loadCategoriesFromCache] END")
    }
    
    private fun handleContentDiffs(diffs: List<ContentDiff>) {
        val liveDiffs = diffs.filter { diff ->
            when (diff) {
                is ContentDiff.GroupAdded -> diff.contentType == "live"
                is ContentDiff.GroupRemoved -> diff.contentType == "live"
                is ContentDiff.GroupCountChanged -> diff.contentType == "live"
                is ContentDiff.ItemsAddedToCategory -> diff.contentType == "live"
                is ContentDiff.ItemsRemovedFromCategory -> diff.contentType == "live"
            }
        }
        
        if (liveDiffs.isEmpty()) return
        
        lifecycleScope.launch {
            val updatedTree = repository.getCachedLiveNavigationTree()
            if (updatedTree != null) {
                navigationTree = updatedTree
                applyDiffsToUI(liveDiffs)
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
    
    @Deprecated("Replaced by reactive pattern - use triggerDataLoad() and observe liveState")
    private fun loadData() {
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
        recyclerView.adapter = groupAdapter
        
        // Update adapter data
        val groups = navigationTree?.groups ?: emptyList()
        groupAdapter.updateGroups(groups)
        
        // Update visibility
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    private fun showCategories(group: GroupNode) {
        currentLevel = NavigationLevel.CATEGORIES
        selectedGroup = group
        selectedCategory = null
        
        // Update UI
        updateBreadcrumb()
        recyclerView.adapter = categoryAdapter
        
        // Update adapter data - get counts from DB instead of loading all channels
        lifecycleScope.launch {
            val categoriesWithCounts = group.categories.map { category ->
                val count = database.liveChannelDao().getCountByCategory(category.categoryId)
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
        recyclerView.adapter = contentAdapter
        
        // Load paged channels for this category
        lifecycleScope.launch {
            repository.getLiveStreamsPaged(categoryId = category.categoryId)
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
    
    private fun playChannel(channel: LiveChannel) {
        val credentials = CredentialsManager.getInstance(requireContext()) ?: return
        
        val streamUrl = StreamUrlBuilder.buildLiveUrl(
            server = credentials.getServer(),
            username = credentials.getUsername(),
            password = credentials.getPassword(),
            streamId = channel.streamId
        )
        
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("TITLE", channel.name)
        }
        startActivity(intent)
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
