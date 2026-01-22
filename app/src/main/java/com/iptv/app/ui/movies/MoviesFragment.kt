package com.iptv.app.ui.movies

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
import com.iptv.app.data.models.Movie
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.data.sync.ContentDiff
import com.iptv.app.data.sync.ReactiveUpdateManager
import com.iptv.app.ui.common.CategoryTreeAdapter
import com.iptv.app.ui.common.GroupAdapter
import com.iptv.app.ui.common.ModernToolbar
import com.iptv.app.ui.common.MoviePagingAdapter
import com.iptv.app.ui.player.PlayerActivity
import com.iptv.app.data.models.ContentFilterSettings
import com.iptv.app.utils.CategoryGrouper
import com.iptv.app.utils.CategoryGrouper.GroupNode
import com.iptv.app.utils.CategoryGrouper.NavigationTree
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.SourceManager
import com.iptv.app.utils.IdleDetectionHelper
import com.iptv.app.utils.PerformanceLogger
import com.iptv.app.utils.PreferencesManager
import com.iptv.app.utils.StreamUrlBuilder
import kotlinx.coroutines.flow.collectLatest
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
        loadData()
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
            val intent = Intent(requireContext(), com.iptv.app.ui.search.SearchActivity::class.java).apply {
                putExtra(com.iptv.app.ui.search.SearchActivity.EXTRA_CONTENT_TYPE, 
                         com.iptv.app.ui.search.SearchActivity.TYPE_MOVIES)
            }
            startActivity(intent)
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
    
    private fun loadData() {
        showLoading(true)
        
        lifecycleScope.launch {
            // Start total timer
            val totalStartTime = PerformanceLogger.start("MoviesFragment.loadData")
            
            val preferencesManager = PreferencesManager.getInstance(requireContext())
            
            // Get filter settings
            PerformanceLogger.logPhase("MoviesFragment.loadData", "Loading filter settings")
            val filterStartTime = PerformanceLogger.start("Filter settings load")
            val groupingEnabled = preferencesManager.isGroupingEnabled(ContentFilterSettings.ContentType.MOVIES)
            val separator = preferencesManager.getCustomSeparator(ContentFilterSettings.ContentType.MOVIES)
            val hiddenGroups = preferencesManager.getHiddenGroups(ContentFilterSettings.ContentType.MOVIES)
            val hiddenCategories = preferencesManager.getHiddenCategories(ContentFilterSettings.ContentType.MOVIES)
            val filterMode = preferencesManager.getFilterMode(ContentFilterSettings.ContentType.MOVIES)
            PerformanceLogger.end("Filter settings load", filterStartTime, 
                "grouping=$groupingEnabled, hiddenGroups=${hiddenGroups.size}, hiddenCategories=${hiddenCategories.size}")
            
            // Try to load cached navigation tree first
            PerformanceLogger.logPhase("MoviesFragment.loadData", "Checking navigation tree cache")
            val navTreeStartTime = PerformanceLogger.start("Navigation tree cache lookup")
            navigationTree = repository.getCachedVodNavigationTree()
            if (navigationTree != null) {
                PerformanceLogger.logCacheHit("movies", "navigationTree", navigationTree?.groups?.size ?: 0)
                PerformanceLogger.end("Navigation tree cache lookup", navTreeStartTime, "HIT")
            } else {
                PerformanceLogger.logCacheMiss("movies", "navigationTree", "not cached")
                PerformanceLogger.end("Navigation tree cache lookup", navTreeStartTime, "MISS")
            }
            
            // Load categories
            PerformanceLogger.logPhase("MoviesFragment.loadData", "Loading categories")
            val categoriesStartTime = PerformanceLogger.start("Categories load")
            val categoriesResult = repository.getMovieCategories()
            if (categoriesResult.isSuccess) {
                categories = categoriesResult.getOrNull() ?: emptyList()
                PerformanceLogger.end("Categories load", categoriesStartTime, "count=${categories.size}")
                
                // Build navigation tree with hierarchical filter settings if cache miss
                if (navigationTree == null) {
                    PerformanceLogger.logPhase("MoviesFragment.loadData", "Building navigation tree")
                    val treeStartTime = PerformanceLogger.start("Build navigation tree")
                    navigationTree = CategoryGrouper.buildVodNavigationTree(
                        categories,
                        groupingEnabled,
                        separator,
                        hiddenGroups,
                        hiddenCategories,
                        filterMode
                    )
                    val groupCount = navigationTree?.groups?.size ?: 0
                    PerformanceLogger.end("Build navigation tree", treeStartTime, "groups=$groupCount")
                }
            } else {
                PerformanceLogger.end("Categories load", categoriesStartTime, "FAILED")
            }
            
            // Show groups (no need to load all movies)
            PerformanceLogger.logPhase("MoviesFragment.loadData", "Updating UI")
            PerformanceLogger.log("Skipping allMovies load - using DB counts and Paging3 for categories")
            val uiStartTime = PerformanceLogger.start("Show groups UI update")
            showGroups()
            PerformanceLogger.end("Show groups UI update", uiStartTime)
            
            showLoading(false)
            
            // End total timer
            PerformanceLogger.end("MoviesFragment.loadData", totalStartTime, "SUCCESS")
        }
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
                modernToolbar.title = "Movies"
                breadcrumbScroll.visibility = View.GONE
                breadcrumbChips.removeAllViews()
            }
            NavigationLevel.CATEGORIES -> {
                modernToolbar.title = "Movies"
                breadcrumbScroll.visibility = View.VISIBLE
                breadcrumbChips.removeAllViews()
                addBreadcrumbChip(selectedGroup?.name ?: "")
            }
            NavigationLevel.CONTENT -> {
                modernToolbar.title = "Movies"
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
        val intent = Intent(requireContext(), com.iptv.app.ui.detail.MovieDetailActivity::class.java).apply {
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
