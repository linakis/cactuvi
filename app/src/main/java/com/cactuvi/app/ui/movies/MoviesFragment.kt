package com.cactuvi.app.ui.movies

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
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.data.repository.MoviesEffect
import com.cactuvi.app.data.sync.ContentDiff
import com.cactuvi.app.data.sync.ReactiveUpdateManager
import com.cactuvi.app.ui.common.CategoryTreeAdapter
import com.cactuvi.app.ui.common.GroupAdapter
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.ui.common.MoviePagingAdapter
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

class MoviesFragment : Fragment() {
    
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
    private val contentAdapter: MoviePagingAdapter by lazy {
        MoviePagingAdapter { movie -> openMovieDetail(movie) }
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
        observeMoviesState()
        observeMoviesEffects()
        
        // Trigger initial load ONCE
        triggerDataLoad()
    }
    
    private fun setupToolbar() {
        modernToolbar.title = "Movies"
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
                         com.cactuvi.app.ui.search.SearchActivity.TYPE_MOVIES)
            }
            startActivity(intent)
        }
        
        // Observe active source and update title
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SourceManager.getInstance(requireContext()).getActiveSourceFlow().collectLatest { source ->
                    val sourceName = source?.nickname ?: "No Source"
                    modernToolbar.title = "Movies • $sourceName"
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
    
    /**
     * Subscribe to ReactiveUpdateManager for background sync updates.
     * Apply granular updates without full screen reload.
     */
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
     * Shows Material 3 progress bar at top of screen during background loading.
     */
    /**
     * Observe movies state reactively (FRP pattern).
     */
    private fun observeMoviesState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.moviesState.collectLatest { state ->
                    handleMoviesState(state)
                }
            }
        }
    }
    
    /**
     * Handle movies state changes reactively.
     */
    private fun handleMoviesState(state: com.cactuvi.app.data.models.DataState<Unit>) {
        when (state) {
            is com.cactuvi.app.data.models.DataState.Loading -> {
                lifecycleScope.launch {
                    val hasCache = (database.cacheMetadataDao().get("movies")?.itemCount ?: 0) > 0
                    if (!hasCache) {
                        showLoadingWithMessage("Loading movies for the first time...")
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
                    showErrorWithRetry("Failed to load movies: ${state.error.message}")
                }
            }
        }
    }
    
    /**
     * Observe movies effects (one-time actions).
     */
    private fun observeMoviesEffects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.moviesEffects.collect { effect ->
                    when (effect) {
                        is MoviesEffect.LoadSuccess -> {
                            android.util.Log.d("MoviesFragment", "Movies loaded successfully: ${effect.itemCount} items")
                        }
                        is MoviesEffect.LoadError -> {
                            if (effect.hasCachedData) {
                                android.util.Log.w("MoviesFragment", "Background refresh failed (cache exists): ${effect.message}")
                            } else {
                                android.util.Log.e("MoviesFragment", "Initial load failed: ${effect.message}")
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
            repository.loadMovies(forceRefresh = false)
        }
    }
    
    /**
     * Refresh UI from cached data.
     */
    private fun refreshUIFromCache() {
        progressBar.visibility = View.GONE
        errorText.visibility = View.GONE
        
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
     * Load groups from cache.
     */
    private suspend fun loadGroupsFromCache() {
        android.util.Log.d("IPTV_PERF", "[DEBUG MoviesFragment.loadGroupsFromCache] START")
        
        val preferencesManager = PreferencesManager.getInstance(requireContext())
        val groupingEnabled = preferencesManager.isGroupingEnabled(ContentFilterSettings.ContentType.MOVIES)
        val separator = preferencesManager.getCustomSeparator(ContentFilterSettings.ContentType.MOVIES)
        val hiddenGroups = preferencesManager.getHiddenGroups(ContentFilterSettings.ContentType.MOVIES)
        val hiddenCategories = preferencesManager.getHiddenCategories(ContentFilterSettings.ContentType.MOVIES)
        val filterMode = preferencesManager.getFilterMode(ContentFilterSettings.ContentType.MOVIES)
        
        navigationTree = repository.getCachedVodNavigationTree()
        
        val categoriesResult = repository.getMovieCategories()
        if (categoriesResult.isSuccess) {
            categories = categoriesResult.getOrNull() ?: emptyList()
            
            if (navigationTree == null) {
                navigationTree = CategoryGrouper.buildVodNavigationTree(
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
        android.util.Log.d("IPTV_PERF", "[DEBUG MoviesFragment.loadGroupsFromCache] END")
    }
    
    /**
     * Handle diff events from background sync.
     * Only process diffs for "movies" content type.
     */
    private fun handleContentDiffs(diffs: List<ContentDiff>) {
        val movieDiffs = diffs.filter { diff ->
            when (diff) {
                is ContentDiff.GroupAdded -> diff.contentType == "movies"
                is ContentDiff.GroupRemoved -> diff.contentType == "movies"
                is ContentDiff.GroupCountChanged -> diff.contentType == "movies"
                is ContentDiff.ItemsAddedToCategory -> diff.contentType == "movies"
                is ContentDiff.ItemsRemovedFromCategory -> diff.contentType == "movies"
            }
        }
        
        if (movieDiffs.isEmpty()) return
        
        // Reload navigation tree to get updated state
        lifecycleScope.launch {
            val updatedTree = repository.getCachedVodNavigationTree()
            if (updatedTree != null) {
                navigationTree = updatedTree
                applyDiffsToUI(movieDiffs)
            }
        }
    }
    
    /**
     * Apply diffs to current UI state without full reload.
     */
    private fun applyDiffsToUI(diffs: List<ContentDiff>) {
        when (currentLevel) {
            NavigationLevel.GROUPS -> {
                // Update groups adapter with new navigation tree
                navigationTree?.let { tree ->
                    groupAdapter.updateGroups(tree.groups)
                }
            }
            NavigationLevel.CATEGORIES -> {
                // Update category count if current group changed
                selectedGroup?.let { group ->
                    val updatedGroup = navigationTree?.findGroup(group.name)
                    if (updatedGroup != null && updatedGroup.count != group.count) {
                        selectedGroup = updatedGroup
                        // CategoryTreeAdapter expects List<Pair<Category, Int>> with depth
                        val categoriesWithDepth = updatedGroup.categories.map { it to 0 }
                        categoryAdapter.updateCategories(categoriesWithDepth)
                    }
                }
            }
            NavigationLevel.CONTENT -> {
                // Content level - refresh paging adapter if items added/removed in current category
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
    
    @Deprecated("Replaced by reactive pattern - use triggerDataLoad() and observe moviesState")
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
        currentLevel = NavigationLevel.CATEGORIES
        selectedGroup = group
        selectedCategory = null
        
        // Update UI
        updateBreadcrumb()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = categoryAdapter
        
        // Update adapter data - get counts from DB instead of loading all movies
        lifecycleScope.launch {
            val countsStart = PerformanceLogger.start("Get category counts")
            val categoriesWithCounts = group.categories.map { category ->
                val count = database.movieDao().getCountByCategory(category.categoryId)
                Pair(category, count)
            }
            PerformanceLogger.end("Get category counts", countsStart, "categories=${categoriesWithCounts.size}")
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
        
        // Load paged movies for this category
        lifecycleScope.launch {
            repository.getMoviesPaged(categoryId = category.categoryId)
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
    
    private fun playMovie(movie: Movie) {
        openMovieDetail(movie)
    }
    
    private fun openMovieDetail(movie: Movie) {
        // Navigate to movie detail screen
        val intent = Intent(requireContext(), com.cactuvi.app.ui.detail.MovieDetailActivity::class.java).apply {
            putExtra("VOD_ID", movie.streamId)
            putExtra("STREAM_ID", movie.streamId)
            putExtra("TITLE", movie.name)
            putExtra("POSTER_URL", movie.streamIcon)
            putExtra("CONTAINER_EXTENSION", movie.containerExtension)
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
