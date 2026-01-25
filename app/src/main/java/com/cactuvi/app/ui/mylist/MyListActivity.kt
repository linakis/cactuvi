package com.cactuvi.app.ui.mylist

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.cactuvi.app.R
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.SourceManager
import kotlinx.coroutines.launch

class MyListActivity : AppCompatActivity() {
    
    private lateinit var modernToolbar: ModernToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    private lateinit var repository: ContentRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_list)
        
        repository = ContentRepository.getInstance(this)
        
        setupToolbar()
        setupViewPager()
        loadFavorites()
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
            tab.text = when (position) {
                0 -> "All"
                1 -> "Live TV"
                2 -> "Movies"
                3 -> "Series"
                else -> ""
            }
        }.attach()
    }
    
    private fun loadFavorites() {
        showLoading(true)
        
        lifecycleScope.launch {
            val result = repository.getFavorites(contentType = null)
            
            if (result.isSuccess) {
                val favorites = result.getOrNull() ?: emptyList()
                
                if (favorites.isEmpty()) {
                    showEmptyState()
                } else {
                    viewPager.visibility = View.VISIBLE
                    tabLayout.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    
                    // Update fragments with data
                    // TODO: Pass data to fragments
                }
            } else {
                Toast.makeText(
                    this@MyListActivity,
                    "Failed to load favorites",
                    Toast.LENGTH_SHORT
                ).show()
                showEmptyState()
            }
            
            showLoading(false)
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        viewPager.visibility = if (show) View.GONE else View.VISIBLE
        tabLayout.visibility = if (show) View.GONE else View.VISIBLE
        emptyState.visibility = View.GONE
    }
    
    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        viewPager.visibility = View.GONE
        tabLayout.visibility = View.GONE
    }
}
