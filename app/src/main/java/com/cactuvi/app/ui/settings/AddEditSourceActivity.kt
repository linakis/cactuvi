package com.cactuvi.app.ui.settings

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cactuvi.app.R
import com.cactuvi.app.data.models.StreamSource
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.utils.SourceManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AddEditSourceActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE_ID = "source_id"
    }

    @Inject lateinit var sourceManager: SourceManager

    private lateinit var modernToolbar: ModernToolbar

    private lateinit var nicknameInputLayout: TextInputLayout
    private lateinit var serverInputLayout: TextInputLayout
    private lateinit var usernameInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout

    private lateinit var nicknameEditText: TextInputEditText
    private lateinit var serverEditText: TextInputEditText
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText

    private lateinit var saveButton: Button
    private lateinit var testConnectionButton: Button
    private lateinit var progressBar: ProgressBar

    private var editingSourceId: String? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_source)

        // Check if editing existing source
        editingSourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
        isEditMode = editingSourceId != null

        setupViews()
        setupToolbar()
        setupClickListeners()

        if (isEditMode) {
            loadSourceData()
        } else {
            // Hardcode test credentials for development
            prefillTestCredentials()
        }
    }

    private fun prefillTestCredentials() {
        serverEditText.setText("http://garlic82302.cdngold.me")
        usernameEditText.setText("2bd16b40497f")
        passwordEditText.setText("fc8edbab6b")
        nicknameEditText.setText("Test Server")
    }

    private fun setupViews() {
        modernToolbar = findViewById(R.id.modernToolbar)

        nicknameInputLayout = findViewById(R.id.nicknameInputLayout)
        serverInputLayout = findViewById(R.id.serverInputLayout)
        usernameInputLayout = findViewById(R.id.usernameInputLayout)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)

        nicknameEditText = findViewById(R.id.nicknameEditText)
        serverEditText = findViewById(R.id.serverEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)

        saveButton = findViewById(R.id.saveButton)
        testConnectionButton = findViewById(R.id.testConnectionButton)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        modernToolbar.title = if (isEditMode) "Edit Source" else "Add Source"
        modernToolbar.onBackClick = { finish() }
    }

    private fun setupClickListeners() {
        saveButton.setOnClickListener { saveSource() }

        testConnectionButton.setOnClickListener { testConnection() }
    }

    private fun loadSourceData() {
        lifecycleScope.launch {
            val sourceId = editingSourceId ?: return@launch
            val sources = withContext(Dispatchers.IO) { sourceManager.getAllSources() }

            val source = sources.find { it.id == sourceId }
            if (source != null) {
                nicknameEditText.setText(source.nickname)
                serverEditText.setText(source.server)
                usernameEditText.setText(source.username)
                passwordEditText.setText(source.password)
            } else {
                Toast.makeText(this@AddEditSourceActivity, "Source not found", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun saveSource() {
        // Clear previous errors
        nicknameInputLayout.error = null
        serverInputLayout.error = null
        usernameInputLayout.error = null
        passwordInputLayout.error = null

        // Get values
        val nickname = nicknameEditText.text?.toString()?.trim() ?: ""
        val server = serverEditText.text?.toString()?.trim() ?: ""
        val username = usernameEditText.text?.toString()?.trim() ?: ""
        val password = passwordEditText.text?.toString()?.trim() ?: ""

        // Validate
        var hasError = false

        if (nickname.isEmpty()) {
            nicknameInputLayout.error = "Nickname is required"
            hasError = true
        }

        if (server.isEmpty()) {
            serverInputLayout.error = "Server URL is required"
            hasError = true
        } else if (!isValidUrl(server)) {
            serverInputLayout.error = "Invalid URL format"
            hasError = true
        }

        if (username.isEmpty()) {
            usernameInputLayout.error = "Username is required"
            hasError = true
        }

        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            hasError = true
        }

        if (hasError) return

        // Save source
        showLoading(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (isEditMode) {
                        // Update existing source
                        val sourceId = editingSourceId ?: return@withContext
                        val sources = sourceManager.getAllSources()
                        val existingSource =
                            sources.find { it.id == sourceId } ?: return@withContext

                        val updatedSource =
                            existingSource.copy(
                                nickname = nickname,
                                server = server,
                                username = username,
                                password = password,
                            )

                        sourceManager.updateSource(updatedSource)
                    } else {
                        // Create new source
                        val newSource =
                            StreamSource(
                                id = UUID.randomUUID().toString(),
                                nickname = nickname,
                                server = server,
                                username = username,
                                password = password,
                                isActive = false,
                                isPrimary = false,
                                createdAt = System.currentTimeMillis(),
                                lastUsed = 0L,
                            )

                        sourceManager.addSource(newSource)

                        // Make new source active automatically
                        sourceManager.setActiveSource(newSource.id)
                    }
                }

                showLoading(false)

                Toast.makeText(
                        this@AddEditSourceActivity,
                        if (isEditMode) "Source updated" else "Source added",
                        Toast.LENGTH_SHORT,
                    )
                    .show()

                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                        this@AddEditSourceActivity,
                        "Failed to save source: ${e.message}",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }
    }

    private fun testConnection() {
        // Clear previous errors
        serverInputLayout.error = null

        val server = serverEditText.text?.toString()?.trim() ?: ""

        if (server.isEmpty()) {
            serverInputLayout.error = "Server URL is required"
            return
        }

        if (!isValidUrl(server)) {
            serverInputLayout.error = "Invalid URL format"
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            try {
                // Simple connection test - try to resolve URL
                withContext(Dispatchers.IO) {
                    val url = URL(server)
                    url.openConnection().connect()
                }

                showLoading(false)
                Toast.makeText(
                        this@AddEditSourceActivity,
                        "Connection successful",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                        this@AddEditSourceActivity,
                        "Connection failed: ${e.message}",
                        Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsed = URL(url)
            parsed.protocol in listOf("http", "https")
        } catch (e: Exception) {
            false
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        saveButton.isEnabled = !show
        testConnectionButton.isEnabled = !show
        nicknameEditText.isEnabled = !show
        serverEditText.isEnabled = !show
        usernameEditText.isEnabled = !show
        passwordEditText.isEnabled = !show
    }
}
