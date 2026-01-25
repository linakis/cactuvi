package com.cactuvi.app.ui.detail

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.cactuvi.app.R
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.data.repository.DownloadRepository
import com.cactuvi.app.ui.player.PlayerActivity
import com.cactuvi.app.utils.CredentialsManager
import com.cactuvi.app.utils.SourceManager
import com.cactuvi.app.utils.StreamUrlBuilder
import kotlinx.coroutines.launch

@UnstableApi
class MovieDetailActivity : AppCompatActivity() {
    
    private lateinit var repository: ContentRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var credentialsManager: CredentialsManager
    
    private lateinit var backdropImage: ImageView
    private lateinit var posterImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var metadataText: TextView
    private lateinit var playButton: Button
    private lateinit var favoriteButton: ImageButton
    private lateinit var downloadButton: ImageButton
    private lateinit var resumeButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var infoContainer: View
    private lateinit var plotText: TextView
    private lateinit var genreText: TextView
    private lateinit var directorText: TextView
    private lateinit var castText: TextView
    
    private var vodId: Int = 0
    private var streamId: Int = 0
    private var containerExtension: String = "mp4"
    private var movieTitle: String = ""
    private var posterUrl: String? = null
    private var isFavorite: Boolean = false
    private var resumePosition: Long = 0
    private var duration: Long = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_detail)
        
        // Initialize repositories
        credentialsManager = CredentialsManager.getInstance(this)
        repository = ContentRepository.getInstance(this)
        downloadRepository = DownloadRepository(this)
        
        // Get data from intent
        vodId = intent.getIntExtra("VOD_ID", 0)
        streamId = intent.getIntExtra("STREAM_ID", 0)
        movieTitle = intent.getStringExtra("TITLE") ?: ""
        posterUrl = intent.getStringExtra("POSTER_URL")
        containerExtension = intent.getStringExtra("CONTAINER_EXTENSION") ?: "mp4"
        
        if (vodId == 0) {
            Toast.makeText(this, "Invalid movie ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        initViews()
        setupClickListeners()
        loadMovieDetails()
    }
    
    private fun initViews() {
        backdropImage = findViewById(R.id.backdropImage)
        posterImage = findViewById(R.id.posterImage)
        titleText = findViewById(R.id.titleText)
        metadataText = findViewById(R.id.metadataText)
        playButton = findViewById(R.id.playButton)
        favoriteButton = findViewById(R.id.favoriteButton)
        downloadButton = findViewById(R.id.downloadButton)
        resumeButton = findViewById(R.id.resumeButton)
        progressBar = findViewById(R.id.progressBar)
        infoContainer = findViewById(R.id.infoContainer)
        plotText = findViewById(R.id.plotText)
        genreText = findViewById(R.id.genreText)
        directorText = findViewById(R.id.directorText)
        castText = findViewById(R.id.castText)
    }
    
    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }
        
        playButton.setOnClickListener {
            playMovie(startPosition = 0)
        }
        
        favoriteButton.setOnClickListener {
            toggleFavorite()
        }
        
        downloadButton.setOnClickListener {
            handleDownloadClick()
        }
        
        resumeButton.setOnClickListener {
            playMovie(startPosition = resumePosition)
        }
    }
    
    private fun loadMovieDetails() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                // Load movie info from API
                val result = repository.getMovieInfo(vodId)
                
                if (result.isSuccess) {
                    val movieInfo = result.getOrNull()
                    movieInfo?.let { displayMovieInfo(it) }
                } else {
                    Toast.makeText(
                        this@MovieDetailActivity,
                        "Failed to load movie details",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Check if movie is in favorites
                checkFavoriteStatus()
                
                // Check for resume position
                checkResumePosition()
                
                // Check download status
                checkDownloadStatus()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@MovieDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun displayMovieInfo(movieInfo: com.cactuvi.app.data.models.MovieInfo) {
        val info = movieInfo.info
        val movieData = movieInfo.movieData
        
        // Set title
        titleText.text = info?.name ?: movieTitle
        
        // Build metadata string
        val metadata = buildString {
            info?.releaseDate?.let { 
                append(it.take(4))
                append(" · ")
            }
            info?.duration?.let { 
                append(it)
                append(" · ")
            }
            info?.rating?.let { 
                append("★ ")
                append(it)
            }
        }
        metadataText.text = metadata
        
        // Load backdrop image
        info?.name?.let {
            // Use poster as backdrop if no specific backdrop available
            Glide.with(this)
                .load(posterUrl)
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .centerCrop()
                .into(backdropImage)
        }
        
        // Load poster
        Glide.with(this)
            .load(posterUrl)
            .placeholder(R.drawable.placeholder_movie)
            .error(R.drawable.placeholder_movie)
            .centerCrop()
            .into(posterImage)
        
        // Set plot
        info?.plot?.let {
            plotText.text = it
        }
        
        // Set genre
        info?.genre?.let {
            genreText.text = it
        }
        
        // Set director
        info?.director?.let {
            directorText.text = it
        }
        
        // Set cast
        info?.cast?.let {
            castText.text = it
        }
        
        // Store duration
        info?.durationSecs?.let {
            duration = it.toLong() * 1000 // Convert to milliseconds
        }
        
        // Update container extension if available
        movieData?.containerExtension?.let {
            containerExtension = it
        }
        
        movieData?.streamId?.let {
            streamId = it
        }
        
        infoContainer.visibility = View.VISIBLE
    }
    
    private suspend fun checkFavoriteStatus() {
        val result = repository.isFavorite(streamId.toString(), "movie")
        if (result.isSuccess) {
            isFavorite = result.getOrNull() ?: false
            updateFavoriteButton()
        }
    }
    
    private suspend fun checkResumePosition() {
        val result = repository.getWatchHistory(limit = 100)
        if (result.isSuccess) {
            val history = result.getOrNull() ?: emptyList()
            val movieHistory = history.find { 
                it.contentId == streamId.toString() && it.contentType == "movie" 
            }
            
            movieHistory?.let {
                if (!it.isCompleted) {
                    resumePosition = it.resumePosition
                    duration = it.duration
                    
                    // Show resume button
                    val timeString = formatTime(resumePosition)
                    resumeButton.text = getString(R.string.resume_from, timeString)
                    resumeButton.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun updateFavoriteButton() {
        if (isFavorite) {
            favoriteButton.setImageResource(R.drawable.ic_favorite)
            favoriteButton.setBackgroundResource(R.drawable.bg_icon_button_circular)
            favoriteButton.imageTintList = null  // Remove tint to show filled orange heart
        } else {
            favoriteButton.setImageResource(R.drawable.ic_favorite_border)
            favoriteButton.setBackgroundResource(R.drawable.bg_icon_button_circular)
            favoriteButton.imageTintList = ColorStateList.valueOf(getColor(R.color.text_tertiary))
        }
    }
    
    private fun toggleFavorite() {
        lifecycleScope.launch {
            try {
                if (isFavorite) {
                    val result = repository.removeFromFavorites(streamId.toString(), "movie")
                    if (result.isSuccess) {
                        isFavorite = false
                        updateFavoriteButton()
                        Toast.makeText(
                            this@MovieDetailActivity,
                            "Removed from My List",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val result = repository.addToFavorites(
                        contentId = streamId.toString(),
                        contentType = "movie",
                        contentName = movieTitle,
                        posterUrl = posterUrl,
                        rating = metadataText.text.toString(),
                        categoryName = ""
                    )
                    if (result.isSuccess) {
                        isFavorite = true
                        updateFavoriteButton()
                        Toast.makeText(
                            this@MovieDetailActivity,
                            "Added to My List",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@MovieDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun playMovie(startPosition: Long) {
        val server = credentialsManager.getServer()
        val username = credentialsManager.getUsername()
        val password = credentialsManager.getPassword()
        
        val streamUrl = StreamUrlBuilder.buildMovieUrl(
            server = server,
            username = username,
            password = password,
            streamId = streamId,
            extension = containerExtension
        )
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("TITLE", movieTitle)
            putExtra("CONTENT_ID", streamId.toString())
            putExtra("CONTENT_TYPE", "movie")
            putExtra("POSTER_URL", posterUrl)
            putExtra("RESUME_POSITION", startPosition)
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
        infoContainer.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun checkDownloadStatus() {
        lifecycleScope.launch {
            val contentId = "movie_$streamId"
            downloadRepository.getDownload(contentId).collect { download ->
                updateDownloadButton(download?.status)
            }
        }
    }
    
    private fun updateDownloadButton(status: String?) {
        when (status) {
            "completed" -> {
                downloadButton.setImageResource(R.drawable.ic_check_circle_24)
                downloadButton.setBackgroundResource(R.drawable.bg_icon_button_circular_active)
                downloadButton.imageTintList = ColorStateList.valueOf(getColor(R.color.text_primary))
                downloadButton.isEnabled = true
            }
            "downloading", "queued" -> {
                downloadButton.setImageResource(R.drawable.ic_download)
                downloadButton.setBackgroundResource(R.drawable.bg_icon_button_circular)
                downloadButton.imageTintList = ColorStateList.valueOf(getColor(R.color.text_tertiary))
                downloadButton.isEnabled = false
            }
            else -> {
                downloadButton.setImageResource(R.drawable.ic_download)
                downloadButton.setBackgroundResource(R.drawable.bg_icon_button_circular)
                downloadButton.imageTintList = ColorStateList.valueOf(getColor(R.color.text_tertiary))
                downloadButton.isEnabled = true
            }
        }
    }
    
    private fun handleDownloadClick() {
        lifecycleScope.launch {
            val contentId = "movie_$streamId"
            val download = downloadRepository.getDownloadSync(contentId)
            
            if (download?.status == "completed") {
                // Already downloaded, show delete option
                showDeleteDownloadDialog()
            } else {
                // Start download
                startDownload()
            }
        }
    }
    
    private fun startDownload() {
        lifecycleScope.launch {
            try {
                val server = credentialsManager.getServer()
                val username = credentialsManager.getUsername()
                val password = credentialsManager.getPassword()
                
                val streamUrl = StreamUrlBuilder.buildMovieUrl(
                    server = server,
                    username = username,
                    password = password,
                    streamId = streamId,
                    extension = containerExtension
                )
                
                val contentId = "movie_$streamId"
                downloadRepository.startDownload(
                    contentId = contentId,
                    contentType = "movie",
                    contentName = movieTitle,
                    streamUrl = streamUrl,
                    posterUrl = posterUrl
                )
                
                Toast.makeText(
                    this@MovieDetailActivity,
                    "Download started",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@MovieDetailActivity,
                    "Failed to start download: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showDeleteDownloadDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Download")
            .setMessage("Do you want to delete this download?")
            .setPositiveButton("Delete") { _, _ ->
                deleteDownload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteDownload() {
        lifecycleScope.launch {
            try {
                val contentId = "movie_$streamId"
                downloadRepository.deleteDownload(contentId)
                
                Toast.makeText(
                    this@MovieDetailActivity,
                    "Download deleted",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MovieDetailActivity,
                    "Failed to delete download: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
