package com.iptv.app.ui.detail

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.iptv.app.R
import com.iptv.app.data.models.Episode
import com.iptv.app.data.repository.ContentRepository
import com.iptv.app.data.repository.DownloadRepository
import com.iptv.app.ui.player.PlayerActivity
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.StreamUrlBuilder
import kotlinx.coroutines.launch

class SeriesDetailActivity : AppCompatActivity() {
    
    private lateinit var repository: ContentRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var credentialsManager: CredentialsManager
    
    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var toolbar: Toolbar
    private lateinit var coverImage: ImageView
    private lateinit var metadataText: TextView
    private lateinit var favoriteButton: ImageButton
    private lateinit var downloadButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var infoContainer: LinearLayout
    private lateinit var plotText: TextView
    private lateinit var genreText: TextView
    private lateinit var episodesContainer: LinearLayout
    private lateinit var episodesRecyclerView: RecyclerView
    
    private lateinit var adapter: SeasonEpisodeAdapter
    
    private var seriesId: Int = 0
    private var seriesTitle: String = ""
    private var coverUrl: String? = null
    private var isFavorite: Boolean = false
    private var seasons: List<com.iptv.app.data.models.Season> = emptyList()
    private var episodes: Map<String, List<com.iptv.app.data.models.Episode>> = emptyMap()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_detail)
        
        // Initialize repositories
        credentialsManager = CredentialsManager.getInstance(this)
        repository = ContentRepository(credentialsManager, this)
        downloadRepository = DownloadRepository(this)
        
        // Get data from intent
        seriesId = intent.getIntExtra("SERIES_ID", 0)
        seriesTitle = intent.getStringExtra("TITLE") ?: ""
        coverUrl = intent.getStringExtra("COVER_URL")
        
        if (seriesId == 0) {
            Toast.makeText(this, "Invalid series ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        initViews()
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadSeriesDetails()
    }
    
    private fun initViews() {
        collapsingToolbar = findViewById(R.id.collapsingToolbar)
        toolbar = findViewById(R.id.toolbar)
        coverImage = findViewById(R.id.coverImage)
        metadataText = findViewById(R.id.metadataText)
        favoriteButton = findViewById(R.id.favoriteButton)
        downloadButton = findViewById(R.id.downloadButton)
        progressBar = findViewById(R.id.progressBar)
        infoContainer = findViewById(R.id.infoContainer)
        plotText = findViewById(R.id.plotText)
        genreText = findViewById(R.id.genreText)
        episodesContainer = findViewById(R.id.episodesContainer)
        episodesRecyclerView = findViewById(R.id.episodesRecyclerView)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        collapsingToolbar.title = seriesTitle
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = SeasonEpisodeAdapter(
            onEpisodeClick = { episode, seasonNumber ->
                playEpisode(episode, seasonNumber)
            },
            onEpisodeLongClick = { episode, seasonNumber ->
                showEpisodeDownloadDialog(episode, seasonNumber)
            }
        )
        
        episodesRecyclerView.layoutManager = LinearLayoutManager(this)
        episodesRecyclerView.adapter = adapter
        episodesRecyclerView.itemAnimator = DefaultItemAnimator()
    }
    
    private fun setupClickListeners() {
        favoriteButton.setOnClickListener {
            toggleFavorite()
        }
        
        downloadButton.setOnClickListener {
            showDownloadOptions()
        }
    }
    
    private fun loadSeriesDetails() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                // Load series info from API
                val result = repository.getSeriesInfo(seriesId)
                
                if (result.isSuccess) {
                    val seriesInfo = result.getOrNull()
                    seriesInfo?.let { displaySeriesInfo(it) }
                } else {
                    Toast.makeText(
                        this@SeriesDetailActivity,
                        "Failed to load series details",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Check if series is in favorites
                checkFavoriteStatus()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@SeriesDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun displaySeriesInfo(seriesInfo: com.iptv.app.data.models.SeriesInfo) {
        val info = seriesInfo.info
        this.seasons = seriesInfo.seasons ?: emptyList()
        this.episodes = seriesInfo.episodes ?: emptyMap()
        
        Log.d("SeriesDetail", "Seasons count: ${seasons.size}")
        Log.d("SeriesDetail", "Episodes map keys: ${episodes.keys}")
        episodes.forEach { (seasonNum, episodeList) ->
            Log.d("SeriesDetail", "Season $seasonNum has ${episodeList.size} episodes")
        }
        
        // Set title in toolbar
        collapsingToolbar.title = info?.name ?: seriesTitle
        
        // Build metadata string
        val metadata = buildString {
            info?.releaseDate?.let { 
                append(it.take(4))
                if (seasons.isNotEmpty()) {
                    append("-")
                    // Find latest season year if available
                    append("Present")
                }
                append(" · ")
            }
            if (seasons.isNotEmpty()) {
                append("${seasons.size} ${getString(R.string.seasons)} · ")
            }
            info?.rating?.let { 
                append("★ ")
                append(it)
            }
        }
        metadataText.text = metadata
        
        // Load cover image
        Glide.with(this)
            .load(info?.cover ?: coverUrl)
            .placeholder(R.drawable.placeholder_movie)
            .error(R.drawable.placeholder_movie)
            .centerCrop()
            .into(coverImage)
        
        // Set plot
        info?.plot?.let {
            plotText.text = it
        }
        
        // Set genre
        info?.genre?.let {
            genreText.text = it
        }
        
        // Show info container
        infoContainer.visibility = View.VISIBLE
        
        // Update episodes
        if (seasons.isNotEmpty()) {
            Log.d("SeriesDetail", "Setting episodesContainer visibility to VISIBLE")
            adapter.updateSeasons(seasons, episodes)
            episodesContainer.visibility = View.VISIBLE
            Log.d("SeriesDetail", "episodesContainer visibility is now: ${episodesContainer.visibility}")
            Log.d("SeriesDetail", "episodesRecyclerView adapter item count: ${episodesRecyclerView.adapter?.itemCount}")
        } else {
            Log.d("SeriesDetail", "No seasons - keeping episodesContainer hidden")
        }
    }
    
    private suspend fun checkFavoriteStatus() {
        val result = repository.isFavorite(seriesId.toString(), "series")
        if (result.isSuccess) {
            isFavorite = result.getOrNull() ?: false
            updateFavoriteButton()
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
                    val result = repository.removeFromFavorites(seriesId.toString(), "series")
                    if (result.isSuccess) {
                        isFavorite = false
                        updateFavoriteButton()
                        Toast.makeText(
                            this@SeriesDetailActivity,
                            "Removed from My List",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val result = repository.addToFavorites(
                        contentId = seriesId.toString(),
                        contentType = "series",
                        contentName = seriesTitle,
                        posterUrl = coverUrl,
                        rating = metadataText.text.toString(),
                        categoryName = ""
                    )
                    if (result.isSuccess) {
                        isFavorite = true
                        updateFavoriteButton()
                        Toast.makeText(
                            this@SeriesDetailActivity,
                            "Added to My List",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SeriesDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun playEpisode(episode: Episode, seasonNumber: Int) {
        val server = credentialsManager.getServer()
        val username = credentialsManager.getUsername()
        val password = credentialsManager.getPassword()
        
        val streamUrl = StreamUrlBuilder.buildSeriesUrl(
            server = server,
            username = username,
            password = password,
            episodeId = episode.id,
            extension = episode.containerExtension
        )
        
        val episodeTitle = "S${seasonNumber}E${episode.episodeNum}: ${episode.title}"
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("TITLE", episodeTitle)
            putExtra("CONTENT_ID", episode.id)
            putExtra("CONTENT_TYPE", "series")
            putExtra("POSTER_URL", coverUrl)
            putExtra("RESUME_POSITION", 0L) // TODO: Load from watch history
        }
        startActivity(intent)
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        infoContainer.visibility = if (show) View.GONE else View.VISIBLE
        // Don't hide episodesContainer here - it's managed in displaySeriesInfo()
    }
    
    private fun showDownloadOptions() {
        val options = mutableListOf<String>()
        options.add("Download All Seasons")
        
        // Add individual season options
        seasons.forEach { season ->
            val episodeCount = episodes[season.seasonNumber.toString()]?.size ?: 0
            if (episodeCount > 0) {
                options.add("Download ${season.name} ($episodeCount episodes)")
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Download Options")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    downloadAllSeasons()
                } else {
                    val seasonIndex = which - 1
                    if (seasonIndex < seasons.size) {
                        downloadSeason(seasons[seasonIndex])
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun downloadAllSeasons() {
        lifecycleScope.launch {
            try {
                var totalEpisodes = 0
                seasons.forEach { season ->
                    val seasonEpisodes = episodes[season.seasonNumber.toString()] ?: emptyList()
                    totalEpisodes += seasonEpisodes.size
                    if (seasonEpisodes.isNotEmpty()) {
                        downloadSeasonInternal(season, showToast = false)
                    }
                }
                
                Toast.makeText(
                    this@SeriesDetailActivity,
                    "Started downloading all seasons ($totalEpisodes episodes)",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@SeriesDetailActivity,
                    "Failed to start downloads: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun downloadSeason(season: com.iptv.app.data.models.Season) {
        lifecycleScope.launch {
            downloadSeasonInternal(season, showToast = true)
        }
    }
    
    private suspend fun downloadSeasonInternal(season: com.iptv.app.data.models.Season, showToast: Boolean) {
        val seasonEpisodes = episodes[season.seasonNumber.toString()] ?: emptyList()
        
        if (seasonEpisodes.isEmpty()) {
            if (showToast) {
                Toast.makeText(
                    this@SeriesDetailActivity,
                    "No episodes found for ${season.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        
        if (showToast) {
            Toast.makeText(
                this@SeriesDetailActivity,
                "Downloading ${season.name} (${seasonEpisodes.size} episodes)...",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Download all episodes in the season
        seasonEpisodes.forEach { episode ->
            downloadEpisodeInternal(episode, season.seasonNumber)
        }
    }
    
    private fun showEpisodeDownloadDialog(episode: Episode, seasonNumber: Int) {
        val episodeTitle = "S${seasonNumber}E${episode.episodeNum}: ${episode.title}"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Download Episode")
            .setMessage("Download $episodeTitle?")
            .setPositiveButton("Download") { _, _ ->
                downloadEpisode(episode, seasonNumber)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun downloadEpisode(episode: Episode, seasonNumber: Int) {
        lifecycleScope.launch {
            try {
                downloadEpisodeInternal(episode, seasonNumber)
                
                val episodeTitle = "S${seasonNumber}E${episode.episodeNum}: ${episode.title}"
                Toast.makeText(
                    this@SeriesDetailActivity,
                    "Started downloading: $episodeTitle",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@SeriesDetailActivity,
                    "Failed to download episode: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private suspend fun downloadEpisodeInternal(episode: Episode, seasonNumber: Int) {
        val server = credentialsManager.getServer()
        val username = credentialsManager.getUsername()
        val password = credentialsManager.getPassword()
        
        val streamUrl = StreamUrlBuilder.buildSeriesUrl(
            server = server,
            username = username,
            password = password,
            episodeId = episode.id,
            extension = episode.containerExtension
        )
        
        val episodeTitle = "S${seasonNumber}E${episode.episodeNum}: ${episode.title}"
        val contentId = "series_${seriesId}_s${seasonNumber}e${episode.episodeNum}"
        
        Log.d("SeriesDetail", "Starting download for $episodeTitle (ID: $contentId)")
        
        // Add to download queue using DownloadRepository
        downloadRepository.startDownload(
            contentId = contentId,
            contentType = "series",
            contentName = seriesTitle,
            streamUrl = streamUrl,
            posterUrl = coverUrl,
            episodeName = episodeTitle,
            seasonNumber = seasonNumber,
            episodeNumber = episode.episodeNum
        )
    }
}
