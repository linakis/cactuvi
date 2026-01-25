package com.cactuvi.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets

/**
 * Manages MAC address spoofing for Xtream Codes API authentication.
 *
 * Many IPTV providers bind credentials to specific MAC addresses. This class allows you to
 * configure and use a fixed MAC address instead of the device's actual MAC address.
 */
class MacAddressManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            "iptv_settings",
            Context.MODE_PRIVATE,
        )

    companion object {
        private const val KEY_MAC_ADDRESS = "mac_address"
        private const val KEY_DEVICE_KEY = "device_key"

        // Default MAC address format (you should change this to your registered MAC)
        private const val DEFAULT_MAC = "00:1A:79:XX:XX:XX"

        @Volatile private var instance: MacAddressManager? = null

        fun getInstance(context: Context): MacAddressManager {
            return instance
                ?: synchronized(this) {
                    instance ?: MacAddressManager(context.applicationContext).also { instance = it }
                }
        }
    }

    /**
     * Get the configured MAC address to use for API calls. Returns the user-configured MAC or
     * default if not set.
     */
    fun getMacAddress(): String {
        return prefs.getString(KEY_MAC_ADDRESS, DEFAULT_MAC) ?: DEFAULT_MAC
    }

    /**
     * Set the MAC address to use for API authentication. This should be the MAC address registered
     * with your IPTV provider.
     *
     * @param macAddress MAC address in format "00:1A:79:XX:XX:XX"
     */
    fun setMacAddress(macAddress: String) {
        // Validate MAC address format
        val macPattern = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        require(macPattern.matches(macAddress)) {
            "Invalid MAC address format. Use format: 00:1A:79:XX:XX:XX"
        }

        prefs.edit().putString(KEY_MAC_ADDRESS, macAddress.uppercase()).apply()
    }

    /** Get the configured device key (if any). Some providers use additional device identifiers. */
    fun getDeviceKey(): String {
        return prefs.getString(KEY_DEVICE_KEY, "") ?: ""
    }

    /** Set the device key for additional identification. */
    fun setDeviceKey(deviceKey: String) {
        prefs.edit().putString(KEY_DEVICE_KEY, deviceKey).apply()
    }

    /**
     * Get MAC address encoded as Base64 (for certain API calls). IP-Pro encodes MAC in Base64 for
     * some requests.
     */
    fun getMacAddressBase64(): String {
        val mac = getMacAddress()
        return Base64.encodeToString(
                mac.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP,
            )
            .trim()
    }

    /**
     * Generate a fake Android ID based on MAC address. Some providers check Android ID consistency
     * with MAC.
     */
    fun getFakeAndroidId(): String {
        // Generate consistent fake Android ID from MAC address
        val mac = getMacAddress().replace(":", "")
        return mac.padEnd(16, '0').substring(0, 16)
    }

    /** Clear all stored MAC/device information. */
    fun clear() {
        prefs.edit().remove(KEY_MAC_ADDRESS).remove(KEY_DEVICE_KEY).apply()
    }

    /** Check if MAC address is configured (not using default). */
    fun isConfigured(): Boolean {
        val mac = getMacAddress()
        return mac != DEFAULT_MAC && mac.isNotEmpty()
    }
}
