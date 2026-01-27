package com.cactuvi.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.cactuvi.app.data.models.ContentFilterSettings
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PreferencesManager.
 *
 * Tests cover:
 * - Grouping enabled defaults
 * - Grouping separator defaults
 * - Set and get grouping settings
 * - Filter mode defaults (BLACKLIST)
 * - Hidden items persistence (JSON)
 * - isItemHidden blacklist mode
 * - isItemHidden whitelist mode
 * - VPN warning settings
 * - Hidden groups and categories persistence
 */
class PreferencesManagerTest {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    // Capture slots for verification
    private val stringSlot = slot<String>()
    private val booleanSlot = slot<Boolean>()

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        // Wire up SharedPreferences
        every { mockContext.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE) } returns
            mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.apply() } just Runs

        preferencesManager = PreferencesManager(mockContext)
    }

    // ========== MOVIES GROUPING DEFAULTS ==========

    @Test
    fun `isMoviesGroupingEnabled defaults to true`() {
        every { mockPrefs.getBoolean("movies_grouping_enabled", true) } returns true

        val result = preferencesManager.isMoviesGroupingEnabled()

        assertTrue(result)
    }

    @Test
    fun `getMoviesGroupingSeparator defaults to hyphen`() {
        every { mockPrefs.getString("movies_grouping_separator", "-") } returns "-"

        val result = preferencesManager.getMoviesGroupingSeparator()

        assertEquals("-", result)
    }

    // ========== SERIES GROUPING DEFAULTS ==========

    @Test
    fun `isSeriesGroupingEnabled defaults to true`() {
        every { mockPrefs.getBoolean("series_grouping_enabled", true) } returns true

        val result = preferencesManager.isSeriesGroupingEnabled()

        assertTrue(result)
    }

    @Test
    fun `getSeriesGroupingSeparator defaults to FIRST_WORD`() {
        every { mockPrefs.getString("series_grouping_separator", "FIRST_WORD") } returns
            "FIRST_WORD"

        val result = preferencesManager.getSeriesGroupingSeparator()

        assertEquals("FIRST_WORD", result)
    }

    // ========== LIVE GROUPING DEFAULTS ==========

    @Test
    fun `isLiveGroupingEnabled defaults to true`() {
        every { mockPrefs.getBoolean("live_grouping_enabled", true) } returns true

        val result = preferencesManager.isLiveGroupingEnabled()

        assertTrue(result)
    }

    @Test
    fun `getLiveGroupingSeparator defaults to pipe`() {
        every { mockPrefs.getString("live_grouping_separator", "|") } returns "|"

        val result = preferencesManager.getLiveGroupingSeparator()

        assertEquals("|", result)
    }

    // ========== SET GROUPING SETTINGS ==========

    @Test
    fun `setMoviesGroupingEnabled saves to SharedPreferences`() {
        preferencesManager.setMoviesGroupingEnabled(false)

        verify { mockEditor.putBoolean("movies_grouping_enabled", false) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `setMoviesGroupingSeparator saves to SharedPreferences`() {
        preferencesManager.setMoviesGroupingSeparator("_")

        verify { mockEditor.putString("movies_grouping_separator", "_") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `setSeriesGroupingEnabled saves to SharedPreferences`() {
        preferencesManager.setSeriesGroupingEnabled(false)

        verify { mockEditor.putBoolean("series_grouping_enabled", false) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `setLiveGroupingSeparator saves to SharedPreferences`() {
        preferencesManager.setLiveGroupingSeparator("-")

        verify { mockEditor.putString("live_grouping_separator", "-") }
        verify { mockEditor.apply() }
    }

    // ========== FILTER MODE DEFAULTS ==========

    @Test
    fun `getMoviesFilterMode defaults to BLACKLIST`() {
        every { mockPrefs.getString("movies_filter_mode", "BLACKLIST") } returns "BLACKLIST"

        val result = preferencesManager.getMoviesFilterMode()

        assertEquals(ContentFilterSettings.FilterMode.BLACKLIST, result)
    }

    @Test
    fun `getSeriesFilterMode defaults to BLACKLIST`() {
        every { mockPrefs.getString("series_filter_mode", "BLACKLIST") } returns "BLACKLIST"

        val result = preferencesManager.getSeriesFilterMode()

        assertEquals(ContentFilterSettings.FilterMode.BLACKLIST, result)
    }

    @Test
    fun `getLiveFilterMode defaults to BLACKLIST`() {
        every { mockPrefs.getString("live_filter_mode", "BLACKLIST") } returns "BLACKLIST"

        val result = preferencesManager.getLiveFilterMode()

        assertEquals(ContentFilterSettings.FilterMode.BLACKLIST, result)
    }

    @Test
    fun `getMoviesFilterMode returns WHITELIST when set`() {
        every { mockPrefs.getString("movies_filter_mode", "BLACKLIST") } returns "WHITELIST"

        val result = preferencesManager.getMoviesFilterMode()

        assertEquals(ContentFilterSettings.FilterMode.WHITELIST, result)
    }

    @Test
    fun `getMoviesFilterMode returns BLACKLIST for invalid value`() {
        every { mockPrefs.getString("movies_filter_mode", "BLACKLIST") } returns "INVALID"

        val result = preferencesManager.getMoviesFilterMode()

        assertEquals(ContentFilterSettings.FilterMode.BLACKLIST, result)
    }

    // ========== HIDDEN ITEMS PERSISTENCE ==========

    @Test
    fun `getMoviesHiddenItems returns empty set when null`() {
        every { mockPrefs.getString("movies_hidden_items", null) } returns null

        val result = preferencesManager.getMoviesHiddenItems()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMoviesHiddenItems returns parsed set from JSON`() {
        every { mockPrefs.getString("movies_hidden_items", null) } returns
            """["item1","item2","item3"]"""

        val result = preferencesManager.getMoviesHiddenItems()

        assertEquals(3, result.size)
        assertTrue(result.contains("item1"))
        assertTrue(result.contains("item2"))
        assertTrue(result.contains("item3"))
    }

    @Test
    fun `setMoviesHiddenItems saves as JSON`() {
        val items = setOf("hidden1", "hidden2")

        preferencesManager.setMoviesHiddenItems(items)

        verify { mockEditor.putString("movies_hidden_items", any()) }
        verify { mockEditor.apply() }
    }

    // ========== IS ITEM HIDDEN BLACKLIST MODE ==========

    @Test
    fun `isItemHidden returns true for blacklisted item`() {
        every { mockPrefs.getString("movies_filter_mode", "BLACKLIST") } returns "BLACKLIST"
        every { mockPrefs.getString("movies_hidden_items", null) } returns """["HiddenCategory"]"""

        val result = preferencesManager.isItemHidden("movies", "HiddenCategory")

        assertTrue(result)
    }

    @Test
    fun `isItemHidden returns false for non-blacklisted item`() {
        every { mockPrefs.getString("movies_filter_mode", "BLACKLIST") } returns "BLACKLIST"
        every { mockPrefs.getString("movies_hidden_items", null) } returns """["HiddenCategory"]"""

        val result = preferencesManager.isItemHidden("movies", "VisibleCategory")

        assertFalse(result)
    }

    // ========== IS ITEM HIDDEN WHITELIST MODE ==========

    @Test
    fun `isItemHidden returns true for item NOT in whitelist`() {
        every { mockPrefs.getString("series_filter_mode", "BLACKLIST") } returns "WHITELIST"
        every { mockPrefs.getString("series_hidden_items", null) } returns """["AllowedCategory"]"""

        val result = preferencesManager.isItemHidden("series", "NotAllowedCategory")

        assertTrue(result) // Not in whitelist = hidden
    }

    @Test
    fun `isItemHidden returns false for item IN whitelist`() {
        every { mockPrefs.getString("series_filter_mode", "BLACKLIST") } returns "WHITELIST"
        every { mockPrefs.getString("series_hidden_items", null) } returns """["AllowedCategory"]"""

        val result = preferencesManager.isItemHidden("series", "AllowedCategory")

        assertFalse(result) // In whitelist = visible
    }

    // ========== VPN WARNING ==========

    @Test
    fun `isVpnWarningEnabled defaults to false`() {
        every { mockPrefs.getBoolean("vpn_warning_enabled", false) } returns false

        val result = preferencesManager.isVpnWarningEnabled()

        assertFalse(result)
    }

    @Test
    fun `setVpnWarningEnabled saves to SharedPreferences`() {
        preferencesManager.setVpnWarningEnabled(true)

        verify { mockEditor.putBoolean("vpn_warning_enabled", true) }
        verify { mockEditor.apply() }
    }

    // ========== GENERIC WRAPPER METHODS ==========

    @Test
    fun `isGroupingEnabled delegates to content type method`() {
        every { mockPrefs.getBoolean("movies_grouping_enabled", true) } returns false
        every { mockPrefs.getBoolean("series_grouping_enabled", true) } returns true
        every { mockPrefs.getBoolean("live_grouping_enabled", true) } returns true

        assertFalse(preferencesManager.isGroupingEnabled(ContentFilterSettings.ContentType.MOVIES))
        assertTrue(preferencesManager.isGroupingEnabled(ContentFilterSettings.ContentType.SERIES))
        assertTrue(preferencesManager.isGroupingEnabled(ContentFilterSettings.ContentType.LIVE_TV))
    }

    @Test
    fun `getCustomSeparator returns correct separator for content type`() {
        every { mockPrefs.getString("movies_grouping_separator", "-") } returns "-"
        every { mockPrefs.getString("series_grouping_separator", "FIRST_WORD") } returns
            "FIRST_WORD"
        every { mockPrefs.getString("live_grouping_separator", "|") } returns "|"

        assertEquals(
            "-",
            preferencesManager.getCustomSeparator(ContentFilterSettings.ContentType.MOVIES)
        )
        assertEquals(
            "FIRST_WORD",
            preferencesManager.getCustomSeparator(ContentFilterSettings.ContentType.SERIES)
        )
        assertEquals(
            "|",
            preferencesManager.getCustomSeparator(ContentFilterSettings.ContentType.LIVE_TV)
        )
    }

    // ========== HIDDEN GROUPS ==========

    @Test
    fun `getMoviesHiddenGroups returns empty set when null`() {
        every { mockPrefs.getString("movies_hidden_groups", null) } returns null

        val result = preferencesManager.getMoviesHiddenGroups()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMoviesHiddenGroups returns parsed set from JSON`() {
        every { mockPrefs.getString("movies_hidden_groups", null) } returns """["US","UK"]"""

        val result = preferencesManager.getMoviesHiddenGroups()

        assertEquals(2, result.size)
        assertTrue(result.contains("US"))
        assertTrue(result.contains("UK"))
    }

    // ========== HIDDEN CATEGORIES ==========

    @Test
    fun `getMoviesHiddenCategories returns empty set when null`() {
        every { mockPrefs.getString("movies_hidden_categories", null) } returns null

        val result = preferencesManager.getMoviesHiddenCategories()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `setMoviesHiddenCategories saves as JSON`() {
        val categories = setOf("Category1", "Category2")

        preferencesManager.setMoviesHiddenCategories(categories)

        verify { mockEditor.putString("movies_hidden_categories", any()) }
        verify { mockEditor.apply() }
    }

    // ========== INVALID CONTENT TYPE ==========

    @Test
    fun `isItemHidden returns false for unknown content type`() {
        val result = preferencesManager.isItemHidden("unknown", "SomeItem")

        assertFalse(result)
    }

    // ========== EMPTY JSON HANDLING ==========

    @Test
    fun `getMoviesHiddenItems handles invalid JSON gracefully`() {
        every { mockPrefs.getString("movies_hidden_items", null) } returns "not valid json"

        val result = preferencesManager.getMoviesHiddenItems()

        assertTrue(result.isEmpty())
    }
}
