package com.cactuvi.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.cactuvi.app.R
import com.cactuvi.app.data.models.ContentFilterSettings
import com.cactuvi.app.ui.common.ModernToolbar
import com.cactuvi.app.utils.PreferencesManager

abstract class FilterSettingsActivity : AppCompatActivity() {
    
    protected lateinit var modernToolbar: ModernToolbar
    protected lateinit var groupingSwitch: SwitchMaterial
    protected lateinit var separatorSpinner: Spinner
    protected lateinit var manageFoldersCard: MaterialCardView
    protected lateinit var folderFilterStatus: TextView
    protected lateinit var preferencesManager: PreferencesManager
    
    protected abstract fun getContentType(): ContentFilterSettings.ContentType
    protected abstract fun getToolbarTitle(): String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_settings)
        
        preferencesManager = PreferencesManager.getInstance(this)
        
        setupToolbar()
        setupViews()
        loadSettings()
    }
    
    private fun setupToolbar() {
        modernToolbar = findViewById(R.id.modernToolbar)
        modernToolbar.title = getToolbarTitle()
        modernToolbar.onBackClick = { finish() }
    }
    
    private fun setupViews() {
        groupingSwitch = findViewById(R.id.groupingSwitch)
        separatorSpinner = findViewById(R.id.separatorSpinner)
        manageFoldersCard = findViewById(R.id.manageFoldersCard)
        folderFilterStatus = findViewById(R.id.folderFilterStatus)
        
        setupSeparatorSpinner()
        setupListeners()
    }
    
    private fun setupSeparatorSpinner() {
        val separators = arrayOf(
            "Pipe (|)",
            "Dash (-)",
            "Slash (/)",
            "First Word"
        )
        
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            separators
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        separatorSpinner.adapter = adapter
    }
    
    private fun setupListeners() {
        groupingSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setGroupingEnabled(getContentType(), isChecked)
            updateSeparatorVisibility(isChecked)
        }
        
        separatorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val separator = when (position) {
                    0 -> "|"
                    1 -> "-"
                    2 -> "/"
                    3 -> "FIRST_WORD"
                    else -> "|"
                }
                preferencesManager.setCustomSeparator(getContentType(), separator)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        manageFoldersCard.setOnClickListener {
            val intent = Intent(this, ManageFoldersActivity::class.java)
            intent.putExtra("content_type", getContentType().name)
            startActivityForResult(intent, REQUEST_MANAGE_FOLDERS)
        }
    }
    
    private fun loadSettings() {
        val isGroupingEnabled = preferencesManager.isGroupingEnabled(getContentType())
        groupingSwitch.isChecked = isGroupingEnabled
        updateSeparatorVisibility(isGroupingEnabled)
        
        val separator = preferencesManager.getCustomSeparator(getContentType())
        val separatorIndex = when (separator) {
            "|" -> 0
            "-" -> 1
            "/" -> 2
            "FIRST_WORD" -> 3
            else -> 0
        }
        separatorSpinner.setSelection(separatorIndex)
        
        updateFolderFilterStatus()
    }
    
    private fun updateSeparatorVisibility(isGroupingEnabled: Boolean) {
        separatorSpinner.isEnabled = isGroupingEnabled
        manageFoldersCard.isEnabled = isGroupingEnabled
        manageFoldersCard.alpha = if (isGroupingEnabled) 1.0f else 0.5f
    }
    
    private fun updateFolderFilterStatus() {
        val filterMode = preferencesManager.getFilterMode(getContentType())
        val hiddenGroups = preferencesManager.getHiddenGroups(getContentType())
        val hiddenCategories = preferencesManager.getHiddenCategories(getContentType())
        
        val statusText = when {
            hiddenGroups.isEmpty() && hiddenCategories.isEmpty() -> "All content visible"
            filterMode == ContentFilterSettings.FilterMode.BLACKLIST -> {
                buildFilterStatusText(hiddenGroups.size, hiddenCategories.size, "hidden")
            }
            else -> {
                buildFilterStatusText(hiddenGroups.size, hiddenCategories.size, "shown")
            }
        }
        
        folderFilterStatus.text = statusText
    }
    
    private fun buildFilterStatusText(groupCount: Int, categoryCount: Int, action: String): String {
        return when {
            groupCount > 0 && categoryCount > 0 -> {
                val groupText = "$groupCount group${if (groupCount == 1) "" else "s"}"
                val categoryText = "$categoryCount categor${if (categoryCount == 1) "y" else "ies"}"
                "$groupText and $categoryText $action"
            }
            groupCount > 0 -> {
                "$groupCount group${if (groupCount == 1) "" else "s"} $action"
            }
            categoryCount > 0 -> {
                "$categoryCount categor${if (categoryCount == 1) "y" else "ies"} $action"
            }
            else -> "All content visible"
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_FOLDERS && resultCode == RESULT_OK) {
            updateFolderFilterStatus()
        }
    }
    
    companion object {
        private const val REQUEST_MANAGE_FOLDERS = 1001
    }
}

class MoviesFilterSettingsActivity : FilterSettingsActivity() {
    override fun getContentType() = ContentFilterSettings.ContentType.MOVIES
    override fun getToolbarTitle() = "Movies Filter Settings"
}

class SeriesFilterSettingsActivity : FilterSettingsActivity() {
    override fun getContentType() = ContentFilterSettings.ContentType.SERIES
    override fun getToolbarTitle() = "Series Filter Settings"
}

class LiveTvFilterSettingsActivity : FilterSettingsActivity() {
    override fun getContentType() = ContentFilterSettings.ContentType.LIVE_TV
    override fun getToolbarTitle() = "Live TV Filter Settings"
}
