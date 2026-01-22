package com.iptv.app.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iptv.app.IPTVApplication
import com.iptv.app.R
import com.iptv.app.ui.common.VPNWarningDialog
import com.iptv.app.ui.menu.HomeMenuFragment

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
        if (IPTVApplication.needsVpnWarning && !hasCheckedVpn) {
            hasCheckedVpn = true
            
            VPNWarningDialog.newInstance(
                onContinue = {
                    // User chose to continue - reset flag
                    IPTVApplication.needsVpnWarning = false
                },
                onCloseApp = {
                    // User chose to close app
                    finish()
                }
            ).show(supportFragmentManager, VPNWarningDialog.TAG)
        }
    }
}
