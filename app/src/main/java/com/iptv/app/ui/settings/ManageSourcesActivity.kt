package com.iptv.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iptv.app.R
import com.iptv.app.data.models.StreamSource
import com.iptv.app.utils.SourceManager
import kotlinx.coroutines.launch

class ManageSourcesActivity : AppCompatActivity() {
    
    private lateinit var sourceManager: SourceManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SourcesAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var fabAddSource: FloatingActionButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_sources)
        
        sourceManager = SourceManager.getInstance(this)
        
        setupToolbar()
        setupViews()
        loadSources()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Sources"
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.sourcesRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        fabAddSource = findViewById(R.id.fabAddSource)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SourcesAdapter(
            onSourceClick = { source -> onSourceSelected(source) },
            onEditClick = { source -> editSource(source) },
            onDeleteClick = { source -> confirmDeleteSource(source) }
        )
        recyclerView.adapter = adapter
        
        fabAddSource.setOnClickListener {
            addNewSource()
        }
    }
    
    private fun loadSources() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val sources = sourceManager.getAllSources()
                val activeSource = sourceManager.getActiveSource()
                
                progressBar.visibility = View.GONE
                
                if (sources.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(sources, activeSource?.id)
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptyView.text = "Error loading sources: ${e.message}"
            }
        }
    }
    
    private fun onSourceSelected(source: StreamSource) {
        lifecycleScope.launch {
            try {
                sourceManager.setActiveSource(source.id)
                
                // TODO: Trigger cache clear and reload
                
                loadSources() // Refresh to show new active source
            } catch (e: Exception) {
                AlertDialog.Builder(this@ManageSourcesActivity)
                    .setTitle("Error")
                    .setMessage("Failed to activate source: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    private fun addNewSource() {
        // TODO: Launch AddEditSourceActivity
        val intent = Intent(this, AddEditSourceActivity::class.java)
        startActivityForResult(intent, REQUEST_ADD_SOURCE)
    }
    
    private fun editSource(source: StreamSource) {
        // TODO: Launch AddEditSourceActivity with source data
        val intent = Intent(this, AddEditSourceActivity::class.java)
        intent.putExtra("SOURCE_ID", source.id)
        startActivityForResult(intent, REQUEST_EDIT_SOURCE)
    }
    
    private fun confirmDeleteSource(source: StreamSource) {
        AlertDialog.Builder(this)
            .setTitle("Delete Source")
            .setMessage("Are you sure you want to delete '${source.nickname}'? All cached data for this source will be removed.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSource(source)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteSource(source: StreamSource) {
        lifecycleScope.launch {
            try {
                sourceManager.deleteSource(source.id)
                // TODO: Clear cache for this source
                loadSources()
            } catch (e: Exception) {
                AlertDialog.Builder(this@ManageSourcesActivity)
                    .setTitle("Error")
                    .setMessage("Failed to delete source: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            loadSources()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    companion object {
        private const val REQUEST_ADD_SOURCE = 1
        private const val REQUEST_EDIT_SOURCE = 2
    }
}
