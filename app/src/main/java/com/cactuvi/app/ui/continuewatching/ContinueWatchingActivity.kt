package com.cactuvi.app.ui.continuewatching

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.ui.player.PlayerActivity
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.StreamUrlBuilder
import kotlinx.coroutines.launch

class ContinueWatchingActivity : AppCompatActivity() {
    
    private lateinit var modernToolbar: ModernToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ContinueWatchingAdapter
    private lateinit var repository: ContentRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_continue_watching)
        
        repository = ContentRepository.getInstance(this)
        
        setupToolbar()
        setupRecyclerView()
        loadWatchHistory()
    }
    
    private fun setupToolbar() {
        modernToolbar = findViewById(R.id.modernToolbar)
        modernToolbar.onBackClick = { finish() }
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = ContinueWatchingAdapter(
            items = emptyList(),
            onItemClick = { item -> resumePlayback(item) },
            onDeleteClick = { item -> showDeleteConfirmation(item) }
        )
        recyclerView.adapter = adapter
    }
    
    private fun loadWatchHistory() {
        showLoading(true)
        
        lifecycleScope.launch {
            val result = repository.getWatchHistory(limit = 50)
            
            if (result.isSuccess) {
                val history = result.getOrNull() ?: emptyList()
                
                if (history.isEmpty()) {
                    showEmptyState()
                } else {
                    adapter.updateItems(history)
                    recyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }
            } else {
                Toast.makeText(
                    this@ContinueWatchingActivity,
                    "Failed to load watch history",
                    Toast.LENGTH_SHORT
                ).show()
                showEmptyState()
            }
            
            showLoading(false)
        }
    }
    
    private fun resumePlayback(item: com.cactuvi.app.data.db.entities.WatchHistoryEntity) {
        val credentials = CredentialsManager.getInstance(this)
        if (credentials == null) {
            Toast.makeText(this, "No credentials found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val streamUrl = when (item.contentType) {
            "movie" -> {
                // For movies, we need to get the container extension
                // Try to parse from contentId or default to mp4
                val streamId = item.contentId.toIntOrNull() ?: return
                StreamUrlBuilder.buildMovieUrl(
                    server = credentials.getServer(),
                    username = credentials.getUsername(),
                    password = credentials.getPassword(),
                    streamId = streamId,
                    extension = "mp4" // Default extension
                )
            }
            "series" -> {
                // For series episodes, the contentId is the episode ID
                StreamUrlBuilder.buildSeriesUrl(
                    server = credentials.getServer(),
                    username = credentials.getUsername(),
                    password = credentials.getPassword(),
                    episodeId = item.contentId,
                    extension = "mp4" // Default extension
                )
            }
            "live_channel" -> {
                val streamId = item.contentId.toIntOrNull() ?: return
                StreamUrlBuilder.buildLiveUrl(
                    server = credentials.getServer(),
                    username = credentials.getUsername(),
                    password = credentials.getPassword(),
                    streamId = streamId,
                    extension = "ts"
                )
            }
            else -> {
                Toast.makeText(this, "Unknown content type", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("TITLE", item.contentName)
            putExtra("CONTENT_ID", item.contentId)
            putExtra("CONTENT_TYPE", item.contentType)
            putExtra("POSTER_URL", item.posterUrl)
            putExtra("RESUME_POSITION", item.resumePosition)
        }
        startActivity(intent)
    }
    
    private fun formatTime(milliseconds: Long): String {
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds / (1000 * 60)) % 60
        val seconds = (milliseconds / 1000) % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyState.visibility = View.GONE
    }
    
    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    
    private fun showDeleteConfirmation(item: com.cactuvi.app.data.db.entities.WatchHistoryEntity) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Remove from Continue Watching")
            .setMessage("Are you sure you want to remove \"${item.contentName}\" from your continue watching list?")
            .setPositiveButton("Remove") { _, _ ->
                deleteWatchHistoryItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteWatchHistoryItem(item: com.cactuvi.app.data.db.entities.WatchHistoryEntity) {
        lifecycleScope.launch {
            val result = repository.deleteWatchHistoryItem(item.contentId)
            
            if (result.isSuccess) {
                Toast.makeText(
                    this@ContinueWatchingActivity,
                    "Removed from continue watching",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Reload the list
                loadWatchHistory()
            } else {
                Toast.makeText(
                    this@ContinueWatchingActivity,
                    "Failed to remove item",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
