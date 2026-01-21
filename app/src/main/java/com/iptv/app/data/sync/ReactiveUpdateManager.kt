package com.iptv.app.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton event bus for reactive UI updates.
 * SyncCoordinator emits ContentDiff events, fragments subscribe and apply granular updates.
 * 
 * TODO: Implement idle detection and queueing (Day 3 - Task 4.1)
 */
class ReactiveUpdateManager private constructor() {
    
    private val _contentDiffs = MutableSharedFlow<List<ContentDiff>>(
        replay = 0,
        extraBufferCapacity = 10
    )
    
    val contentDiffs: SharedFlow<List<ContentDiff>> = _contentDiffs.asSharedFlow()
    
    companion object {
        @Volatile
        private var INSTANCE: ReactiveUpdateManager? = null
        
        fun getInstance(): ReactiveUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReactiveUpdateManager().also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * Emit diff events to subscribers (fragments).
     * Called by SyncCoordinator after detecting changes.
     */
    suspend fun emitDiffs(diffs: List<ContentDiff>) {
        if (diffs.isNotEmpty()) {
            _contentDiffs.emit(diffs)
        }
    }
}
