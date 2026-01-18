package com.iptv.app.ui.downloads

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iptv.app.R
import com.iptv.app.data.repository.DownloadRepository
import com.iptv.app.ui.common.ModernToolbar
import com.iptv.app.ui.player.PlayerActivity
import kotlinx.coroutines.launch

@UnstableApi
class DownloadsActivity : AppCompatActivity() {
    
    private lateinit var modernToolbar: ModernToolbar
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: View
    private lateinit var emptyIcon: ImageView
    private lateinit var emptyText: TextView
    private lateinit var adapter: DownloadsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)
        
        downloadRepository = DownloadRepository(this)
        
        initViews()
        setupRecyclerView()
        observeDownloads()
    }
    
    private fun initViews() {
        modernToolbar = findViewById(R.id.modernToolbar)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        emptyIcon = findViewById(R.id.emptyIcon)
        emptyText = findViewById(R.id.emptyText)
        
        modernToolbar.onBackClick = { finish() }
        
        findViewById<TextView>(R.id.clearAllButton).setOnClickListener {
            showClearAllDialog()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = DownloadsAdapter(
            onPlayClick = { download ->
                playDownload(download)
            },
            onDeleteClick = { download ->
                showDeleteDialog(download)
            }
        )
        
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter
    }
    
    private fun observeDownloads() {
        showLoading(true)
        
        lifecycleScope.launch {
            downloadRepository.getCompletedDownloads().collect { downloads ->
                showLoading(false)
                
                if (downloads.isEmpty()) {
                    showEmptyState(true)
                } else {
                    showEmptyState(false)
                    adapter.submitList(downloads)
                }
            }
        }
    }
    
    private fun playDownload(download: com.iptv.app.data.db.entities.DownloadEntity) {
        val uri = download.downloadUri
        if (uri.isNullOrEmpty()) {
            Toast.makeText(this, "Download not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", uri)
            putExtra("TITLE", download.contentName)
            putExtra("CONTENT_ID", download.contentId)
            putExtra("CONTENT_TYPE", download.contentType)
            putExtra("POSTER_URL", download.posterUrl)
            putExtra("RESUME_POSITION", 0L)
        }
        startActivity(intent)
    }
    
    private fun showDeleteDialog(download: com.iptv.app.data.db.entities.DownloadEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_download)
            .setMessage("Delete ${download.contentName}?")
            .setPositiveButton(R.string.delete_download) { _, _ ->
                deleteDownload(download)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun deleteDownload(download: com.iptv.app.data.db.entities.DownloadEntity) {
        lifecycleScope.launch {
            try {
                downloadRepository.deleteDownload(download.contentId)
                Toast.makeText(
                    this@DownloadsActivity,
                    "Download deleted",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@DownloadsActivity,
                    "Failed to delete: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Downloads")
            .setMessage("Delete all downloaded content?")
            .setPositiveButton("Delete All") { _, _ ->
                clearAllDownloads()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun clearAllDownloads() {
        lifecycleScope.launch {
            try {
                downloadRepository.deleteAllDownloads()
                Toast.makeText(
                    this@DownloadsActivity,
                    "All downloads deleted",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@DownloadsActivity,
                    "Failed to clear downloads: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showEmptyState(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}
