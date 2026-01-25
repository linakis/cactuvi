package com.cactuvi.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cactuvi.app.R
import com.cactuvi.app.data.repository.ContentRepository
import com.cactuvi.app.services.BackgroundSyncWorker
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.utils.PreferencesManager
import com.cactuvi.app.utils.SyncPreferencesManager
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var modernToolbar: ModernToolbar
    private lateinit var repository: ContentRepository
    private lateinit var syncPrefs: SyncPreferencesManager
    private lateinit var prefsManager: PreferencesManager

    private lateinit var lastSyncText: TextView
    private lateinit var syncEnabledSwitch: SwitchMaterial
    private lateinit var wifiOnlySwitch: SwitchMaterial
    private lateinit var syncIntervalGroup: RadioGroup
    private lateinit var vpnWarningSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        repository = ContentRepository.getInstance(this)
        syncPrefs = SyncPreferencesManager.getInstance(this)
        prefsManager = PreferencesManager.getInstance(this)

        setupToolbar()
        setupVpnSettings()
        setupSyncSettings()
        setupClickListeners()
    }

    private fun setupToolbar() {
        modernToolbar = findViewById(R.id.modernToolbar)
        modernToolbar.onBackClick = { finish() }
    }

    private fun setupVpnSettings() {
        vpnWarningSwitch = findViewById(R.id.vpnWarningSwitch)

        // Load current setting
        vpnWarningSwitch.isChecked = prefsManager.isVpnWarningEnabled()

        // Setup listener
        vpnWarningSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setVpnWarningEnabled(isChecked)
            Toast.makeText(
                    this,
                    if (isChecked) "VPN warning enabled" else "VPN warning disabled",
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    private fun setupClickListeners() {
        // Sync Now
        findViewById<View>(R.id.syncNowCard).setOnClickListener { triggerImmediateSync() }

        // Clear Cache
        findViewById<View>(R.id.clearCacheCard).setOnClickListener { showClearCacheDialog() }

        // Clear Watch History
        findViewById<View>(R.id.clearHistoryCard).setOnClickListener { showClearHistoryDialog() }

        // Manage Sources
        findViewById<View>(R.id.manageSourcesCard).setOnClickListener {
            startActivity(Intent(this, ManageSourcesActivity::class.java))
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
            .setMessage(
                "This will remove all cached data. You'll need an internet connection to reload content. Continue?",
            )
            .setPositiveButton("Clear") { _, _ -> clearCache() }
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
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } else {
                Toast.makeText(
                        this@SettingsActivity,
                        "Failed to clear cache",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Watch History")
            .setMessage("This will remove all watch progress. This cannot be undone. Continue?")
            .setPositiveButton("Clear") { _, _ -> clearHistory() }
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
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } else {
                Toast.makeText(
                        this@SettingsActivity,
                        "Failed to clear watch history",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }
    }

    private fun setupSyncSettings() {
        // Initialize views
        lastSyncText = findViewById(R.id.lastSyncText)
        syncEnabledSwitch = findViewById(R.id.syncEnabledSwitch)
        wifiOnlySwitch = findViewById(R.id.wifiOnlySwitch)
        syncIntervalGroup = findViewById(R.id.syncIntervalGroup)

        // Load current settings
        syncEnabledSwitch.isChecked = syncPrefs.isSyncEnabled
        wifiOnlySwitch.isChecked = syncPrefs.isWifiOnly

        when (syncPrefs.syncIntervalHours) {
            6L -> syncIntervalGroup.check(R.id.interval6h)
            12L -> syncIntervalGroup.check(R.id.interval12h)
            24L -> syncIntervalGroup.check(R.id.interval24h)
        }

        updateLastSyncText()

        // Setup listeners
        syncEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            syncPrefs.isSyncEnabled = isChecked
            BackgroundSyncWorker.schedule(this)
            Toast.makeText(
                    this,
                    if (isChecked) "Background sync enabled" else "Background sync disabled",
                    Toast.LENGTH_SHORT,
                )
                .show()
        }

        wifiOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            syncPrefs.isWifiOnly = isChecked
            BackgroundSyncWorker.schedule(this)
        }

        syncIntervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val interval =
                when (checkedId) {
                    R.id.interval6h -> 6L
                    R.id.interval12h -> 12L
                    R.id.interval24h -> 24L
                    else -> 6L
                }
            syncPrefs.syncIntervalHours = interval
            BackgroundSyncWorker.schedule(this)
        }
    }

    private fun updateLastSyncText() {
        val lastSyncMovies = syncPrefs.lastSyncMovies
        val lastSyncSeries = syncPrefs.lastSyncSeries
        val lastSyncLive = syncPrefs.lastSyncLive

        val mostRecent = maxOf(lastSyncMovies, lastSyncSeries, lastSyncLive)

        if (mostRecent == 0L) {
            lastSyncText.text = "Last synced: Never"
        } else {
            val timeDiff = System.currentTimeMillis() - mostRecent
            val timeStr = formatTimeDiff(timeDiff)
            lastSyncText.text = "Last synced: $timeStr ago"
        }
    }

    private fun formatTimeDiff(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""}"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
            minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""}"
            else -> "Just now"
        }
    }

    private fun triggerImmediateSync() {
        BackgroundSyncWorker.syncNow(this)
        Toast.makeText(this, "Sync started...", Toast.LENGTH_SHORT).show()

        // Update UI after a short delay
        lastSyncText.postDelayed({ updateLastSyncText() }, 2000)
    }
}
