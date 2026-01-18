package com.iptv.app.ui.live

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iptv.app.R

class LiveTvActivity : AppCompatActivity() {
    
    private lateinit var fragment: LiveTvFragment
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container)
        
        // Load LiveTvFragment
        if (savedInstanceState == null) {
            fragment = LiveTvFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Let fragment handle back press first
        val handled = if (::fragment.isInitialized) {
            fragment.onBackPressed()
        } else {
            false
        }
        
        // If fragment didn't handle it (at top level), finish activity
        if (!handled) {
            finish()
        }
    }
}
