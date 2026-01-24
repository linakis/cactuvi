package com.cactuvi.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cactuvi.app.data.models.ContentFilterSettings

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
        private const val KEY_MOVIES_HIDDEN_GROUPS = "movies_hidden_groups"
        private const val KEY_MOVIES_HIDDEN_CATEGORIES = "movies_hidden_categories"
        
        private const val KEY_SERIES_FILTER_MODE = "series_filter_mode"
        private const val KEY_SERIES_HIDDEN_ITEMS = "series_hidden_items"
        private const val KEY_SERIES_HIDDEN_GROUPS = "series_hidden_groups"
        private const val KEY_SERIES_HIDDEN_CATEGORIES = "series_hidden_categories"
        
        private const val KEY_LIVE_FILTER_MODE = "live_filter_mode"
        private const val KEY_LIVE_HIDDEN_ITEMS = "live_hidden_items"
        private const val KEY_LIVE_HIDDEN_GROUPS = "live_hidden_groups"
        private const val KEY_LIVE_HIDDEN_CATEGORIES = "live_hidden_categories"
        
        // VPN warning key
        private const val KEY_VPN_WARNING_ENABLED = "vpn_warning_enabled"
        
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
    fun getMoviesFilterMode(): ContentFilterSettings.FilterMode {
        val mode = prefs.getString(KEY_MOVIES_FILTER_MODE, ContentFilterSettings.FilterMode.BLACKLIST.name) 
            ?: ContentFilterSettings.FilterMode.BLACKLIST.name
        return try {
            ContentFilterSettings.FilterMode.valueOf(mode)
        } catch (e: IllegalArgumentException) {
            ContentFilterSettings.FilterMode.BLACKLIST
        }
    }
    
    fun setMoviesFilterMode(mode: ContentFilterSettings.FilterMode) {
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
    fun getSeriesFilterMode(): ContentFilterSettings.FilterMode {
        val mode = prefs.getString(KEY_SERIES_FILTER_MODE, ContentFilterSettings.FilterMode.BLACKLIST.name) 
            ?: ContentFilterSettings.FilterMode.BLACKLIST.name
        return try {
            ContentFilterSettings.FilterMode.valueOf(mode)
        } catch (e: IllegalArgumentException) {
            ContentFilterSettings.FilterMode.BLACKLIST
        }
    }
    
    fun setSeriesFilterMode(mode: ContentFilterSettings.FilterMode) {
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
    fun getLiveFilterMode(): ContentFilterSettings.FilterMode {
        val mode = prefs.getString(KEY_LIVE_FILTER_MODE, ContentFilterSettings.FilterMode.BLACKLIST.name) 
            ?: ContentFilterSettings.FilterMode.BLACKLIST.name
        return try {
            ContentFilterSettings.FilterMode.valueOf(mode)
        } catch (e: IllegalArgumentException) {
            ContentFilterSettings.FilterMode.BLACKLIST
        }
    }
    
    fun setLiveFilterMode(mode: ContentFilterSettings.FilterMode) {
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
            ContentFilterSettings.FilterMode.BLACKLIST -> itemName in hiddenItems
            ContentFilterSettings.FilterMode.WHITELIST -> itemName !in hiddenItems
        }
    }
    
    // Generic wrapper methods for use with ContentType enum
    fun isGroupingEnabled(contentType: ContentFilterSettings.ContentType): Boolean {
        return when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> isMoviesGroupingEnabled()
            ContentFilterSettings.ContentType.SERIES -> isSeriesGroupingEnabled()
            ContentFilterSettings.ContentType.LIVE_TV -> isLiveGroupingEnabled()
        }
    }
    
    fun setGroupingEnabled(contentType: ContentFilterSettings.ContentType, enabled: Boolean) {
        when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> setMoviesGroupingEnabled(enabled)
            ContentFilterSettings.ContentType.SERIES -> setSeriesGroupingEnabled(enabled)
            ContentFilterSettings.ContentType.LIVE_TV -> setLiveGroupingEnabled(enabled)
        }
    }
    
    fun getCustomSeparator(contentType: ContentFilterSettings.ContentType): String {
        return when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> getMoviesGroupingSeparator()
            ContentFilterSettings.ContentType.SERIES -> getSeriesGroupingSeparator()
            ContentFilterSettings.ContentType.LIVE_TV -> getLiveGroupingSeparator()
        }
    }
    
    fun setCustomSeparator(contentType: ContentFilterSettings.ContentType, separator: String) {
        when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> setMoviesGroupingSeparator(separator)
            ContentFilterSettings.ContentType.SERIES -> setSeriesGroupingSeparator(separator)
            ContentFilterSettings.ContentType.LIVE_TV -> setLiveGroupingSeparator(separator)
        }
    }
    
    fun getFilterMode(contentType: ContentFilterSettings.ContentType): ContentFilterSettings.FilterMode {
        return when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> getMoviesFilterMode()
            ContentFilterSettings.ContentType.SERIES -> getSeriesFilterMode()
            ContentFilterSettings.ContentType.LIVE_TV -> getLiveFilterMode()
        }
    }
    
    fun setFilterMode(contentType: ContentFilterSettings.ContentType, mode: ContentFilterSettings.FilterMode) {
        when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> setMoviesFilterMode(mode)
            ContentFilterSettings.ContentType.SERIES -> setSeriesFilterMode(mode)
            ContentFilterSettings.ContentType.LIVE_TV -> setLiveFilterMode(mode)
        }
    }
    
    fun getHiddenItems(contentType: ContentFilterSettings.ContentType): Set<String> {
        return when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> getMoviesHiddenItems()
            ContentFilterSettings.ContentType.SERIES -> getSeriesHiddenItems()
            ContentFilterSettings.ContentType.LIVE_TV -> getLiveHiddenItems()
        }
    }
    
    fun setHiddenItems(contentType: ContentFilterSettings.ContentType, items: Set<String>) {
        when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> setMoviesHiddenItems(items)
            ContentFilterSettings.ContentType.SERIES -> setSeriesHiddenItems(items)
            ContentFilterSettings.ContentType.LIVE_TV -> setLiveHiddenItems(items)
        }
    }
    
    // ========== HIERARCHICAL FILTERING (Groups + Categories) ==========
    
    // Movies hierarchical filtering
    fun getMoviesHiddenGroups(): Set<String> {
        val json = prefs.getString(KEY_MOVIES_HIDDEN_GROUPS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setMoviesHiddenGroups(groups: Set<String>) {
        val json = gson.toJson(groups)
        prefs.edit().putString(KEY_MOVIES_HIDDEN_GROUPS, json).apply()
    }
    
    fun getMoviesHiddenCategories(): Set<String> {
        val json = prefs.getString(KEY_MOVIES_HIDDEN_CATEGORIES, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setMoviesHiddenCategories(categories: Set<String>) {
        val json = gson.toJson(categories)
        prefs.edit().putString(KEY_MOVIES_HIDDEN_CATEGORIES, json).apply()
    }
    
    // Series hierarchical filtering
    fun getSeriesHiddenGroups(): Set<String> {
        val json = prefs.getString(KEY_SERIES_HIDDEN_GROUPS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setSeriesHiddenGroups(groups: Set<String>) {
        val json = gson.toJson(groups)
        prefs.edit().putString(KEY_SERIES_HIDDEN_GROUPS, json).apply()
    }
    
    fun getSeriesHiddenCategories(): Set<String> {
        val json = prefs.getString(KEY_SERIES_HIDDEN_CATEGORIES, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setSeriesHiddenCategories(categories: Set<String>) {
        val json = gson.toJson(categories)
        prefs.edit().putString(KEY_SERIES_HIDDEN_CATEGORIES, json).apply()
    }
    
    // Live TV hierarchical filtering
    fun getLiveHiddenGroups(): Set<String> {
        val json = prefs.getString(KEY_LIVE_HIDDEN_GROUPS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setLiveHiddenGroups(groups: Set<String>) {
        val json = gson.toJson(groups)
        prefs.edit().putString(KEY_LIVE_HIDDEN_GROUPS, json).apply()
    }
    
    fun getLiveHiddenCategories(): Set<String> {
        val json = prefs.getString(KEY_LIVE_HIDDEN_CATEGORIES, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setLiveHiddenCategories(categories: Set<String>) {
        val json = gson.toJson(categories)
        prefs.edit().putString(KEY_LIVE_HIDDEN_CATEGORIES, json).apply()
    }
    
    // Generic wrapper methods for hierarchical filtering
    fun getHiddenGroups(contentType: ContentFilterSettings.ContentType): Set<String> {
        return when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> getMoviesHiddenGroups()
            ContentFilterSettings.ContentType.SERIES -> getSeriesHiddenGroups()
            ContentFilterSettings.ContentType.LIVE_TV -> getLiveHiddenGroups()
        }
    }
    
    fun setHiddenGroups(contentType: ContentFilterSettings.ContentType, groups: Set<String>) {
        when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> setMoviesHiddenGroups(groups)
            ContentFilterSettings.ContentType.SERIES -> setSeriesHiddenGroups(groups)
            ContentFilterSettings.ContentType.LIVE_TV -> setLiveHiddenGroups(groups)
        }
    }
    
    fun getHiddenCategories(contentType: ContentFilterSettings.ContentType): Set<String> {
        return when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> getMoviesHiddenCategories()
            ContentFilterSettings.ContentType.SERIES -> getSeriesHiddenCategories()
            ContentFilterSettings.ContentType.LIVE_TV -> getLiveHiddenCategories()
        }
    }
    
    fun setHiddenCategories(contentType: ContentFilterSettings.ContentType, categories: Set<String>) {
        when (contentType) {
            ContentFilterSettings.ContentType.MOVIES -> setMoviesHiddenCategories(categories)
            ContentFilterSettings.ContentType.SERIES -> setSeriesHiddenCategories(categories)
            ContentFilterSettings.ContentType.LIVE_TV -> setLiveHiddenCategories(categories)
        }
    }
    
    // ========== VPN WARNING ==========
    
    /**
     * Check if VPN warning is enabled (default: false).
     * When enabled, app will warn user if VPN is not connected.
     */
    fun isVpnWarningEnabled(): Boolean {
        return prefs.getBoolean(KEY_VPN_WARNING_ENABLED, false)
    }
    
    /**
     * Enable or disable VPN warning.
     */
    fun setVpnWarningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_WARNING_ENABLED, enabled).apply()
    }
}

