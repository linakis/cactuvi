package com.cactuvi.app.ui.downloads

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.adapter.FragmentStateAdapter

@UnstableApi
class DownloadsPagerAdapter(
    fragmentActivity: FragmentActivity
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            TAB_ACTIVE -> ActiveDownloadsFragment.newInstance()
            TAB_COMPLETED -> CompletedDownloadsFragment.newInstance()
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }

    companion object {
        const val TAB_ACTIVE = 0
        const val TAB_COMPLETED = 1
    }
}
