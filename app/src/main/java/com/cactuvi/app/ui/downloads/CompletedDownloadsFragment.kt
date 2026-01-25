package com.cactuvi.app.ui.downloads

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.R
import com.cactuvi.app.data.db.entities.DownloadEntity
import com.cactuvi.app.data.repository.DownloadRepository
import com.cactuvi.app.ui.player.PlayerActivity
import kotlinx.coroutines.launch

@UnstableApi
class CompletedDownloadsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var emptySubtext: TextView
    private lateinit var adapter: DownloadsAdapter
    private lateinit var downloadRepository: DownloadRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_downloads_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        downloadRepository = DownloadRepository(requireContext())

        initViews(view)
        setupRecyclerView()
        observeDownloads()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyView = view.findViewById(R.id.emptyView)
        emptyText = view.findViewById(R.id.emptyText)
        emptySubtext = view.findViewById(R.id.emptySubtext)

        emptyText.text = getString(R.string.no_downloads)
        emptySubtext.text = getString(R.string.start_downloading)
    }

    private fun setupRecyclerView() {
        adapter =
            DownloadsAdapter(
                onPlayClick = { download -> playDownload(download) },
                onDeleteClick = { download -> showDeleteDialog(download) },
            )

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        recyclerView.adapter = adapter
    }

    private fun observeDownloads() {
        showLoading(true)

        lifecycleScope.launch {
            downloadRepository.getCompletedDownloads().collect { downloads ->
                showLoading(false)

                if (downloads.isEmpty()) {
                    showEmptyState(true)
                } else {
                    showEmptyState(false)
                    adapter.submitList(downloads)
                }
            }
        }
    }

    private fun playDownload(download: DownloadEntity) {
        val uri = download.downloadUri
        if (uri.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.download_not_available, Toast.LENGTH_SHORT)
                .show()
            return
        }

        val intent =
            Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("STREAM_URL", uri)
                putExtra("TITLE", download.contentName)
                putExtra("CONTENT_ID", download.contentId)
                putExtra("CONTENT_TYPE", download.contentType)
                putExtra("POSTER_URL", download.posterUrl)
                putExtra("RESUME_POSITION", 0L)
            }
        startActivity(intent)
    }

    private fun showDeleteDialog(download: DownloadEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_download)
            .setMessage(getString(R.string.delete_download_message, download.contentName))
            .setPositiveButton(R.string.delete_download) { _, _ -> deleteDownload(download) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteDownload(download: DownloadEntity) {
        lifecycleScope.launch {
            try {
                downloadRepository.deleteDownload(download.contentId)
                Toast.makeText(
                        requireContext(),
                        R.string.download_deleted,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } catch (e: Exception) {
                Toast.makeText(
                        requireContext(),
                        getString(R.string.download_delete_failed, e.message),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showEmptyState(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    companion object {
        fun newInstance() = CompletedDownloadsFragment()
    }
}
