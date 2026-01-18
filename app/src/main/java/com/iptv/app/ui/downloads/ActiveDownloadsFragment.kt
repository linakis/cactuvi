package com.iptv.app.ui.downloads

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iptv.app.R
import com.iptv.app.data.db.entities.DownloadEntity
import com.iptv.app.data.repository.DownloadRepository
import kotlinx.coroutines.launch

@UnstableApi
class ActiveDownloadsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var emptySubtext: TextView
    private lateinit var adapter: ActiveDownloadsAdapter
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

        emptyText.text = getString(R.string.no_active_downloads)
        emptySubtext.text = getString(R.string.no_active_downloads_hint)
    }

    private fun setupRecyclerView() {
        adapter = ActiveDownloadsAdapter(
            onCancelClick = { download ->
                showCancelDialog(download)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun observeDownloads() {
        showLoading(true)

        lifecycleScope.launch {
            downloadRepository.getActiveDownloads().collect { downloads ->
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

    private fun showCancelDialog(download: DownloadEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.cancel_download)
            .setMessage(getString(R.string.cancel_download_message, download.contentName))
            .setPositiveButton(R.string.cancel_download) { _, _ ->
                cancelDownload(download)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun cancelDownload(download: DownloadEntity) {
        lifecycleScope.launch {
            try {
                downloadRepository.cancelDownload(download.contentId)
                Toast.makeText(
                    requireContext(),
                    R.string.download_cancelled,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.download_cancel_failed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
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
        fun newInstance() = ActiveDownloadsFragment()
    }
}
