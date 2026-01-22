package com.iptv.app.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.iptv.app.R
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.SourceManager
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var repository: ContentRepository
    
    private var streamUrl: String = ""
    private var title: String = ""
    private var contentId: String? = null
    private var contentType: String = ""
    private var posterUrl: String? = null
    private var resumePosition: Long = 0
    
    // Progress tracking
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdateInterval = 10000L // 10 seconds
    private var lastSavedPosition: Long = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        
        // Hide system UI for immersive playback
        hideSystemUI()
        
        playerView = findViewById(R.id.playerView)
        
        // Initialize repository
        repository = ContentRepository(
            SourceManager.getInstance(this),
            this
        )
        
        // Get data from intent
        streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
        title = intent.getStringExtra("TITLE") ?: ""
        contentId = intent.getStringExtra("CONTENT_ID")
        contentType = intent.getStringExtra("CONTENT_TYPE") ?: ""
        posterUrl = intent.getStringExtra("POSTER_URL")
        resumePosition = intent.getLongExtra("RESUME_POSITION", 0)
        
        if (streamUrl.isEmpty()) {
            Toast.makeText(this, "Invalid stream URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Set title in player controls
        playerView.findViewById<android.widget.TextView>(R.id.exo_title)?.text = title
        
        initializePlayer()
    }
    
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            
            // Create media item
            val mediaItem = MediaItem.fromUri(streamUrl)
            
            // Set media item and prepare
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            
            // Seek to resume position if provided
            if (resumePosition > 0) {
                exoPlayer.seekTo(resumePosition)
            }
            
            // Start playback when ready
            exoPlayer.playWhenReady = true
            
            // Add listener for playback events
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            // Show loading indicator
                        }
                        Player.STATE_READY -> {
                            // Hide loading indicator
                            // Start progress tracking
                            startProgressTracking()
                        }
                        Player.STATE_ENDED -> {
                            // Mark as completed and save
                            saveWatchProgress(isCompleted = true)
                            finish()
                        }
                    }
                }
                
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(
                        this@PlayerActivity,
                        getString(R.string.playback_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }
    }
    
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }
    
    private fun startProgressTracking() {
        progressHandler.removeCallbacks(progressUpdateRunnable)
        progressHandler.post(progressUpdateRunnable)
    }
    
    private fun stopProgressTracking() {
        progressHandler.removeCallbacks(progressUpdateRunnable)
    }
    
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            player?.let { exoPlayer ->
                val currentPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                
                // Only save if position has changed significantly (>5 seconds)
                if (Math.abs(currentPos - lastSavedPosition) > 5000 && duration > 0) {
                    saveWatchProgress(isCompleted = false)
                    lastSavedPosition = currentPos
                }
            }
            
            // Schedule next update
            progressHandler.postDelayed(this, progressUpdateInterval)
        }
    }
    
    private fun saveWatchProgress(isCompleted: Boolean) {
        val id = contentId ?: return
        val type = contentType.takeIf { it.isNotEmpty() } ?: return
        
        player?.let { exoPlayer ->
            val currentPosition = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            
            // Don't save if duration is invalid
            if (duration <= 0) return
            
            lifecycleScope.launch {
                try {
                    repository.updateWatchProgress(
                        contentId = id,
                        contentType = type,
                        contentName = title,
                        posterUrl = posterUrl,
                        resumePosition = if (isCompleted) duration else currentPosition,
                        duration = duration
                    )
                } catch (e: Exception) {
                    // Silently fail - don't interrupt playback
                }
            }
        }
    }
    
    private fun releasePlayer() {
        stopProgressTracking()
        
        player?.let { exoPlayer ->
            // Save final playback position
            if (contentId != null && contentType.isNotEmpty()) {
                saveWatchProgress(isCompleted = false)
            }
            
            exoPlayer.release()
        }
        player = null
    }
    
    override fun onStart() {
        super.onStart()
        if (player == null) {
            initializePlayer()
        }
    }
    
    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
