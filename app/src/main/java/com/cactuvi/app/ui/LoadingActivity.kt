package com.cactuvi.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.cactuvi.app.MainActivity
import com.cactuvi.app.R
import com.cactuvi.app.utils.SourceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Loading screen for first app launch.
 *
 * Strategy: Independent background loading for Movies/Series/Live
 * - No sources: Navigate to Add Source screen
 * - ANY cache exists: Navigate to MainActivity immediately (fragments handle loading)
 * - NO cache: Trigger background sync and navigate to MainActivity (fragments show loading UI)
 *
 * Each fragment (Movies/Series/Live) handles its own loading state independently.
 */
@AndroidEntryPoint
class LoadingActivity : AppCompatActivity() {

    @Inject lateinit var sourceManager: SourceManager

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var retryButton: Button

    /** Track whether initial source check is complete (for splash screen) */
    private var isCheckingSource = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen BEFORE super.onCreate() per Android 12+ API requirements
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Keep splash screen visible while checking for sources
        splashScreen.setKeepOnScreenCondition { isCheckingSource }

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
                val sources = sourceManager.getAllSources()

                // Source check complete - dismiss splash screen
                isCheckingSource = false

                if (sources.isEmpty()) {
                    // No sources configured - jump directly to add source screen
                    navigateToAddSource()
                    return@launch
                }

                // Navigate immediately - fragments will handle their own loading states
                // Background sync will load any missing content types
                navigateToMain()
            } catch (e: Exception) {
                isCheckingSource = false
                showError("Failed to check cache: ${e.message}")
            }
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
        val intent =
            Intent(
                this,
                com.cactuvi.app.ui.settings.AddEditSourceActivity::class.java,
            )
        startActivityForResult(intent, REQUEST_ADD_SOURCE)
    }

    private fun showNoSourcesError() {
        progressBar.visibility = View.GONE
        statusText.text = "No Sources Configured"
        detailText.text = "Please add an IPTV source to continue"
        retryButton.text = "Add Source"
        retryButton.visibility = View.VISIBLE
        retryButton.requestFocus()

        retryButton.setOnClickListener { navigateToAddSource() }
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
