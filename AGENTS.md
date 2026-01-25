# AGENTS.md - IPTV Android App Development Guide

This file contains essential information for AI coding agents working on this codebase.

## ⚠️ CRITICAL: Communication Style

**NO SUMMARIES** - Do not produce summary sections after completing tasks. This is non-negotiable.
**NO IMPLEMENTATION DOCUMENTS** - Do not create detailed implementation plans or design documents. BD tasks should contain all necessary information.

- **Be concise:** Brief confirmations only (e.g., "Done" or "Fixed")
- **Action-oriented:** Do the work, don't explain what was done
- **Ask when unclear:** If requirements are ambiguous, ask before implementing

## ⚠️ CRITICAL: Task Tracking with BD

**ALWAYS use BD for task management.** BD (Beads) is the single source of truth for all work.

### Mandatory BD Workflow

1. **Before starting work:**
   ```bash
   bd list              # Check existing tasks
   bd show <task-id>    # Read task details
   ```

2. **During work:**
   ```bash
   # Update task with progress (BD doesn't have a 'start' command)
   bd update <task-id> --description "Started: [what you're working on]"
   
   # Update task with ongoing progress
   bd update <task-id> --description "Progress: [update details]"
   ```

3. **Creating subtasks:**
   ```bash
   bd create --title "Subtask name" --type task --priority P1 --parent <parent-id>
   ```

4. **After completion:**
   ```bash
   bd update <task-id> --description "Completed: [what was done]"
   bd close <task-id>   # Only after verification passes
   ```

### When to Create BD Tasks

- **ALWAYS** create tasks for:
  - Features or bug fixes that take >15 minutes
  - Any work that involves multiple files
  - Performance optimizations
  - Refactoring efforts
  - Documentation updates (if substantial)

- **Multi-step work** requires:
  - Parent task describing the goal
  - Subtasks for each major step
  - Update parent task with progress after each subtask

### BD Task Quality Standards

- **Titles:** Clear, actionable (e.g., "Implement streaming JSON parser for large datasets")
- **Descriptions:** Include:
  - Acceptance criteria
  - Files affected
  - Testing requirements
  - Expected results/measurements
- **Priorities:**
  - P0: Critical/blocking
  - P1: High priority, next to work on
  - P2: Medium priority
  - P3: Low priority/nice-to-have

### Common BD Commands

```bash
# List tasks
bd list                          # All open tasks
bd list --priority P1            # P1 tasks only
bd list --json | jq '.[] | {id, title}'  # JSON output

# Show task details
bd show <task-id>                # Human-readable
bd show <task-id> --json         # JSON format
bd show <task-id> --refs         # Show references

# Create tasks
bd create --title "Task name" --type task --priority P1
bd create --parent <id> --title "Subtask"

# Update tasks
bd update <task-id> --description "Updated description"
bd update <task-id> --priority P0
bd close <task-id>               # Mark as completed

# Reopen tasks if needed
bd reopen <task-id>

# Search tasks
bd list --json | jq '.[] | select(.title | test("search term"; "i"))'

# Set operational state (creates event + updates label)
# Note: Use dimension=value syntax, not --state flag
bd set-state <task-id> operational_state=in_progress  # Mark as started
bd set-state <task-id> operational_state=blocked      # Mark as blocked
bd set-state <task-id> operational_state=completed    # Mark as done
```

### Integration with Git Commits

Always reference BD task IDs in commit messages:
```bash
git commit -m "Optimize: Implement streaming parser (iptv-app-q7q.1)

- Add StreamingJsonParser utility
- Update ContentRepository to use streaming
- 40% performance improvement

Refs: iptv-app-q7q"
```

## ⚠️ CRITICAL: Documentation Policy

**ONLY keep these MD files:**
1. **README.md** - Project overview, build instructions, usage guide
2. **AGENTS.md** - AI agent guidelines (this file)
3. **ARCHITECTURE.md** - Architecture patterns, layer diagrams, code examples

**NEVER create:**
- ❌ Summary documents (use BD task descriptions instead)
- ❌ Session notes (use BD task updates)
- ❌ Implementation plans (use BD subtasks with acceptance criteria)
- ❌ Validation reports (add to BD task description as completion proof)
- ❌ Test reports (update BD task with results)
- ❌ Checklists (use BD task acceptance criteria)
- ❌ Build instructions as separate MD (put in README)

**Golden Rule:** Everything you do should be documented in BD tasks for posterity. 
Markdown files are ONLY for ongoing reference and patterns, not historical records.

**Why this matters:**
- BD tasks are searchable, filterable, and trackable
- MD files proliferate and become outdated
- One source of truth prevents conflicting information
- Future developers find work history in BD, not scattered MD files

## Architecture Guidelines

**See ARCHITECTURE.md for detailed patterns and diagrams.**

### MVVM + UDF Pattern (Mandatory)
The app follows **MVVM (Model-View-ViewModel) with Unidirectional Data Flow**:

```
Fragment → ViewModel → UseCase → Repository → DataSource → API/DB
   ↓          ↓          ↓           ↓            ↓
  UI      UiState   Business    Resource     Remote/Local
```

**Key Principles:**
- **UI Layer:** Fragments observe single `UiState` from ViewModels
- **Domain Layer:** UseCases encapsulate business logic, injected into ViewModels
- **Data Layer:** Repository exposes `Flow<Resource<T>>` (single source of truth)
- **DI:** Hilt for dependency injection throughout

### State Management Rules

**✅ DO:**
1. Use `Resource<T>` sealed class for all async operations (Loading/Success/Error)
2. Expose single `UiState` data class per screen from ViewModel
3. Derive UI state from data streams (no manual flags)
4. ViewModels survive configuration changes via `viewModelScope`
5. Use `StateFlow` for state, `SharedFlow` for one-time events
6. Inject dependencies via Hilt (@Inject constructors, @HiltViewModel)

**❌ DON'T:**
1. Use imperative state flags (isLoading, hasCache, forceRefresh)
2. Put business logic in Fragments (use ViewModels + UseCases)
3. Expose mutable state (expose `StateFlow`, not `MutableStateFlow`)
4. Call Repository directly from Fragments (use UseCases)
5. Use `GlobalScope` or `runBlocking` on main thread
6. Mix reactive and imperative patterns

### Testing Requirements

**Minimum Coverage:**
- ViewModel tests: 80% coverage required for all new screens
- UseCase tests: Required for complex business logic
- Repository tests: Required for data transformations
- Integration tests: For critical user flows

**Test Stack:**
- JUnit 4 for test framework
- MockK for mocking
- Turbine for Flow testing
- Coroutines Test for suspend functions

**Example:**
```kotlin
@Test
fun `should emit loading then success when movies load`() = runTest {
    viewModel.uiState.test {
        val loading = awaitItem()
        assertTrue(loading.isLoading)
        
        val success = awaitItem()
        assertNotNull(success.navigationTree)
    }
}
```

See ARCHITECTURE.md for complete testing patterns.

## Project Overview

**Type:** Android IPTV streaming application (Netflix-style UI)  
**Language:** Kotlin  
**Min SDK:** 26 | **Compile SDK:** 35 | **Target SDK:** 34  
**Architecture:** Repository pattern with ViewModels, Fragment-based navigation  
**Key Libraries:** Room, Retrofit, Paging 3, ExoPlayer (Media3), Glide, Material 3

**⚠️ TV Navigation Requirements:**  
This app is designed for TV screens and MUST support D-pad navigation. All UI elements must be:
- Focusable and navigable using D-pad (up/down/left/right)
- Properly handle focus states with clear visual indicators
- Support back button navigation through the app hierarchy
- Test all screens with D-pad input, not just touch

## Build & Test Commands

### Standard Build Commands
```bash
# Clean build
./gradlew clean

# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Install debug APK on connected device
./gradlew installDebug

# Build and install
./gradlew clean assembleDebug installDebug
```

### Testing Commands
```bash
# Run all unit tests
./gradlew test

# Run all instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.cactuvi.app.SpecificTestClass"

# Run single test method
./gradlew test --tests "com.cactuvi.app.SpecificTestClass.testMethodName"

# Run tests with coverage
./gradlew testDebugUnitTest jacocoTestReport
```

### Lint & Code Quality
```bash
# Run lint checks
./gradlew lint

# Generate lint report
./gradlew lintDebug

# Check for dependency updates
./gradlew dependencyUpdates
```

### Debugging Commands
```bash
# Build and check for Kotlin compile errors
./gradlew assembleDebug 2>&1 | grep "e: file"

# View recent app logs
adb devices
adb logcat -d | grep "com.cactuvi.app" | tail -50

# View only crash logs
adb logcat -d -s "AndroidRuntime:E"

# Clear logcat and watch live (filter errors and app logs)
adb logcat -c && adb logcat | grep -E "com.cactuvi.app|ERROR|FATAL"

# Monitor memory usage
adb shell dumpsys meminfo com.cactuvi.app

# Force stop app
adb shell am force-stop com.cactuvi.app

# Launch app
adb shell am start -n com.cactuvi.app/.ui.LoadingActivity
```

### Manual Testing & Verification Commands

#### App Installation & Reset
```bash
# Build, install, and start fresh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.cactuvi.app  # Clear all app data
adb shell am start -n com.cactuvi.app/.ui.LoadingActivity

# Quick reinstall from clean state
adb uninstall com.cactuvi.app
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Log Monitoring & Analysis
```bash
# Monitor performance logs (streaming optimization)
adb logcat -c && adb logcat | grep -E "IPTV_PERF|StreamingJsonParser"

# Watch specific component logs
adb logcat | grep "MoviesFragment"
adb logcat | grep "ContentRepository"
adb logcat | grep "LoadingActivity"

# Filter by log level
adb logcat *:E  # Errors only
adb logcat *:W  # Warnings and above
adb logcat *:D  # Debug and above

# Save logs to file for analysis
adb logcat -d > logs.txt
adb logcat -d | grep "IPTV_PERF" > perf_logs.txt
```

#### UI Inspection & Screenshots
```bash
# Take screenshot
adb exec-out screencap -p > screenshot.png

# Take screenshot with timestamp
adb exec-out screencap -p > "screenshot_$(date +%Y%m%d_%H%M%S).png"

# View UI hierarchy (XML dump)
adb shell uiautomator dump /sdcard/hierarchy.xml
adb pull /sdcard/hierarchy.xml .
cat hierarchy.xml  # Inspect element structure

# Extract UI element bounds for tap automation
# Example: Find button bounds and tap center
grep -o 'text="Save Source"[^>]*bounds="\[[0-9,\[\] ]*\]"' hierarchy.xml
# Output: bounds="[42,1222][1038,1357]" means tap at center: (540, 1290)

# View current activity
adb shell dumpsys activity activities | grep "mResumedActivity"

# View current fragment
adb shell dumpsys activity com.cactuvi.app | grep "Fragment"
```

**UI Automation Workflow:**
1. Dump hierarchy: `adb shell uiautomator dump /sdcard/hierarchy.xml && adb pull /sdcard/hierarchy.xml .`
2. Find element bounds: `grep 'text="Button Text"' hierarchy.xml` or `grep 'resource-id="button_id"' hierarchy.xml`
3. Calculate tap coordinates: Center of bounds `[x1,y1][x2,y2]` = `((x1+x2)/2, (y1+y2)/2)`
4. Perform tap: `adb shell input tap <x> <y>`
5. Verify with screenshot: `adb exec-out screencap -p > after_tap.png`

#### User Interaction Simulation
```bash
# Tap at coordinates (x, y)
adb shell input tap 500 1000

# Swipe gesture (x1 y1 x2 y2 duration_ms)
adb shell input swipe 500 1500 500 500 300  # Swipe up
adb shell input swipe 500 500 500 1500 300  # Swipe down
adb shell input swipe 900 800 100 800 300   # Swipe left
adb shell input swipe 100 800 900 800 300   # Swipe right

# Type text
adb shell input text "Hello"

# Press hardware buttons
adb shell input keyevent KEYCODE_BACK        # Back button
adb shell input keyevent KEYCODE_HOME        # Home button
adb shell input keyevent KEYCODE_MENU        # Menu button
adb shell input keyevent KEYCODE_ENTER       # Enter/OK
adb shell input keyevent KEYCODE_DPAD_UP     # D-pad up
adb shell input keyevent KEYCODE_DPAD_DOWN   # D-pad down
adb shell input keyevent KEYCODE_DPAD_LEFT   # D-pad left
adb shell input keyevent KEYCODE_DPAD_RIGHT  # D-pad right
adb shell input keyevent KEYCODE_DPAD_CENTER # D-pad select
```

#### Complete Verification Workflow Example
```bash
# 1. Build and install fresh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. Clear app data for clean test
adb shell pm clear com.cactuvi.app

# 3. Start performance monitoring
adb logcat -c
adb logcat | grep "IPTV_PERF" > perf_test.log &
LOGCAT_PID=$!

# 4. Launch app
adb shell am start -n com.cactuvi.app/.ui.LoadingActivity

# 5. Take screenshot at key points
sleep 3
adb exec-out screencap -p > step1_loading.png

sleep 5
adb exec-out screencap -p > step2_add_source.png

# 6. Simulate user input (tap Save button at coordinates)
adb shell input tap 540 1800

# 7. Wait for data load and capture final state
sleep 180  # Wait 3 minutes for full data load
adb exec-out screencap -p > step3_home.png

# 8. Stop logging and analyze
kill $LOGCAT_PID
grep "Repository.getMovies.*END" perf_test.log
grep "inserted=" perf_test.log

# 9. Check UI hierarchy
adb shell uiautomator dump /sdcard/hierarchy.xml
adb pull /sdcard/hierarchy.xml .

# 10. Verify database content (pull database first - see "Database Debugging & Inspection" section)
adb shell "run-as com.cactuvi.app cat /data/user/0/com.cactuvi.app/databases/iptv_database" > /tmp/iptv_database.db
sqlite3 /tmp/iptv_database.db "SELECT COUNT(*) FROM movies;"
```

#### TV D-Pad Navigation Testing
```bash
# Navigate through app using D-pad
adb shell input keyevent KEYCODE_DPAD_DOWN   # Move focus down
adb shell input keyevent KEYCODE_DPAD_CENTER # Select item
sleep 2
adb exec-out screencap -p > navigation_test.png

# Navigate back
adb shell input keyevent KEYCODE_BACK
```

#### Performance & Memory Profiling
```bash
# Continuous memory monitoring
watch -n 1 'adb shell dumpsys meminfo com.cactuvi.app | grep -E "TOTAL|Native Heap"'

# CPU usage
adb shell top -n 1 | grep com.cactuvi.app

# Network traffic
adb shell "dumpsys package com.cactuvi.app | grep -A 5 'Network'"

# Database size
adb shell "run-as com.cactuvi.app du -h /data/data/com.cactuvi.app/databases/"
```

#### Database Debugging & Inspection
```bash
# Pull database for local inspection (sqlite3 not available on device)
# Use run-as to copy db to a world-readable location
adb shell "run-as com.cactuvi.app cp /data/user/0/com.cactuvi.app/databases/iptv_database /sdcard/iptv_database.db"

# Then pull it to local machine
adb pull /sdcard/iptv_database.db /tmp/iptv_database.db

# Query locally with sqlite3
sqlite3 /tmp/iptv_database.db ".tables"
sqlite3 /tmp/iptv_database.db "SELECT COUNT(*) FROM movies;"
sqlite3 /tmp/iptv_database.db "SELECT * FROM cache_metadata;"
sqlite3 /tmp/iptv_database.db "SELECT categoryId, COUNT(*) as count FROM movies GROUP BY categoryId LIMIT 10;"

# Alternative: Use cat to pull (if cp fails)
adb shell "run-as com.cactuvi.app cat /data/user/0/com.cactuvi.app/databases/iptv_database" > /tmp/iptv_database.db

# Clean up
adb shell rm /sdcard/iptv_database.db
```

## Code Style Guidelines

### File Organization
```
app/src/main/java/com/iptv/app/
├── data/               # Data layer
│   ├── api/           # Retrofit API clients
│   ├── db/            # Room database (entities, DAOs, mappers)
│   ├── models/        # Domain models (NOT database entities)
│   └── repository/    # Repository implementations
├── ui/                # UI layer
│   ├── common/        # Shared adapters & custom views
│   ├── [feature]/     # Feature-specific screens (movies, series, live, etc.)
│   ├── detail/        # Detail screens
│   └── player/        # Video player
├── services/          # Background services (downloads, etc.)
└── utils/             # Utility classes
```

### Import Organization
**Order:** Android → AndroidX → Third-party → Kotlin → Java → App-specific

```kotlin
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.cactuvi.app.R
import com.cactuvi.app.data.models.Movie
import com.cactuvi.app.data.repository.ContentRepository
```

**Important:** When using both `kotlinx.coroutines.flow.map` and `androidx.paging.map`, alias one:
```kotlin
import kotlinx.coroutines.flow.map as flowMap
import androidx.paging.map
```

### Naming Conventions

#### Variables & Properties
- **UI components:** Descriptive names with type suffix: `recyclerView`, `progressBar`, `errorText`
- **Adapters:** Type-based naming: `movieAdapter`, `contentAdapter`, `groupAdapter`
- **Data collections:** Plural nouns: `allMovies`, `categories`, `channels`
- **Booleans:** Prefixed with `is`, `has`, `should`: `isFavorite`, `hasError`, `shouldRefresh`

#### Functions
- **Actions:** Verb-noun pattern: `loadData()`, `showLoading()`, `updateBreadcrumb()`
- **Navigation:** Descriptive verbs: `openMovieDetail()`, `showCategories()`, `handleBackPress()`
- **Data operations:** Clear intent: `getMoviesPaged()`, `addToFavorites()`, `clearCache()`

#### Classes
- **Activities:** Feature + `Activity`: `MovieDetailActivity`, `PlayerActivity`
- **Fragments:** Feature + `Fragment`: `MoviesFragment`, `SeriesFragment`
- **Adapters:** Type + `Adapter`: `MoviePagingAdapter`, `CategoryTreeAdapter`
- **Entities:** Model + `Entity`: `MovieEntity`, `SeriesEntity`
- **DAOs:** Model + `Dao`: `MovieDao`, `CategoryDao`

### Code Formatting

#### Indentation & Spacing
- **Indentation:** 4 spaces (NOT tabs)
- **Max line length:** 120 characters
- **Blank lines:** One blank line between methods, two between classes

#### Kotlin-Specific Conventions
```kotlin
// ✅ Good: Expression body for simple functions
private fun isCacheValid(lastUpdated: Long, ttl: Long): Boolean =
    (System.currentTimeMillis() - lastUpdated) < ttl

// ✅ Good: Named parameters for clarity
database.movieDao().updateResumePosition(streamId = movie.id, position = 5000L)

// ✅ Good: Trailing comma in multi-line lists
val entities = listOf(
    movie1,
    movie2,
    movie3,
)

// ✅ Good: Lazy initialization for expensive objects
private val contentAdapter: MoviePagingAdapter by lazy {
    MoviePagingAdapter { movie -> openMovieDetail(movie) }
}

// ❌ Bad: Unnecessary null checks with lateinit
private lateinit var repository: ContentRepository
if (::repository.isInitialized) { ... }  // Only check if truly needed
```

### Type Usage

#### Prefer Explicit Types When:
- Return types are not obvious from function name
- Nullable types are involved
- Working with generics or complex types

```kotlin
// ✅ Good: Explicit return type for clarity
suspend fun getMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) { ... }

// ✅ Good: Explicit nullable type
var selectedCategory: Category? = null

// ✅ Good: Type inference for obvious cases
val movies = repository.getMovies()
val count = 10
```

### Error Handling

#### Repository Layer (Data Operations)
**Always return `Result<T>` for suspend functions that can fail:**
```kotlin
suspend fun getMovies(forceRefresh: Boolean = false): Result<List<Movie>> =
    withContext(Dispatchers.IO) {
        try {
            // Try cache first
            val cached = database.movieDao().getAll()
            if (!forceRefresh && cached.isNotEmpty()) {
                return@withContext Result.success(cached.map { it.toModel() })
            }
            
            // Fetch from API
            val movies = getApiService().getVodStreams(username, password)
            database.movieDao().insertAll(movies.map { it.toEntity() })
            Result.success(movies)
        } catch (e: Exception) {
            // Fallback to cache on error
            val cached = database.movieDao().getAll()
            if (cached.isNotEmpty()) {
                Result.success(cached.map { it.toModel() })
            } else {
                Result.failure(e)
            }
        }
    }
```

#### UI Layer (Fragments/Activities)
**Handle Result types with isSuccess/isFailure:**
```kotlin
lifecycleScope.launch {
    val result = repository.getMovies()
    if (result.isSuccess) {
        val movies = result.getOrNull() ?: emptyList()
        updateUI(movies)
    } else {
        showError()
    }
}
```

### Paging 3 Implementation

#### DAO Layer - Return PagingSource
```kotlin
@Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY num ASC")
fun getByCategoryIdPaged(categoryId: String): PagingSource<Int, MovieEntity>
```

#### Repository Layer - Return Flow<PagingData<T>>
```kotlin
fun getMoviesPaged(categoryId: String? = null): Flow<PagingData<Movie>> {
    val pagingSourceFactory = {
        if (categoryId != null) {
            database.movieDao().getByCategoryIdPaged(categoryId)
        } else {
            database.movieDao().getAllPaged()
        }
    }
    
    return Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            prefetchDistance = 10
        ),
        pagingSourceFactory = pagingSourceFactory
    ).flow.flowMap { pagingData ->
        pagingData.map { entity -> entity.toModel() }
    }
}
```

#### UI Layer - Collect and Submit PagingData
```kotlin
private val contentAdapter: MoviePagingAdapter by lazy {
    MoviePagingAdapter { movie -> openMovieDetail(movie) }
}

private fun loadMovies(categoryId: String) {
    lifecycleScope.launch {
        repository.getMoviesPaged(categoryId = categoryId)
            .collectLatest { pagingData ->
                contentAdapter.submitData(pagingData)
            }
    }
}
```

### Comments & Documentation

- **When to comment:** Complex business logic, non-obvious decisions, workarounds
- **What NOT to comment:** Self-explanatory code
- **Section headers:** Use in large files to organize logical sections

```kotlin
// ========== AUTHENTICATION ==========

// ========== PAGING METHODS ==========
```

## Common Patterns

### ViewBinding Setup
```kotlin
class MoviesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_movies, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        // ... initialize other views
    }
}
```

### Repository Instantiation
```kotlin
private lateinit var repository: ContentRepository

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    repository = ContentRepository(
        CredentialsManager.getInstance(requireContext()),
        requireContext()
    )
}
```

### RecyclerView with Paging Adapter
```kotlin
private val adapter: MoviePagingAdapter by lazy {
    MoviePagingAdapter { movie -> onMovieClick(movie) }
}

private fun setupRecyclerView() {
    recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
    recyclerView.adapter = adapter
}
```

## Testing Guidelines

- **Unit tests:** Repository logic, utilities, mappers
- **Instrumented tests:** Database operations, UI interactions
- **Test naming:** `should[ExpectedBehavior]When[Condition]`

```kotlin
@Test
fun shouldReturnCachedMoviesWhenCacheIsValid() { ... }
```

## Common Pitfalls to Avoid

❌ **Don't** use `findViewById` in Activities - use ViewBinding  
❌ **Don't** perform network/database operations on main thread  
❌ **Don't** use `!!` (non-null assertion) - handle nullability properly  
❌ **Don't** commit sensitive data (API keys, credentials) to git  
❌ **Don't** mix old adapters (RecyclerView.Adapter) with Paging 3 - use PagingDataAdapter  

✅ **Do** use `withContext(Dispatchers.IO)` for I/O operations  
✅ **Do** return `Result<T>` for failable operations  
✅ **Do** use lazy initialization for expensive objects  
✅ **Do** follow the existing navigation tree pattern for hierarchical browsing  
✅ **Do** cache data with TTL to reduce API calls  

## Key Dependencies

- **Room:** 2.6.1 (with Paging support)
- **Paging 3:** 3.2.1
- **Retrofit:** 2.9.0
- **ExoPlayer (Media3):** 1.2.1
- **Glide:** 4.16.0
- **Coroutines:** 1.7.3

## Mobile Testing with Mobile-MCP

This project uses Mobile-MCP for automated mobile app testing via OpenCode.

### Prerequisites
- **Android Emulator:** `Medium_Phone` (available via `emulator -list-avds`)
- **Node.js:** v20+ (already installed)
- **Android SDK:** With Platform Tools at `/Users/nlinakis/Library/Android/sdk`
- **Package Name:** `com.cactuvi.app`

### Emulator Management

**Option 1: Auto-start via Mobile-MCP (Recommended)**
```
Start the Medium_Phone emulator
```
Mobile-MCP will start the emulator automatically.

**Option 2: Manual start**
```bash
emulator -avd Medium_Phone &
adb wait-for-device
adb devices  # Verify connection
```

### Available Mobile-MCP Tools (20+ Tools)

#### Device Management
- `mobile_list_available_devices` - List all iOS/Android devices
- `mobile_get_screen_size` - Get device dimensions
- `mobile_get_orientation` - Get portrait/landscape state
- `mobile_set_orientation` - Change orientation

#### App Management
- `mobile_list_apps` - List installed apps
- `mobile_launch_app` - Launch app by package name
- `mobile_terminate_app` - Stop running app
- `mobile_install_app` - Install APK/IPA
- `mobile_uninstall_app` - Remove app

#### Screen Interaction
- `mobile_take_screenshot` - Capture screen
- `mobile_save_screenshot` - Save screenshot to file
- `mobile_list_elements_on_screen` - Get accessibility tree
- `mobile_click_on_screen_at_coordinates` - Tap coordinates
- `mobile_double_tap_on_screen` - Double-tap
- `mobile_long_press_on_screen_at_coordinates` - Long press
- `mobile_swipe_on_screen` - Swipe gestures

#### Input & Navigation
- `mobile_type_keys` - Type text with optional submit
- `mobile_press_button` - Hardware keys (BACK, HOME, VOLUME, DPAD, etc.)
- `mobile_open_url` - Open URLs in browser

### Common Testing Patterns

#### Basic Navigation Flow
```
Test Movies navigation:
1. Launch com.cactuvi.app
2. Wait 2 seconds for home screen
3. List all elements on screen
4. Click on Movies section
5. Verify Movies screen loaded
```

#### D-Pad Navigation (Critical for TV)
```
Test D-pad navigation in Movies:
1. Launch com.cactuvi.app
2. Navigate to Movies section
3. Press DPAD_DOWN 3 times
4. Take screenshot to verify focus moved
5. Press DPAD_RIGHT
6. Press DPAD_CENTER to select
7. Verify detail screen opened
8. Press BACK to return
```

#### Fragment Back-Stack Testing
```
Test navigation back-stack:
1. Navigate: Movies → Group → Category → Content
2. Take screenshot at each level
3. Press BACK button
4. Verify at Category level
5. Press BACK again
6. Verify at Group level
```

#### Player Integration Test
```
Test video player:
1. Launch app
2. Navigate to Live TV
3. Click first channel
4. Wait 3 seconds for player initialization
5. Take screenshot to verify player controls
6. Press BACK to exit
7. Verify app didn't crash
```

#### Accessibility Tree Analysis
```
Analyze accessibility for Movies screen:
1. Launch com.cactuvi.app
2. Navigate to Movies
3. List all elements with accessibility properties
4. Verify all interactive elements are focusable
5. Check content descriptions exist for images
6. Verify logical focus order
```

### Installation & Build Commands

**Build Debug APK:**
```bash
cd /Users/nlinakis/Development/iptv/iptv-app
./gradlew assembleDebug
```

**Install via ADB:**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Install via Mobile-MCP:**
```
Install the APK at app/build/outputs/apk/debug/app-debug.apk
```

### TV D-Pad Button References

Hardware keys for D-pad testing:
- `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT` - Navigation
- `DPAD_CENTER` or `ENTER` - Select/confirm
- `BACK` - Navigate back
- `HOME` - Go to Android home
- `MENU` - Open menu
- `VOLUME_UP`, `VOLUME_DOWN` - Volume control

### Navigation Tree Structure
The app uses a hierarchical navigation pattern:
```
Home → Movies → Groups → Categories → Content → Movie Detail
     → Series → Groups → Categories → Content → Series Detail → Episodes
     → Live TV → Categories → Channels → Player
     → My List → Content
```

Always verify back-stack works correctly at each level.

### Troubleshooting

**Emulator not starting:**
```bash
# Check available emulators
emulator -list-avds

# Start manually
emulator -avd Medium_Phone &
```

**ADB connection issues:**
```bash
# Restart ADB server
adb kill-server
adb start-server
adb devices
```

**App not launching:**
```bash
# Verify app is installed
adb shell pm list packages | grep com.cactuvi.app

# Check logcat for errors
adb logcat | grep "com.cactuvi.app"
```

**Mobile-MCP not responding:**
- Restart OpenCode
- Check `~/.opencode` logs
- Verify `npx @mobilenext/mobile-mcp@latest` works standalone

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd sync
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
- ALWAYS use BD for task tracking (see "CRITICAL: Task Tracking with BD" section above)
