package com.cactuvi.app.ui.movies

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.cactuvi.app.R

class MoviesActivity : AppCompatActivity() {
    
    private lateinit var fragment: MoviesFragment
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container)
        
        // Load MoviesFragment
        if (savedInstanceState == null) {
            fragment = MoviesFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
        
        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
        })
    }
}
