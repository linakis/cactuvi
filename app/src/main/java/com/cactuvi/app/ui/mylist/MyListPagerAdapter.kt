package com.cactuvi.app.ui.mylist

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MyListPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = 4  // All, Live TV, Movies, Series
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MyListFragment.newInstance(null)  // All
            1 -> MyListFragment.newInstance("live_channel")  // Live TV
            2 -> MyListFragment.newInstance("movie")  // Movies
            3 -> MyListFragment.newInstance("series")  // Series
            else -> MyListFragment.newInstance(null)
        }
    }
}
