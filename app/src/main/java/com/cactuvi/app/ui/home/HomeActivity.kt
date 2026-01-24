package com.cactuvi.app.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cactuvi.app.Cactuvi
import com.cactuvi.app.R
import com.cactuvi.app.ui.common.VPNWarningDialog
import com.cactuvi.app.ui.menu.HomeMenuFragment

class HomeActivity : AppCompatActivity() {
    
    private var hasCheckedVpn = false
    
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
    
    override fun onResume() {
        super.onResume()
        
        // Check VPN warning flag from application
        if (Cactuvi.needsVpnWarning && !hasCheckedVpn) {
            hasCheckedVpn = true
            
            VPNWarningDialog.newInstance(
                onContinue = {
                    // User chose to continue - reset flag
                    Cactuvi.needsVpnWarning = false
                },
                onCloseApp = {
                    // User chose to close app
                    finish()
                }
            ).show(supportFragmentManager, VPNWarningDialog.TAG)
        }
    }
}
