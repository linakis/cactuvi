# Race Condition Fix - Validation Plan

## Problem Summary
Series categories showed **(0)** item counts despite 30k+ records in database due to race condition.

## Root Cause
Two concurrent `getSeries()` calls:
1. **Call 1** (app init): clearAll() → starts inserting 30k items (~2 min)
2. **Call 2** (user navigation, 15s later): clearAll() → wipes Call 1's data

Result: Only 1 record in DB, all categories show (0).

## Fix Applied
1. **Mutex synchronization** - Only one getSeries() executes at a time
2. **Fragment-level duplicate prevention** - Wait for existing loads before starting new ones
3. **Fixed return labels** - `return@withLock` instead of `return@withContext`

## Validation Scenarios

### Scenario 1: Race Condition Prevention (Primary)
**Objective:** Verify mutex prevents concurrent getSeries() calls

**Setup:**
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.iptv.app
adb logcat -c
```

**Test Steps:**
1. Launch app
2. Add IPTV source:
   - Server: `http://garlic82302.cdngold.me`
   - Username: `2bd16b40497f`
   - Password: `fc8edbab6b`
3. Tap "Save Source" 
4. **IMMEDIATELY** after HomeActivity appears, tap "Series" button (within 5 seconds)
5. Monitor logs for 3 minutes

**Expected Logs:**
```
# Only ONE getSeries() should execute
19:50:00 Repository.getSeries START (Call 1 - app init)
19:50:05 SeriesFragment: Series already loading - waiting  ← Call 2 blocked by mutex
19:52:00 Repository.getSeries END | 120s | inserted=30477
19:52:00 [DEBUG getSeries] DB count verification: 30477  ← MUST MATCH!
19:52:00 SeriesFragment: Load completed - checking cache
19:52:00 SeriesFragment: Cache now exists - loading UI
```

**Success Criteria:**
- ✅ Only ONE `Repository.getSeries START` log
- ✅ `SeriesFragment` sees "already loading" and waits
- ✅ DB count verification matches inserted count (30477 = 30477)
- ✅ Category counts show positive numbers, not (0)
- ✅ No `clearAll()` overlap in logs

**Failure Indicators:**
- ❌ TWO `Repository.getSeries START` logs
- ❌ DB count < inserted count
- ❌ Categories still show (0)

### Scenario 2: Normal Operation (No Race Condition)
**Objective:** Verify normal operation when no concurrent calls occur

**Test Steps:**
1. Clear app data
2. Add source
3. **Wait 3 minutes** after saving source (let background load complete)
4. Navigate to Series tab
5. Verify instant load from cache

**Expected Result:**
- Series screen loads instantly (<1 second)
- Categories show correct counts
- No background loading triggered

### Scenario 3: Multiple Tab Rapid Switching
**Objective:** Verify mutex works across all content types

**Test Steps:**
1. Clear app data
2. Add source
3. Rapidly switch between: Movies → Series → Live TV → Movies (within 30 seconds)
4. Monitor logs

**Expected Result:**
- Each content type loads independently with its own mutex
- No cross-contamination between Movies/Series/Live databases
- All counts eventually show correctly

## Validation Commands

### Full Validation Workflow
```bash
# 1. Build and install
cd /Users/nlinakis/Development/iptv/iptv-app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. Clear data and start logging
adb shell pm clear com.iptv.app
adb logcat -c
adb logcat | grep -E "IPTV_PERF|Repository\.(getSeries|getMovies|getLive)|SeriesFragment|MoviesFragment" > race_condition_test.log &
LOGCAT_PID=$!

# 3. Launch app
adb shell am start -n com.iptv.app/.ui.LoadingActivity

# 4. Manual steps:
#    - Add source (see credentials above)
#    - Immediately tap Series
#    - Wait 3 minutes

# 5. Stop logging and analyze
kill $LOGCAT_PID
grep "Repository.getSeries.*START" race_condition_test.log | wc -l  # MUST BE 1
grep "\[DEBUG getSeries\] Total inserted:" race_condition_test.log
grep "\[DEBUG getSeries\] DB count verification:" race_condition_test.log
grep "Series already loading" race_condition_test.log
```

### Check Category Counts in DB
```bash
# After load completes, verify DB directly
adb shell "run-as com.iptv.app sqlite3 /data/data/com.iptv.app/databases/iptv_database 'SELECT COUNT(*) FROM series;'"
# Expected: 30477 (or similar large number)

# Check sample category counts
adb shell "run-as com.iptv.app sqlite3 /data/data/com.iptv.app/databases/iptv_database \"SELECT categoryId, COUNT(*) FROM series GROUP BY categoryId LIMIT 5;\""
# Expected: Multiple categories with non-zero counts
```

### Screenshot Verification
```bash
# After Series screen loads, take screenshot
adb exec-out screencap -p > series_fixed.png
# Manually verify: Categories show numbers like (123) not (0)
```

## Success Metrics

### Primary (Must Pass)
- ✅ Only ONE concurrent getSeries() call executes
- ✅ DB verification count matches inserted count
- ✅ Category counts are non-zero (e.g., "AFRICA SERIES (123)")
- ✅ No database corruption logs

### Secondary (Nice to Have)
- ✅ UI shows loading indicator while waiting for mutex
- ✅ Smooth navigation between tabs
- ✅ Memory usage stays under 150MB

## Rollback Plan
If validation fails:
```bash
git revert 4f1675e
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Re-investigate with additional logging
```

## Next Steps After Validation

### If Fix Works ✅
1. Remove verbose debug logs (keep key metrics)
2. Document fix in AGENTS.md
3. Consider similar race conditions in Movies/Live
4. Mark iptv-app-nn2 as validated

### If Fix Fails ❌
1. Analyze logs to understand why mutex didn't work
2. Check for other concurrent paths bypassing mutex
3. Consider additional synchronization (e.g., Repository-level semaphore)
4. Test on actual device (not just emulator)

## Known Limitations
- Manual testing required (automated UI tests had timing issues)
- Requires specific timing to reproduce (tap Series within 5 seconds of home screen)
- Debug logs add noise to logcat

## Related Files
- `ContentRepository.kt:581-720` - getSeries() with mutex
- `SeriesFragment.kt:271-339` - loadData() with duplicate prevention
- Commit: `4f1675e`
- BD Task: `iptv-app-nn2`
