# AGENTS.md - IPTV Android App Development Guide

This file contains essential information for AI coding agents working on this codebase.

## Communication Style

- **No summaries:** Do not produce summary sections after completing tasks
- **Be concise:** Brief confirmations are sufficient (e.g., "Done" or "Fixed")
- **Action-oriented:** Focus on doing the work, not explaining what was done
- **Ask when unclear:** If requirements are ambiguous, ask before implementing

## Project Overview

**Type:** Android IPTV streaming application (Netflix-style UI)  
**Language:** Kotlin  
**Min SDK:** 26 | **Compile SDK:** 35 | **Target SDK:** 34  
**Architecture:** Repository pattern with ViewModels, Fragment-based navigation  
**Key Libraries:** Room, Retrofit, Paging 3, ExoPlayer (Media3), Glide, Material 3

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
./gradlew test --tests "com.iptv.app.SpecificTestClass"

# Run single test method
./gradlew test --tests "com.iptv.app.SpecificTestClass.testMethodName"

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
import com.iptv.app.R
import com.iptv.app.data.models.Movie
import com.iptv.app.data.repository.ContentRepository
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
Use 'bd' for task tracking
