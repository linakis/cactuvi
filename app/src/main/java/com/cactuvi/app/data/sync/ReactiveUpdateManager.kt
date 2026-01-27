package com.cactuvi.app.data.sync

import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Event bus for reactive UI updates with idle detection.
 *
 * Flow:
 * 1. SyncCoordinator emits ContentDiff events
 * 2. Events are queued if user is interacting with UI
 * 3. When user becomes idle (3s no interaction) AND fragment is visible, emit queued events
 * 4. Fragments apply granular updates without full screen reload
 */
@Singleton
class ReactiveUpdateManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _contentDiffs =
        MutableSharedFlow<List<ContentDiff>>(
            replay = 0,
            extraBufferCapacity = 10,
        )

    val contentDiffs: SharedFlow<List<ContentDiff>> = _contentDiffs.asSharedFlow()

    private val pendingDiffs = CopyOnWriteArrayList<ContentDiff>()
    private var isUserIdle = true
    private var lastInteractionTime = 0L

    companion object {
        private const val IDLE_THRESHOLD_MS = 3000L // 3 seconds
    }

    /**
     * Called by SyncCoordinator after detecting changes. Queues diffs and emits when user is idle.
     */
    suspend fun emitDiffs(diffs: List<ContentDiff>) {
        if (diffs.isEmpty()) return

        pendingDiffs.addAll(diffs)

        // If user is already idle, emit immediately
        if (isUserIdle) {
            flushPendingDiffs()
        }
    }

    /** Called by IdleDetectionHelper when user interacts with UI. */
    fun onUserInteraction() {
        isUserIdle = false
        lastInteractionTime = System.currentTimeMillis()

        // Start idle timer
        scope.launch {
            delay(IDLE_THRESHOLD_MS)

            // Check if still idle (no new interaction in last 3s)
            val timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime
            if (timeSinceLastInteraction >= IDLE_THRESHOLD_MS) {
                isUserIdle = true
                flushPendingDiffs()
            }
        }
    }

    /** Emit all pending diffs to subscribers. */
    private suspend fun flushPendingDiffs() {
        if (pendingDiffs.isEmpty()) return

        val diffsToEmit = pendingDiffs.toList()
        pendingDiffs.clear()

        _contentDiffs.emit(diffsToEmit)
    }
}
