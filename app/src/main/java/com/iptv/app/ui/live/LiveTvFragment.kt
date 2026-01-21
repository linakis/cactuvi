package com.iptv.app.ui.live

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.iptv.app.R
import com.iptv.app.data.models.Category
import com.iptv.app.data.models.LiveChannel
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.ui.common.CategoryTreeAdapter
import com.iptv.app.ui.common.GroupAdapter
import com.iptv.app.ui.common.LiveChannelPagingAdapter
import com.iptv.app.ui.common.ModernToolbar
import com.iptv.app.ui.player.PlayerActivity
import com.iptv.app.data.models.ContentFilterSettings
import com.iptv.app.utils.CategoryGrouper
import com.iptv.app.utils.CategoryGrouper.GroupNode
import com.iptv.app.utils.CategoryGrouper.NavigationTree
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.PreferencesManager
import com.iptv.app.utils.StreamUrlBuilder
import kotlinx.coroutines.flow.collectLatest
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
            CredentialsManager.getInstance(requireContext()),
            requireContext()
        )
        
        database = com.iptv.app.data.db.AppDatabase.getInstance(requireContext())
        
        setupToolbar()
        setupRecyclerView()
        loadData()
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
            val intent = Intent(requireContext(), com.iptv.app.ui.search.SearchActivity::class.java).apply {
                putExtra(com.iptv.app.ui.search.SearchActivity.EXTRA_CONTENT_TYPE, 
                         com.iptv.app.ui.search.SearchActivity.TYPE_LIVE)
            }
            startActivity(intent)
        }
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Initialize adapters
        groupAdapter = GroupAdapter(emptyList()) { group ->
            onGroupSelected(group)
        }
        
        categoryAdapter = CategoryTreeAdapter(emptyList()) { category ->
            onCategorySelected(category)
        }
        
        // contentAdapter is already initialized as lazy property
        
        // Start with groups
        recyclerView.adapter = groupAdapter
    }
    
    private fun loadData() {
        showLoading(true)
        
        lifecycleScope.launch {
            val preferencesManager = PreferencesManager.getInstance(requireContext())
            
            // Get filter settings
            val groupingEnabled = preferencesManager.isGroupingEnabled(ContentFilterSettings.ContentType.LIVE_TV)
            val separator = preferencesManager.getCustomSeparator(ContentFilterSettings.ContentType.LIVE_TV)
            val hiddenGroups = preferencesManager.getHiddenGroups(ContentFilterSettings.ContentType.LIVE_TV)
            val hiddenCategories = preferencesManager.getHiddenCategories(ContentFilterSettings.ContentType.LIVE_TV)
            val filterMode = preferencesManager.getFilterMode(ContentFilterSettings.ContentType.LIVE_TV)
            
            // Try to load cached navigation tree first
            navigationTree = repository.getCachedLiveNavigationTree()
            
            // Load categories
            val categoriesResult = repository.getLiveCategories()
            if (categoriesResult.isSuccess) {
                categories = categoriesResult.getOrNull() ?: emptyList()
                
                // Build navigation tree with hierarchical filter settings if cache miss
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
            
            // Show groups (no need to load all channels)
            showGroups()
            showLoading(false)
        }
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
                modernToolbar.title = "Live TV"
                breadcrumbScroll.visibility = View.GONE
                breadcrumbChips.removeAllViews()
            }
            NavigationLevel.CATEGORIES -> {
                modernToolbar.title = "Live TV"
                breadcrumbScroll.visibility = View.VISIBLE
                breadcrumbChips.removeAllViews()
                addBreadcrumbChip(selectedGroup?.name ?: "")
            }
            NavigationLevel.CONTENT -> {
                modernToolbar.title = "Live TV"
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
        val credentials = CredentialsManager.getInstance(requireContext()).getCredentials() ?: return
        
        val streamUrl = StreamUrlBuilder.buildLiveUrl(
            server = credentials.server,
            username = credentials.username,
            password = credentials.password,
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
