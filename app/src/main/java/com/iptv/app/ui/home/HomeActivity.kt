package com.iptv.app.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iptv.app.R
import com.iptv.app.ui.menu.HomeMenuFragment

class HomeActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_new)
        
        // Load home menu fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeMenuFragment())
                .commit()
        }
    }
}
