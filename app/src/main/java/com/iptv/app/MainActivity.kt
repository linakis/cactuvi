package com.iptv.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iptv.app.ui.home.HomeActivity
import com.iptv.app.ui.playlist.AddPlaylistActivity
import com.iptv.app.utils.CredentialsManager

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val credentialsManager = CredentialsManager.getInstance(this)
        
        // Check if credentials are configured
        if (credentialsManager.isConfigured()) {
            // Go to Home screen
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
        } else {
            // Go to Add Playlist screen
            val intent = Intent(this, AddPlaylistActivity::class.java)
            startActivity(intent)
        }
        
        finish()
    }
}
