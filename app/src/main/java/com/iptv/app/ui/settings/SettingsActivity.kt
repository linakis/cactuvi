package com.iptv.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iptv.app.R
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.ui.common.ModernToolbar
import com.iptv.app.utils.CredentialsManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var modernToolbar: ModernToolbar
    private lateinit var repository: ContentRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        repository = ContentRepository(
            CredentialsManager.getInstance(this),
            this
        )
        
        setupToolbar()
        setupClickListeners()
    }
    
    private fun setupToolbar() {
        modernToolbar = findViewById(R.id.modernToolbar)
        modernToolbar.onBackClick = { finish() }
    }
    
    private fun setupClickListeners() {
        // Clear Cache
        findViewById<View>(R.id.clearCacheCard).setOnClickListener {
            showClearCacheDialog()
        }
        
        // Clear Watch History
        findViewById<View>(R.id.clearHistoryCard).setOnClickListener {
            showClearHistoryDialog()
        }
        
        // Content Filters
        findViewById<View>(R.id.moviesFilterCard).setOnClickListener {
            startActivity(Intent(this, MoviesFilterSettingsActivity::class.java))
        }
        
        findViewById<View>(R.id.seriesFilterCard).setOnClickListener {
            startActivity(Intent(this, SeriesFilterSettingsActivity::class.java))
        }
        
        findViewById<View>(R.id.liveTvFilterCard).setOnClickListener {
            startActivity(Intent(this, LiveTvFilterSettingsActivity::class.java))
        }
    }
    
    private fun showClearCacheDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Cache")
            .setMessage("This will remove all cached data. You'll need an internet connection to reload content. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                clearCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearCache() {
        lifecycleScope.launch {
            val result = repository.clearAllCache()
            
            if (result.isSuccess) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Cache cleared successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    "Failed to clear cache",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Watch History")
            .setMessage("This will remove all watch progress. This cannot be undone. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                clearHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearHistory() {
        lifecycleScope.launch {
            val result = repository.clearWatchHistory()
            
            if (result.isSuccess) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Watch history cleared successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    "Failed to clear watch history",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
