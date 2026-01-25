package com.cactuvi.app.ui.common

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.utils.CategoryGrouper.GroupNode
import com.cactuvi.app.utils.IdleDetectionHelper
import com.cactuvi.app.utils.PerformanceLogger
import com.cactuvi.app.utils.SourceManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Generic base fragment for content navigation (Movies, Series, Live TV). Handles all shared
 * navigation logic: groups → categories → content. Subclasses only need to provide content-specific
 * behavior via abstract methods.
 *
 * @param T Content type (Movie, Series, LiveChannel)
 */
abstract class ContentNavigationFragment<T : Any> : Fragment() {

    // UI Components
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var progressBar: ProgressBar
    protected lateinit var errorText: TextView
    protected lateinit var emptyText: TextView
    protected lateinit var modernToolbar: ModernToolbar
    protected lateinit var breadcrumbScroll: HorizontalScrollView
    protected lateinit var breadcrumbChips: ChipGroup

    // Adapters
    protected lateinit var groupAdapter: GroupAdapter
    protected lateinit var categoryAdapter: CategoryTreeAdapter
    protected abstract val contentAdapter: PagingDataAdapter<T, *>

    // ========== ABSTRACT METHODS (content-specific) ==========

    /** Get ViewModel that manages navigation state */
    protected abstract fun getViewModel(): ContentViewModel<T>

    /** Get paged content flow from ViewModel */
    protected abstract fun getPagedContentFlow(): Flow<androidx.paging.PagingData<T>>

    /** Get display title for this content type (e.g. "Movies", "Series", "Live TV") */
    protected abstract fun getContentTitle(): String

    /** Get search type constant for SearchActivity */
    protected abstract fun getSearchType(): String

    /** Get count of items in a category (for display) */
    protected abstract suspend fun getCategoryItemCount(categoryId: String): Int

    /** Handle click on content item (navigate to detail screen) */
    protected abstract fun onContentItemClick(item: T)

    /**
     * Get layout manager for content view. Override to customize layout (e.g. list vs grid).
     * Default: GridLayoutManager with 3 columns
     */
    protected open fun getContentLayoutManager(): RecyclerView.LayoutManager {
        return GridLayoutManager(requireContext(), 3)
    }

    // ========== LIFECYCLE ==========

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
        observePagedContent()
    }

    // ========== SETUP ==========

    private fun setupToolbar() {
        modernToolbar.title = getContentTitle()
        modernToolbar.onBackClick = {
            val handled = getViewModel().navigateBack()
            if (!handled) {
                requireActivity().finish()
            }
        }
        modernToolbar.onActionClick = {
            val intent =
                android.content
                    .Intent(
                        requireContext(),
                        com.cactuvi.app.ui.search.SearchActivity::class.java,
                    )
                    .apply {
                        putExtra(
                            com.cactuvi.app.ui.search.SearchActivity.EXTRA_CONTENT_TYPE,
                            getSearchType(),
                        )
                    }
            startActivity(intent)
        }

        // Observe active source and update title
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SourceManager.getInstance(requireContext()).getActiveSourceFlow().collectLatest {
                    source ->
                    val sourceName = source?.nickname ?: "No Source"
                    modernToolbar.title = "${getContentTitle()} • $sourceName"
                }
            }
        }
    }

    private fun setupRecyclerView() {
        // Initialize adapters
        groupAdapter = GroupAdapter { group -> getViewModel().selectGroup(group.name) }

        categoryAdapter = CategoryTreeAdapter { category ->
            getViewModel().selectCategory(category.categoryId)
        }

        // contentAdapter is initialized by subclass

        // Start with groups (linear layout)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = groupAdapter

        // Attach idle detection
        IdleDetectionHelper.attach(recyclerView)
    }

    // ========== OBSERVATION ==========

    /** Observe ViewModel UiState - single source of truth. */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                getViewModel().uiState.collectLatest { state -> renderUiState(state) }
            }
        }
    }

    private fun observePagedContent() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                getPagedContentFlow().collectLatest { pagingData ->
                    contentAdapter.submitData(pagingData)
                }
            }
        }
    }

    // ========== UI RENDERING ==========

    /** Render UI based on current state. Pure UI rendering - no business logic. */
    private fun renderUiState(state: ContentUiState) {
        // Update loading/error visibility
        progressBar.isVisible = state.showLoading
        errorText.isVisible = state.showError
        recyclerView.isVisible = state.showContent

        if (state.showError) {
            errorText.text = "${state.error}\n\nTap to retry"
            errorText.setOnClickListener { getViewModel().refresh() }
            return
        }

        // Render content based on navigation level
        when (state.currentLevel) {
            NavigationLevel.GROUPS -> {
                showGroupsView(state.navigationTree)
            }
            NavigationLevel.CATEGORIES -> {
                state.selectedGroup?.let { group -> showCategoriesView(group) }
            }
            NavigationLevel.CONTENT -> {
                state.selectedCategoryId?.let { categoryId -> showContentView(categoryId) }
            }
        }

        // Update breadcrumb
        updateBreadcrumb(state)
    }

    private fun showGroupsView(
        navigationTree: com.cactuvi.app.utils.CategoryGrouper.NavigationTree?
    ) {
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
            val categoriesWithCounts =
                group.categories.map { category ->
                    val count = getCategoryItemCount(category.categoryId)
                    Pair(category, count)
                }
            PerformanceLogger.end(
                "Get category counts",
                countsStart,
                "categories=${categoriesWithCounts.size}"
            )
            categoryAdapter.updateCategories(categoriesWithCounts)
        }
    }

    private fun showContentView(categoryId: String) {
        recyclerView.layoutManager = getContentLayoutManager()
        recyclerView.adapter = contentAdapter
        // Paged data is automatically loaded via observePagedContent()
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
                // Use breadcrumbPath if available (better), otherwise fallback to manual lookup
                if (state.breadcrumbPath.isNotEmpty()) {
                    state.breadcrumbPath.forEach { crumb -> addBreadcrumbChip(crumb) }
                } else {
                    addBreadcrumbChip(state.selectedGroupName ?: "")
                    // Find category name from navigationTree
                    val category =
                        state.selectedGroup?.categories?.find {
                            it.categoryId == state.selectedCategoryId
                        }
                    addBreadcrumbChip(category?.categoryName ?: "")
                }
            }
        }
    }

    private fun addBreadcrumbChip(text: String) {
        val chip =
            Chip(requireContext()).apply {
                this.text = text
                isClickable = true
                chipBackgroundColor =
                    ColorStateList.valueOf(
                        requireContext().getColor(R.color.surface_elevated),
                    )
                setTextColor(requireContext().getColor(R.color.brand_green))
                setOnClickListener { getViewModel().navigateBack() }
            }
        breadcrumbChips.addView(chip)
    }

    // ========== PUBLIC API ==========

    /** Handle back press from activity */
    open fun onBackPressed(): Boolean {
        return getViewModel().navigateBack()
    }
}
