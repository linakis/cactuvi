package com.iptv.app.ui.settings

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.iptv.app.R
import com.iptv.app.data.models.ContentFilterSettings
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.ui.common.HierarchicalFolderAdapter
import com.iptv.app.ui.common.HierarchicalItem
import com.iptv.app.ui.common.HierarchicalItemHelper
import com.iptv.app.ui.common.ModernToolbar
import com.iptv.app.utils.CategoryGrouper
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.PreferencesManager
import kotlinx.coroutines.launch

class ManageFoldersActivity : AppCompatActivity() {
    
    private lateinit var modernToolbar: ModernToolbar
    private lateinit var searchInput: EditText
    private lateinit var modeSwitch: SwitchMaterial
    private lateinit var selectAllButton: MaterialButton
    private lateinit var deselectAllButton: MaterialButton
    private lateinit var foldersRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var applyButton: MaterialButton
    
    private lateinit var repository: ContentRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var hierarchicalAdapter: HierarchicalFolderAdapter
    
    private lateinit var contentType: ContentFilterSettings.ContentType
    private var groups: List<HierarchicalItem.GroupItem> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_folders)
        
        val contentTypeString = intent.getStringExtra("content_type") ?: run {
            finish()
            return
        }
        
        contentType = ContentFilterSettings.ContentType.valueOf(contentTypeString)
        
        repository = ContentRepository(
            CredentialsManager.getInstance(this),
            this
        )
        preferencesManager = PreferencesManager.getInstance(this)
        
        setupToolbar()
        setupViews()
        loadFolders()
    }
    
    private fun setupToolbar() {
        modernToolbar = findViewById(R.id.modernToolbar)
        modernToolbar.onBackClick = { finish() }
    }
    
    private fun setupViews() {
        searchInput = findViewById(R.id.searchInput)
        modeSwitch = findViewById(R.id.modeSwitch)
        selectAllButton = findViewById(R.id.selectAllButton)
        deselectAllButton = findViewById(R.id.deselectAllButton)
        foldersRecyclerView = findViewById(R.id.foldersRecyclerView)
        emptyState = findViewById(R.id.emptyState)
        applyButton = findViewById(R.id.applyButton)
        
        // Load current settings
        val filterMode = preferencesManager.getFilterMode(contentType)
        modeSwitch.isChecked = filterMode == ContentFilterSettings.FilterMode.WHITELIST
        
        setupListeners()
    }
    
    private fun setupListeners() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                hierarchicalAdapter.filter(s.toString())
            }
        })
        
        modeSwitch.setOnCheckedChangeListener { _, _ ->
            // Mode change doesn't affect UI immediately, only on apply
        }
        
        selectAllButton.setOnClickListener {
            hierarchicalAdapter.selectAll()
        }
        
        deselectAllButton.setOnClickListener {
            hierarchicalAdapter.deselectAll()
        }
        
        applyButton.setOnClickListener {
            applyChanges()
        }
    }
    
    private fun loadFolders() {
        lifecycleScope.launch {
            try {
                // Load categories based on content type
                val categories = when (contentType) {
                    ContentFilterSettings.ContentType.MOVIES -> {
                        val result = repository.getMovieCategories()
                        result.getOrNull() ?: emptyList()
                    }
                    ContentFilterSettings.ContentType.SERIES -> {
                        val result = repository.getSeriesCategories()
                        result.getOrNull() ?: emptyList()
                    }
                    ContentFilterSettings.ContentType.LIVE_TV -> {
                        val result = repository.getLiveCategories()
                        result.getOrNull() ?: emptyList()
                    }
                }
                
                if (categories.isEmpty()) {
                    showEmptyState()
                    return@launch
                }
                
                // Build navigation tree to get groups with categories
                val separator = preferencesManager.getCustomSeparator(contentType)
                val tree = when (contentType) {
                    ContentFilterSettings.ContentType.MOVIES -> 
                        CategoryGrouper.buildVodNavigationTree(categories, true, separator, emptySet(), ContentFilterSettings.FilterMode.BLACKLIST)
                    ContentFilterSettings.ContentType.SERIES -> 
                        CategoryGrouper.buildSeriesNavigationTree(categories, true, separator, emptySet(), ContentFilterSettings.FilterMode.BLACKLIST)
                    ContentFilterSettings.ContentType.LIVE_TV -> 
                        CategoryGrouper.buildLiveNavigationTree(categories, true, separator, emptySet(), ContentFilterSettings.FilterMode.BLACKLIST)
                }
                
                // Convert to hierarchical items
                groups = tree.groups.map { groupNode ->
                    HierarchicalItem.GroupItem(
                        name = groupNode.name,
                        categories = groupNode.categories,
                        isExpanded = false,
                        isChecked = false,
                        isIndeterminate = false
                    )
                }.sortedBy { it.name }
                
                // Load current selections from hierarchical preferences
                val hiddenGroups = preferencesManager.getHiddenGroups(contentType)
                val hiddenCategories = preferencesManager.getHiddenCategories(contentType)
                
                // Apply selections to hierarchical items
                HierarchicalItemHelper.applySelections(groups, hiddenGroups, hiddenCategories)
                
                // Setup adapter
                hierarchicalAdapter = HierarchicalFolderAdapter(
                    groups = groups,
                    onGroupToggled = { group ->
                        // When group checkbox is toggled, update all children
                        val displayList = hierarchicalAdapter.getSelectedCategories() // triggers update
                        hierarchicalAdapter.updateDisplayList()
                    },
                    onCategoryToggled = { category ->
                        // When category is toggled, update parent state
                        val displayList = HierarchicalItemHelper.buildDisplayList(groups)
                        HierarchicalItemHelper.updateParentState(groups, displayList, category.groupName)
                        hierarchicalAdapter.updateDisplayList()
                    },
                    onGroupExpanded = { groupName ->
                        // Toggle expansion
                        groups.find { it.name == groupName }?.let { group ->
                            group.isExpanded = !group.isExpanded
                        }
                    }
                )
                
                foldersRecyclerView.layoutManager = LinearLayoutManager(this@ManageFoldersActivity)
                foldersRecyclerView.adapter = hierarchicalAdapter
                
                hideEmptyState()
            } catch (e: Exception) {
                Toast.makeText(this@ManageFoldersActivity, "Failed to load folders: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
        }
    }
    
    private fun applyChanges() {
        val isWhitelist = modeSwitch.isChecked
        val filterMode = if (isWhitelist) {
            ContentFilterSettings.FilterMode.WHITELIST
        } else {
            ContentFilterSettings.FilterMode.BLACKLIST
        }
        
        val selectedGroups = hierarchicalAdapter.getSelectedGroups()
        val selectedCategories = hierarchicalAdapter.getSelectedCategories()
        
        // Save settings using hierarchical methods
        preferencesManager.setFilterMode(contentType, filterMode)
        preferencesManager.setHiddenGroups(contentType, selectedGroups)
        preferencesManager.setHiddenCategories(contentType, selectedCategories)
        
        // Invalidate navigation cache to force rebuild with new filters
        lifecycleScope.launch {
            when (contentType) {
                ContentFilterSettings.ContentType.MOVIES -> repository.invalidateMovieNavigationCache()
                ContentFilterSettings.ContentType.SERIES -> repository.invalidateSeriesNavigationCache()
                ContentFilterSettings.ContentType.LIVE_TV -> repository.invalidateLiveNavigationCache()
            }
            
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
    
    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        foldersRecyclerView.visibility = View.GONE
    }
    
    private fun hideEmptyState() {
        emptyState.visibility = View.GONE
        foldersRecyclerView.visibility = View.VISIBLE
    }
}
