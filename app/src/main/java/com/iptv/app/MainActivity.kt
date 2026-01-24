package com.iptv.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iptv.app.ui.home.HomeActivity
import com.iptv.app.utils.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            val sourceManager = SourceManager.getInstance(this@MainActivity)
            val sources = withContext(Dispatchers.IO) {
                sourceManager.getAllSources()
            }
            
            // Navigate to HomeActivity - sources are already configured at this point
            // (LoadingActivity handles the case when no sources exist)
            val intent = Intent(this@MainActivity, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
