package com.iptv.app.ui.playlist

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.iptv.app.R
import com.iptv.app.data.api.ApiClient
import com.iptv.app.ui.home.HomeActivity
import com.iptv.app.utils.CredentialsManager
import com.iptv.app.utils.SourceManager
import kotlinx.coroutines.launch

class AddPlaylistActivity : AppCompatActivity() {
    
    private lateinit var serverInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var testButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var progressBar: View
    private lateinit var statusText: android.widget.TextView
    
    private lateinit var credentialsManager: CredentialsManager
    private var testSuccessful = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_playlist)
        
        credentialsManager = CredentialsManager.getInstance(this)
        
        initViews()
        setupListeners()
        loadExistingCredentials()
    }
    
    private fun initViews() {
        serverInput = findViewById(R.id.serverInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        testButton = findViewById(R.id.testButton)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
    }
    
    private fun setupListeners() {
        testButton.setOnClickListener {
            testConnection()
        }
        
        saveButton.setOnClickListener {
            saveCredentials()
        }
        
        cancelButton.setOnClickListener {
            finish()
        }
    }
    
    private fun loadExistingCredentials() {
        if (credentialsManager.isConfigured()) {
            serverInput.setText(credentialsManager.getServer())
            usernameInput.setText(credentialsManager.getUsername())
            passwordInput.setText(credentialsManager.getPassword())
        }
    }
    
    private fun testConnection() {
        val server = serverInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        
        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.please_fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Ensure server URL is properly formatted
        val formattedServer = if (!server.startsWith("http://") && !server.startsWith("https://")) {
            "http://$server"
        } else {
            server
        }.trimEnd('/')
        
        showLoading(true)
        statusText.visibility = View.GONE
        testSuccessful = false
        
        lifecycleScope.launch {
            try {
                val apiService = ApiClient.createService(formattedServer)
                val response = apiService.authenticate(username, password)
                
                // Check if authentication was successful
                if (response.userInfo.auth == 1) {
                    testSuccessful = true
                    showStatus(getString(R.string.connection_successful), true)
                    saveButton.isEnabled = true
                } else {
                    showStatus("Authentication failed: ${response.userInfo.message ?: "Unknown error"}", false)
                    saveButton.isEnabled = false
                }
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("513") == true -> "Server rejected request (513). This may be due to rate limiting, server overload, or malformed request. Please try again later."
                    e.message?.contains("HTTP") == true -> "HTTP Error: ${e.message}"
                    e.message?.contains("timeout") == true -> "Connection timeout. Please check your internet connection and server URL."
                    e.message?.contains("UnknownHost") == true -> "Server not found. Please check the server URL."
                    else -> getString(R.string.connection_failed, e.message)
                }
                showStatus(errorMessage, false)
                saveButton.isEnabled = false
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun saveCredentials() {
        if (!testSuccessful) {
            Toast.makeText(this, "Please test connection first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val server = serverInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        
        val formattedServer = if (!server.startsWith("http://") && !server.startsWith("https://")) {
            "http://$server"
        } else {
            server
        }.trimEnd('/')
        
        credentialsManager.saveCredentials(formattedServer, username, password)
        
        Toast.makeText(this, "Credentials saved!", Toast.LENGTH_SHORT).show()
        
        // Navigate to home screen
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        testButton.isEnabled = !show
    }
    
    private fun showStatus(message: String, success: Boolean) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
        statusText.setTextColor(
            if (success) getColor(R.color.accent) else getColor(R.color.error)
        )
    }
}
