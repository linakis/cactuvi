package com.cactuvi.app.ui.downloads

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.cactuvi.app.R
import com.cactuvi.app.data.repository.DownloadRepository
import com.cactuvi.app.ui.common.ModernToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

@UnstableApi
class DownloadsActivity : AppCompatActivity() {

    private lateinit var modernToolbar: ModernToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var clearAllButton: TextView
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var pagerAdapter: DownloadsPagerAdapter

    private val tabTitles =
        arrayOf(
            R.string.tab_active,
            R.string.tab_completed,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        downloadRepository = DownloadRepository(this)

        initViews()
        setupTabs()
    }

    private fun initViews() {
        modernToolbar = findViewById(R.id.modernToolbar)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        clearAllButton = findViewById(R.id.clearAllButton)

        modernToolbar.onBackClick = { finish() }

        clearAllButton.setOnClickListener { showClearAllDialog() }
    }

    private fun setupTabs() {
        pagerAdapter = DownloadsPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = getString(tabTitles[position])
            }
            .attach()

        // Observe active downloads count for badge
        observeActiveDownloadsCount()
    }

    private fun observeActiveDownloadsCount() {
        lifecycleScope.launch {
            downloadRepository.getActiveDownloads().collect { downloads ->
                val activeTab = tabLayout.getTabAt(DownloadsPagerAdapter.TAB_ACTIVE)
                if (downloads.isNotEmpty()) {
                    activeTab?.orCreateBadge?.apply {
                        number = downloads.size
                        backgroundColor = getColor(R.color.brand_green)
                        badgeTextColor = getColor(R.color.text_primary)
                    }
                } else {
                    activeTab?.removeBadge()
                }
            }
        }
    }

    private fun showClearAllDialog() {
        val currentTab = viewPager.currentItem

        val title: String
        val message: String
        val action: () -> Unit

        if (currentTab == DownloadsPagerAdapter.TAB_ACTIVE) {
            title = getString(R.string.cancel_all_downloads)
            message = getString(R.string.cancel_all_downloads_message)
            action = { cancelAllActiveDownloads() }
        } else {
            title = getString(R.string.clear_all_downloads)
            message = getString(R.string.clear_all_downloads_message)
            action = { clearAllCompletedDownloads() }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun cancelAllActiveDownloads() {
        lifecycleScope.launch {
            try {
                // Get all active downloads and cancel them
                downloadRepository.getActiveDownloads().collect { downloads ->
                    downloads.forEach { download ->
                        downloadRepository.cancelDownload(download.contentId)
                    }
                    Toast.makeText(
                            this@DownloadsActivity,
                            R.string.all_downloads_cancelled,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                    return@collect
                }
            } catch (e: Exception) {
                Toast.makeText(
                        this@DownloadsActivity,
                        getString(R.string.cancel_failed, e.message),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }
    }

    private fun clearAllCompletedDownloads() {
        lifecycleScope.launch {
            try {
                downloadRepository.deleteAllDownloads()
                Toast.makeText(
                        this@DownloadsActivity,
                        R.string.all_downloads_deleted,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } catch (e: Exception) {
                Toast.makeText(
                        this@DownloadsActivity,
                        getString(R.string.delete_failed, e.message),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }
    }
}
