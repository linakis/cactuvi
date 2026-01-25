package com.cactuvi.app.utils

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.cactuvi.app.data.sync.ReactiveUpdateManager

/**
 * Helper to detect user interaction and notify ReactiveUpdateManager. Attach to RecyclerView or any
 * view to track touch/scroll events.
 *
 * Usage:
 * ```
 * IdleDetectionHelper.attach(recyclerView)
 * ```
 */
object IdleDetectionHelper {

    private val reactiveUpdateManager = ReactiveUpdateManager.getInstance()

    /** Attach idle detection to a RecyclerView. Tracks scroll and touch events. */
    fun attach(recyclerView: RecyclerView) {
        // Track scroll events
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                        reactiveUpdateManager.onUserInteraction()
                    }
                }
            }
        )

        // Track touch events
        recyclerView.setOnTouchListener { _, event ->
            if (
                event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE
            ) {
                reactiveUpdateManager.onUserInteraction()
            }
            false // Don't consume the event
        }
    }

    /** Attach idle detection to any view (tracks touch only). */
    fun attach(view: View) {
        view.setOnTouchListener { _, event ->
            if (
                event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE
            ) {
                reactiveUpdateManager.onUserInteraction()
            }
            false // Don't consume the event
        }
    }
}
