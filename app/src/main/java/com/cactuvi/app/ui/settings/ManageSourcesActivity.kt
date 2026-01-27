package com.cactuvi.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.data.models.StreamSource
import com.cactuvi.app.domain.repository.ContentRepository
import com.cactuvi.app.utils.SourceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ManageSourcesActivity : AppCompatActivity() {

    @Inject lateinit var sourceManager: SourceManager
    @Inject lateinit var repository: ContentRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SourcesAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var fabAddSource: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_sources)

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
        adapter =
            SourcesAdapter(
                onSourceClick = { source -> onSourceSelected(source) },
                onEditClick = { source -> editSource(source) },
                onDeleteClick = { source -> confirmDeleteSource(source) },
            )
        recyclerView.adapter = adapter

        fabAddSource.setOnClickListener { addNewSource() }
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
        // Show loading dialog
        val loadingDialog =
            AlertDialog.Builder(this)
                .setTitle("Switching Source")
                .setMessage("Switching to '${source.nickname}'...")
                .setCancelable(false)
                .create()

        loadingDialog.show()

        lifecycleScope.launch {
            try {
                // Set active source
                sourceManager.setActiveSource(source.id)

                // Clear cache for new source
                repository.clearAllCache()

                // Show success
                loadingDialog.dismiss()
                Toast.makeText(
                        this@ManageSourcesActivity,
                        "Switched to '${source.nickname}'",
                        Toast.LENGTH_SHORT,
                    )
                    .show()

                loadSources() // Refresh to show new active source
            } catch (e: Exception) {
                loadingDialog.dismiss()
                AlertDialog.Builder(this@ManageSourcesActivity)
                    .setTitle("Error")
                    .setMessage("Failed to activate source: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun addNewSource() {
        val intent = Intent(this, AddEditSourceActivity::class.java)
        startActivityForResult(intent, REQUEST_ADD_SOURCE)
    }

    private fun editSource(source: StreamSource) {
        val intent = Intent(this, AddEditSourceActivity::class.java)
        intent.putExtra(AddEditSourceActivity.EXTRA_SOURCE_ID, source.id)
        startActivityForResult(intent, REQUEST_EDIT_SOURCE)
    }

    private fun confirmDeleteSource(source: StreamSource) {
        // Check if trying to delete active source
        lifecycleScope.launch {
            val activeSource = sourceManager.getActiveSource()

            if (activeSource?.id == source.id) {
                AlertDialog.Builder(this@ManageSourcesActivity)
                    .setTitle("Cannot Delete Active Source")
                    .setMessage(
                        "Please switch to another source before deleting '${source.nickname}'."
                    )
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            // Show delete confirmation
            AlertDialog.Builder(this@ManageSourcesActivity)
                .setTitle("Delete Source")
                .setMessage(
                    "Are you sure you want to delete '${source.nickname}'? All cached data for this source will be removed.",
                )
                .setPositiveButton("Delete") { _, _ -> deleteSource(source) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun deleteSource(source: StreamSource) {
        lifecycleScope.launch {
            try {
                // Clear cache for this source
                repository.clearSourceCache(source.id)

                // Delete source
                sourceManager.deleteSource(source.id)

                Toast.makeText(
                        this@ManageSourcesActivity,
                        "Source '${source.nickname}' deleted",
                        Toast.LENGTH_SHORT,
                    )
                    .show()

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
