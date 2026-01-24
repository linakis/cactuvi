# clearAll() Usage Audit & Improvement Plan

## Current Usage Analysis

### Data Loading (High Risk - Race Condition Vulnerable)
**Location:** ContentRepository.kt

1. **Line 244** - `database.liveChannelDao().clearAll()` in `getLiveStreams()`
   - **Risk:** Race condition if concurrent calls
   - **Protected:** ✅ Yes (liveMutex)
   - **Improvement:** Replace with sourceId-based deletion

2. **Line 410** - `database.movieDao().clearAll()` in `getMovies()`
   - **Risk:** Race condition if concurrent calls
   - **Protected:** ✅ Yes (moviesMutex)
   - **Improvement:** Replace with sourceId-based deletion

3. **Line 628** - `database.seriesDao().clearAll()` in `getSeries()`
   - **Risk:** Race condition if concurrent calls (THIS WAS THE BUG!)
   - **Protected:** ✅ Yes (seriesMutex)
   - **Improvement:** Replace with sourceId-based deletion

### User-Initiated Actions (Low Risk - Single Threaded)

4. **Line 917** - `database.watchHistoryDao().clearAll()` in `clearWatchHistory()`
   - **Risk:** Low (user-initiated, UI thread)
   - **Protected:** No (not needed)
   - **Keep:** ✅ Legitimate use case

### Cache Management (Medium Risk - Could be called concurrently)

5. **Lines 1195-1198** - Multiple clearAll() in `clearAllCache()`
   ```kotlin
   database.liveChannelDao().clearAll()
   database.movieDao().clearAll()
   database.seriesDao().clearAll()
   database.categoryDao().clearAll()
   ```
   - **Risk:** Medium (could be called while data is loading)
   - **Protected:** No
   - **Improvement:** Add mutex or check loading state

6. **Lines 1213-1216** - Multiple clearAll() in `clearSourceCache(sourceId)`
   ```kotlin
   database.liveChannelDao().clearAll()  // TODO: sourceId filter
   database.movieDao().clearAll()        // TODO: sourceId filter
   database.seriesDao().clearAll()       // TODO: sourceId filter
   database.categoryDao().clearAll()     // TODO: sourceId filter
   ```
   - **Risk:** High - has TODO for sourceId filtering!
   - **Protected:** No
   - **Improvement:** MUST implement sourceId-based deletion

## Root Cause: Missing sourceId-Based Deletion

The core issue is that DAOs don't have methods to delete by sourceId. Current schema:
```kotlin
// SeriesEntity has sourceId field but no delete method!
@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,  // ← This exists but can't delete by it!
    val seriesId: Int,
    // ...
)
```

## Improvement Plan

### Phase 1: Add sourceId-Based Deletion Methods ✅ HIGH PRIORITY

Add to each DAO:
```kotlin
@Query("DELETE FROM series WHERE sourceId = :sourceId")
suspend fun deleteBySourceId(sourceId: String)
```

Apply to:
- SeriesDao
- MovieDao
- LiveChannelDao
- CategoryDao

### Phase 2: Replace clearAll() in Data Loading Methods ✅ HIGH PRIORITY

**Before:**
```kotlin
suspend fun getSeries(forceRefresh: Boolean = false): Result<List<Series>> = 
    withContext(Dispatchers.IO) {
        seriesMutex.withLock {
            // ...
            database.seriesDao().clearAll()  // ← Dangerous!
            // Insert new data...
        }
    }
```

**After:**
```kotlin
suspend fun getSeries(forceRefresh: Boolean = false): Result<List<Series>> = 
    withContext(Dispatchers.IO) {
        seriesMutex.withLock {
            // ...
            val (username, password, sourceId) = getCredentials()
            database.seriesDao().deleteBySourceId(sourceId)  // ← Safe & targeted!
            // Insert new data...
        }
    }
```

**Benefits:**
- Multi-source support: Can have data from multiple sources simultaneously
- Safer: Only affects current source's data
- Future-proof: Enables multi-source feature (iptv-app-hbr epic)

### Phase 3: Add Mutex Timeout ✅ HIGH PRIORITY

**Current Issue:** Mutex can wait forever if something goes wrong.

**Solution:** Use `withTimeout` wrapper:
```kotlin
suspend fun getSeries(forceRefresh: Boolean = false): Result<List<Series>> = 
    withContext(Dispatchers.IO) {
        try {
            withTimeout(5.minutes.inWholeMilliseconds) {  // ← 5 minute timeout
                seriesMutex.withLock {
                    // ... existing logic ...
                }
            }
        } catch (e: TimeoutCancellationException) {
            PerformanceLogger.log("getSeries timed out after 5 minutes - possible deadlock")
            Result.failure(Exception("Data load timed out. Please try again.", e))
        }
    }
```

**Benefits:**
- Prevents infinite waiting
- Surfaces deadlock issues quickly
- Better user experience (error instead of frozen app)

### Phase 4: Add Retry Mechanism ✅ MEDIUM PRIORITY

**Scenario:** Load fails due to network issue while waiting for mutex.

**Solution:** Add retry with exponential backoff:
```kotlin
private suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelay: Long = 1000L,
    maxDelay: Long = 10000L,
    factor: Double = 2.0,
    block: suspend () -> Result<T>
): Result<T> {
    var currentDelay = initialDelay
    repeat(maxAttempts - 1) { attempt ->
        val result = block()
        if (result.isSuccess) return result
        
        PerformanceLogger.log("Attempt ${attempt + 1} failed, retrying in ${currentDelay}ms")
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // Last attempt
}

// Usage:
suspend fun getSeries(forceRefresh: Boolean = false): Result<List<Series>> = 
    retryWithBackoff {
        withContext(Dispatchers.IO) {
            withTimeout(5.minutes.inWholeMilliseconds) {
                seriesMutex.withLock {
                    // ... existing logic ...
                }
            }
        }
    }
```

### Phase 5: Protect Cache Management Methods ✅ MEDIUM PRIORITY

**clearAllCache()** and **clearSourceCache()** should:
1. Check if any content is currently loading
2. If yes, wait for completion or show error
3. Reuse existing mutexes

```kotlin
suspend fun clearAllCache(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        // Wait for all loading operations to complete
        listOf(
            async { moviesMutex.withLock {} },
            async { seriesMutex.withLock {} },
            async { liveMutex.withLock {} }
        ).awaitAll()
        
        // Now safe to clear
        database.liveChannelDao().clearAll()
        database.movieDao().clearAll()
        database.seriesDao().clearAll()
        database.categoryDao().clearAll()
        invalidateAllNavigationTrees()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

## Implementation Priority

### P0 - CRITICAL (Race Condition Prevention)
✅ Already Done:
- Add mutexes to data loading methods
- Fix return@ labels
- Add Fragment-level duplicate prevention

### P1 - HIGH (Data Integrity & UX)
🔄 This Session:
1. Add `deleteBySourceId()` methods to DAOs
2. Replace `clearAll()` with `deleteBySourceId()` in data loading
3. Add mutex timeout (5 minutes)
4. Implement clearSourceCache() properly

### P2 - MEDIUM (Robustness)
⏳ Future:
1. Add retry mechanism with exponential backoff
2. Protect cache management methods with mutexes
3. Add telemetry for mutex wait times

## Testing Strategy

### Unit Tests
- Test `deleteBySourceId()` only deletes target source
- Test timeout triggers after 5 minutes
- Test retry attempts correct number of times

### Integration Tests
- Load data from Source A
- Load data from Source B
- Verify both exist simultaneously
- Clear Source A cache
- Verify Source B data intact

### Manual Tests
1. Trigger race condition scenario (worked before)
2. Let mutex wait 6 minutes (should timeout)
3. Simulate network failure (should retry)

## Rollback Plan

If improvements cause issues:
```bash
git revert <commit-hash>
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Success Criteria

- ✅ No more `clearAll()` in data loading (use `deleteBySourceId`)
- ✅ Mutex timeout after 5 minutes (no infinite waiting)
- ✅ Retry on failure (3 attempts with backoff)
- ✅ clearSourceCache() works with sourceId filtering
- ✅ Multi-source support enabled
- ✅ All tests pass

## Files to Modify

1. **SeriesDao.kt** - Add deleteBySourceId()
2. **MovieDao.kt** - Add deleteBySourceId()
3. **LiveChannelDao.kt** - Add deleteBySourceId()
4. **CategoryDao.kt** - Add deleteBySourceId()
5. **ContentRepository.kt** - Replace clearAll(), add timeout, add retry
6. **ContentRepositoryTest.kt** (NEW) - Add unit tests

## Related Tasks

- **iptv-app-nn2** - Race condition fix (DONE)
- **iptv-app-hbr** - Multi-source support epic (ENABLED by this work)
- **Phase 3** - Parallel parsing (compatible with these improvements)
