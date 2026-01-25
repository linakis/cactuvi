package com.cactuvi.app.data.models

data class ContentFilterSettings(
    // Grouping settings per content type
    val moviesGroupingEnabled: Boolean = true,
    val moviesGroupingSeparator: String = "-",
    val seriesGroupingEnabled: Boolean = true,
    val seriesGroupingSeparator: String = "FIRST_WORD",
    val liveGroupingEnabled: Boolean = true,
    val liveGroupingSeparator: String = "|",

    // Folder visibility settings
    val moviesFilterMode: FilterMode = FilterMode.BLACKLIST,
    val moviesHiddenItems: Set<String> = emptySet(),
    val seriesFilterMode: FilterMode = FilterMode.BLACKLIST,
    val seriesHiddenItems: Set<String> = emptySet(),
    val liveFilterMode: FilterMode = FilterMode.BLACKLIST,
    val liveHiddenItems: Set<String> = emptySet(),
) {
    enum class ContentType {
        MOVIES,
        SERIES,
        LIVE_TV,
    }

    enum class FilterMode {
        BLACKLIST, // Hide selected items
        WHITELIST, // Show only selected items
    }
}
