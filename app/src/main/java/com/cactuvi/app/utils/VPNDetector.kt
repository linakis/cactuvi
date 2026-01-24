package com.cactuvi.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utility class to detect if device is connected to a VPN.
 * Uses ConnectivityManager API with NetworkCapabilities.hasTransport(TRANSPORT_VPN).
 */
object VPNDetector {
    
    /**
     * Checks if device is currently connected to a VPN.
     * 
     * @param context Application or Activity context
     * @return true if VPN is active, false otherwise
     */
    fun isVpnActive(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            // Return false if any error occurs (graceful degradation)
            false
        }
    }
}
