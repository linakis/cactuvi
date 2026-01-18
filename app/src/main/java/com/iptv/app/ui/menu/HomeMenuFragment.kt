package com.iptv.app.ui.menu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iptv.app.R

class HomeMenuFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MenuGridAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_menu, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar(view)
        setupRecyclerView(view)
        loadMenuItems()
    }
    
    private fun setupToolbar(view: View) {
        view.findViewById<View>(R.id.searchButton).setOnClickListener {
            val intent = Intent(requireContext(), com.iptv.app.ui.search.SearchActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.menuRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        
        adapter = MenuGridAdapter(emptyList()) { menuItem ->
            menuItem.action()
        }
        recyclerView.adapter = adapter
    }
    
    private fun loadMenuItems() {
        val menuItems = listOf(
            MenuItem(
                id = "continue_watching",
                title = "Continue Watching",
                icon = R.drawable.ic_play_circle
            ) {
                val intent = Intent(requireContext(), com.iptv.app.ui.continuewatching.ContinueWatchingActivity::class.java)
                startActivity(intent)
            },
            MenuItem(
                id = "live_tv",
                title = "Live TV",
                icon = R.drawable.ic_live_tv
            ) {
                val intent = Intent(requireContext(), com.iptv.app.ui.live.LiveTvActivity::class.java)
                startActivity(intent)
            },
            MenuItem(
                id = "movies",
                title = "Movies",
                icon = R.drawable.ic_movies
            ) {
                val intent = Intent(requireContext(), com.iptv.app.ui.movies.MoviesActivity::class.java)
                startActivity(intent)
            },
            MenuItem(
                id = "series",
                title = "Series",
                icon = R.drawable.ic_series
            ) {
                val intent = Intent(requireContext(), com.iptv.app.ui.series.SeriesActivity::class.java)
                startActivity(intent)
            },
            MenuItem(
                id = "my_list",
                title = "My List",
                icon = R.drawable.ic_favorite
            ) {
                val intent = Intent(requireContext(), com.iptv.app.ui.mylist.MyListActivity::class.java)
                startActivity(intent)
            },
            MenuItem(
                id = "downloads",
                title = "Downloads",
                icon = R.drawable.ic_download
            ) {
                val intent = Intent(requireContext(), com.iptv.app.ui.downloads.DownloadsActivity::class.java)
                startActivity(intent)
            },
            MenuItem(
                id = "settings",
                title = "Settings",
                icon = R.drawable.ic_settings
            ) {
                val intent = Intent(requireContext(), com.iptv.app.ui.settings.SettingsActivity::class.java)
                startActivity(intent)
            }
        )
        
        adapter.updateItems(menuItems)
    }
}
