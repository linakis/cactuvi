package com.iptv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iptv.app.MainActivity
import com.iptv.app.R
import com.iptv.app.data.db.AppDatabase
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.SourceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Loading screen for first app launch.
 * Checks if cache exists:
 * - No cache: Block and fetch initial data from API
 * - Cache exists: Skip immediately to MainActivity
 * 
 * Only shown on first launch, subsequent launches go directly to MainActivity.
 */
class LoadingActivity : AppCompatActivity() {
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var retryButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)
        
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        detailText = findViewById(R.id.detailText)
        retryButton = findViewById(R.id.retryButton)
        
        retryButton.setOnClickListener {
            retryButton.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            checkCacheAndLoad()
        }
        
        checkCacheAndLoad()
    }
    
    private fun checkCacheAndLoad() {
        lifecycleScope.launch {
            try {
                // Check if any sources are configured
                val sourceManager = SourceManager.getInstance(this@LoadingActivity)
                val sources = sourceManager.getAllSources()
                
                if (sources.isEmpty()) {
                    // No sources configured - jump directly to add source screen
                    navigateToAddSource()
                    return@launch
                }
                
                val database = AppDatabase.getInstance(this@LoadingActivity)
                
                // Check if cache exists for any content type
                val moviesMetadata = database.cacheMetadataDao().get("movies")
                val seriesMetadata = database.cacheMetadataDao().get("series")
                val liveMetadata = database.cacheMetadataDao().get("live")
                
                val hasCache = (moviesMetadata?.itemCount ?: 0) > 0 ||
                               (seriesMetadata?.itemCount ?: 0) > 0 ||
                               (liveMetadata?.itemCount ?: 0) > 0
                
                if (hasCache) {
                    // Cache exists - skip to MainActivity
                    navigateToMain()
                } else {
                    // No cache - fetch initial data
                    fetchInitialData()
                }
            } catch (e: Exception) {
                showError("Failed to check cache: ${e.message}")
            }
        }
    }
    
    private suspend fun fetchInitialData() {
        try {
            val repository = ContentRepository(
                SourceManager.getInstance(this),
                this
            )
            
            statusText.text = "Loading content for the first time..."
            detailText.text = "This may take a few moments"
            
            // Fetch all content types in parallel
            val results = lifecycleScope.async {
                val moviesDeferred = async { repository.getMovies(forceRefresh = true) }
                val seriesDeferred = async { repository.getSeries(forceRefresh = true) }
                val liveDeferred = async { repository.getLiveStreams(forceRefresh = true) }
                
                Triple(
                    moviesDeferred.await(),
                    seriesDeferred.await(),
                    liveDeferred.await()
                )
            }.await()
            
            val (moviesResult, seriesResult, liveResult) = results
            
            // Check if at least one succeeded
            if (moviesResult.isSuccess || seriesResult.isSuccess || liveResult.isSuccess) {
                statusText.text = "Ready!"
                navigateToMain()
            } else {
                val error = moviesResult.exceptionOrNull()
                    ?: seriesResult.exceptionOrNull()
                    ?: liveResult.exceptionOrNull()
                showError("Failed to load content: ${error?.message ?: "Unknown error"}")
            }
        } catch (e: Exception) {
            showError("Failed to load content: ${e.message}")
        }
    }
    
    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        statusText.text = "Error"
        detailText.text = message
        retryButton.visibility = View.VISIBLE
        retryButton.requestFocus()
    }
    
    private fun navigateToAddSource() {
        val intent = Intent(this, com.iptv.app.ui.settings.AddEditSourceActivity::class.java)
        startActivityForResult(intent, REQUEST_ADD_SOURCE)
    }
    
    private fun showNoSourcesError() {
        progressBar.visibility = View.GONE
        statusText.text = "No Sources Configured"
        detailText.text = "Please add an IPTV source to continue"
        retryButton.text = "Add Source"
        retryButton.visibility = View.VISIBLE
        retryButton.requestFocus()
        
        retryButton.setOnClickListener {
            navigateToAddSource()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_SOURCE && resultCode == RESULT_OK) {
            // Source added, retry loading
            retryButton.text = "Retry"
            retryButton.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            checkCacheAndLoad()
        }
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    companion object {
        private const val REQUEST_ADD_SOURCE = 1
    }
}