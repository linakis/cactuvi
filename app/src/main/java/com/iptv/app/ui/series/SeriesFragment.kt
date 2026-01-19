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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.iptv.app.R
import com.iptv.app.data.models.Category
import com.iptv.app.data.models.Series
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.ui.common.CategoryTreeAdapter
import com.iptv.app.ui.common.GroupAdapter
import com.iptv.app.ui.common.ModernToolbar
import com.iptv.app.ui.common.SeriesPagingAdapter
import com.iptv.app.data.models.ContentFilterSettings
import com.iptv.app.utils.CategoryGrouper
import com.iptv.app.utils.CategoryGrouper.GroupNode
import com.iptv.app.utils.CategoryGrouper.NavigationTree
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.PreferencesManager
import kotlinx.coroutines.flow.collectLatest
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
    private var navigationTree: NavigationTree? = null
    private var allSeries: List<Series> = emptyList()
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
            CredentialsManager.getInstance(requireContext()),
            requireContext()
        )
        
        setupToolbar()
        setupRecyclerView()
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
    }
    
    private fun setupRecyclerView() {
        // Initialize adapters
        groupAdapter = GroupAdapter(emptyList()) { group ->
            onGroupSelected(group)
        }
        
        categoryAdapter = CategoryTreeAdapter(emptyList()) { category ->
            onCategorySelected(category)
        }
        
        // contentAdapter is already initialized as lazy property
        
        // Start with groups (linear layout)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = groupAdapter
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
        showLoading(true)
        
        lifecycleScope.launch {
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
            if (categoriesResult.isSuccess) {
                categories = categoriesResult.getOrNull() ?: emptyList()
                
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
            
            // Load series
            val seriesResult = repository.getSeries()
            if (seriesResult.isSuccess) {
                allSeries = seriesResult.getOrNull() ?: emptyList()
                
                // Set category names
                allSeries.forEach { series ->
                    val category = categories.find { it.categoryId == series.categoryId }
                    series.categoryName = category?.categoryName ?: ""
                }
                
                // Show groups
                showGroups()
                showLoading(false)
            } else {
                showError()
            }
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
        
        // Update adapter data - convert categories to pairs with counts
        val categoriesWithCounts = group.categories.map { category ->
            val count = allSeries.count { it.categoryId == category.categoryId }
            Pair(category, count)
        }
        categoryAdapter.updateCategories(categoriesWithCounts)
        
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
                modernToolbar.title = "Series"
                breadcrumbScroll.visibility = View.GONE
                breadcrumbChips.removeAllViews()
            }
            NavigationLevel.CATEGORIES -> {
                modernToolbar.title = "Series"
                breadcrumbScroll.visibility = View.VISIBLE
                breadcrumbChips.removeAllViews()
                addBreadcrumbChip(selectedGroup?.name ?: "")
            }
            NavigationLevel.CONTENT -> {
                modernToolbar.title = "Series"
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
