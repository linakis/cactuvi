package com.cactuvi.app.ui.mylist

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.cactuvi.app.R
import com.cactuvi.app.ui.common.ModernToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyListActivity : AppCompatActivity() {

    private val viewModel: MyListViewModel by viewModels()
    private lateinit var modernToolbar: ModernToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_list)

        setupToolbar()
        setupViewPager()
        observeUiState()
    }

    private fun setupToolbar() {
        modernToolbar = findViewById(R.id.modernToolbar)
        modernToolbar.onBackClick = { finish() }
    }

    private fun setupViewPager() {
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)

        val adapter = MyListPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text =
                    when (position) {
                        0 -> "All"
                        1 -> "Live TV"
                        2 -> "Movies"
                        3 -> "Series"
                        else -> ""
                    }
            }
            .attach()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state -> renderUiState(state) }
            }
        }
    }

    private fun renderUiState(state: MyListUiState) {
        // Loading state
        progressBar.isVisible = state.isLoading
        viewPager.isVisible = !state.isLoading && state.favorites.isNotEmpty()
        tabLayout.isVisible = !state.isLoading && state.favorites.isNotEmpty()
        emptyState.isVisible = !state.isLoading && state.favorites.isEmpty()

        // Error state
        state.error?.let { errorMsg -> Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show() }
    }
}
