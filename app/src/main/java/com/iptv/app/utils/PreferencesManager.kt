package com.iptv.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.iptv.app.data.models.FilterMode

class PreferencesManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "iptv_settings",
        Context.MODE_PRIVATE
    )
    
    private val gson = Gson()
    
    companion object {
        // Grouping settings keys
        private const val KEY_MOVIES_GROUPING_ENABLED = "movies_grouping_enabled"
        private const val KEY_MOVIES_GROUPING_SEPARATOR = "movies_grouping_separator"
        
        private const val KEY_SERIES_GROUPING_ENABLED = "series_grouping_enabled"
        private const val KEY_SERIES_GROUPING_SEPARATOR = "series_grouping_separator"
        
        private const val KEY_LIVE_GROUPING_ENABLED = "live_grouping_enabled"
        private const val KEY_LIVE_GROUPING_SEPARATOR = "live_grouping_separator"
        
        // Filter mode keys
        private const val KEY_MOVIES_FILTER_MODE = "movies_filter_mode"
        private const val KEY_MOVIES_HIDDEN_ITEMS = "movies_hidden_items"
        
        private const val KEY_SERIES_FILTER_MODE = "series_filter_mode"
        private const val KEY_SERIES_HIDDEN_ITEMS = "series_hidden_items"
        
        private const val KEY_LIVE_FILTER_MODE = "live_filter_mode"
        private const val KEY_LIVE_HIDDEN_ITEMS = "live_hidden_items"
        
        @Volatile
        private var instance: PreferencesManager? = null
        
        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    // Movies grouping settings
    fun isMoviesGroupingEnabled(): Boolean {
        return prefs.getBoolean(KEY_MOVIES_GROUPING_ENABLED, true)
    }
    
    fun setMoviesGroupingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOVIES_GROUPING_ENABLED, enabled).apply()
    }
    
    fun getMoviesGroupingSeparator(): String {
        return prefs.getString(KEY_MOVIES_GROUPING_SEPARATOR, "-") ?: "-"
    }
    
    fun setMoviesGroupingSeparator(separator: String) {
        prefs.edit().putString(KEY_MOVIES_GROUPING_SEPARATOR, separator).apply()
    }
    
    // Series grouping settings
    fun isSeriesGroupingEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERIES_GROUPING_ENABLED, true)
    }
    
    fun setSeriesGroupingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERIES_GROUPING_ENABLED, enabled).apply()
    }
    
    fun getSeriesGroupingSeparator(): String {
        return prefs.getString(KEY_SERIES_GROUPING_SEPARATOR, "FIRST_WORD") ?: "FIRST_WORD"
    }
    
    fun setSeriesGroupingSeparator(separator: String) {
        prefs.edit().putString(KEY_SERIES_GROUPING_SEPARATOR, separator).apply()
    }
    
    // Live TV grouping settings
    fun isLiveGroupingEnabled(): Boolean {
        return prefs.getBoolean(KEY_LIVE_GROUPING_ENABLED, true)
    }
    
    fun setLiveGroupingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_GROUPING_ENABLED, enabled).apply()
    }
    
    fun getLiveGroupingSeparator(): String {
        return prefs.getString(KEY_LIVE_GROUPING_SEPARATOR, "|") ?: "|"
    }
    
    fun setLiveGroupingSeparator(separator: String) {
        prefs.edit().putString(KEY_LIVE_GROUPING_SEPARATOR, separator).apply()
    }
    
    // Movies filter settings
    fun getMoviesFilterMode(): FilterMode {
        val mode = prefs.getString(KEY_MOVIES_FILTER_MODE, FilterMode.BLACKLIST.name) 
            ?: FilterMode.BLACKLIST.name
        return try {
            FilterMode.valueOf(mode)
        } catch (e: IllegalArgumentException) {
            FilterMode.BLACKLIST
        }
    }
    
    fun setMoviesFilterMode(mode: FilterMode) {
        prefs.edit().putString(KEY_MOVIES_FILTER_MODE, mode.name).apply()
    }
    
    fun getMoviesHiddenItems(): Set<String> {
        val json = prefs.getString(KEY_MOVIES_HIDDEN_ITEMS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setMoviesHiddenItems(items: Set<String>) {
        val json = gson.toJson(items)
        prefs.edit().putString(KEY_MOVIES_HIDDEN_ITEMS, json).apply()
    }
    
    // Series filter settings
    fun getSeriesFilterMode(): FilterMode {
        val mode = prefs.getString(KEY_SERIES_FILTER_MODE, FilterMode.BLACKLIST.name) 
            ?: FilterMode.BLACKLIST.name
        return try {
            FilterMode.valueOf(mode)
        } catch (e: IllegalArgumentException) {
            FilterMode.BLACKLIST
        }
    }
    
    fun setSeriesFilterMode(mode: FilterMode) {
        prefs.edit().putString(KEY_SERIES_FILTER_MODE, mode.name).apply()
    }
    
    fun getSeriesHiddenItems(): Set<String> {
        val json = prefs.getString(KEY_SERIES_HIDDEN_ITEMS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setSeriesHiddenItems(items: Set<String>) {
        val json = gson.toJson(items)
        prefs.edit().putString(KEY_SERIES_HIDDEN_ITEMS, json).apply()
    }
    
    // Live TV filter settings
    fun getLiveFilterMode(): FilterMode {
        val mode = prefs.getString(KEY_LIVE_FILTER_MODE, FilterMode.BLACKLIST.name) 
            ?: FilterMode.BLACKLIST.name
        return try {
            FilterMode.valueOf(mode)
        } catch (e: IllegalArgumentException) {
            FilterMode.BLACKLIST
        }
    }
    
    fun setLiveFilterMode(mode: FilterMode) {
        prefs.edit().putString(KEY_LIVE_FILTER_MODE, mode.name).apply()
    }
    
    fun getLiveHiddenItems(): Set<String> {
        val json = prefs.getString(KEY_LIVE_HIDDEN_ITEMS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setLiveHiddenItems(items: Set<String>) {
        val json = gson.toJson(items)
        prefs.edit().putString(KEY_LIVE_HIDDEN_ITEMS, json).apply()
    }
    
    // Helper method to check if an item is hidden based on current filter mode
    fun isItemHidden(contentType: String, itemName: String): Boolean {
        val (filterMode, hiddenItems) = when (contentType) {
            "movies" -> getMoviesFilterMode() to getMoviesHiddenItems()
            "series" -> getSeriesFilterMode() to getSeriesHiddenItems()
            "live" -> getLiveFilterMode() to getLiveHiddenItems()
            else -> return false
        }
        
        return when (filterMode) {
            FilterMode.BLACKLIST -> itemName in hiddenItems
            FilterMode.WHITELIST -> itemName !in hiddenItems
        }
    }
}
