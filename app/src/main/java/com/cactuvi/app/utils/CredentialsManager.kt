package com.cactuvi.app.utils

import android.content.Context
import android.content.SharedPreferences

class CredentialsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "iptv_credentials",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_SERVER = "server"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_IS_CONFIGURED = "is_configured"
        
        @Volatile
        private var instance: CredentialsManager? = null
        
        fun getInstance(context: Context): CredentialsManager {
            return instance ?: synchronized(this) {
                instance ?: CredentialsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    fun saveCredentials(server: String, username: String, password: String) {
        prefs.edit().apply {
            putString(KEY_SERVER, server)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_IS_CONFIGURED, true)
            apply()
        }
    }
    
    fun getServer(): String {
        return prefs.getString(KEY_SERVER, "") ?: ""
    }
    
    fun getUsername(): String {
        return prefs.getString(KEY_USERNAME, "") ?: ""
    }
    
    fun getPassword(): String {
        return prefs.getString(KEY_PASSWORD, "") ?: ""
    }
    
    fun isConfigured(): Boolean {
        return prefs.getBoolean(KEY_IS_CONFIGURED, false)
    }
    
    fun clear() {
        prefs.edit().clear().apply()
    }
    
    data class Credentials(
        val server: String,
        val username: String,
        val password: String
    )
    
    fun getCredentials(): Credentials? {
        return if (isConfigured()) {
            Credentials(getServer(), getUsername(), getPassword())
        } else {
            null
        }
    }
}
