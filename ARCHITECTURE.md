# IPTV App - Architecture Guide

## Overview

This app follows **MVVM (Model-View-ViewModel) + Unidirectional Data Flow (UDF) + Clean Architecture** principles for maintainable, testable, and scalable code.

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     UI Layer (Presentation)                  │
│  ┌────────────┐              ┌──────────────┐               │
│  │  Fragment  │ ─observes──> │  ViewModel   │               │
│  │            │              │              │               │
│  │  • Renders │              │  • UiState   │               │
│  │  • User    │              │  • Transform │               │
│  │    actions │              │    domain→UI │               │
│  └────────────┘              └──────────────┘               │
│         ↑                           ↓                        │
│         │                      (User Intents)                │
│         └───────────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (Business Logic)             │
│  ┌──────────────┐                                            │
│  │   UseCase    │                                            │
│  │              │                                            │
│  │  • Business  │                                            │
│  │    rules     │                                            │
│  │  • Data      │                                            │
│  │    transform │                                            │
│  └──────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer (Data Management)              │
│  ┌──────────────┐              ┌──────────────┐             │
│  │  Repository  │ ─composes──> │ DataSource   │             │
│  │              │              │              │             │
│  │  • Reactive  │              │ • Remote     │             │
│  │    streams   │              │ • Local      │             │
│  │  • Resource  │              │              │             │
│  │    wrapper   │              │              │             │
│  └──────────────┘              └──────────────┘             │
│         ↓                           ↓                        │
│   Flow<Resource<T>>          API / Database                 │
└─────────────────────────────────────────────────────────────┘
```

## Key Patterns

### 1. Resource Wrapper

Replaces imperative state flags with explicit state representation:

```kotlin
sealed class Resource<out T> {
    data class Loading<T>(
        val data: T? = null,        // Optional cached data
        val progress: Int? = null    // 0-100 or null
    ) : Resource<T>()
    
    data class Success<T>(
        val data: T,
        val source: DataSource = DataSource.CACHE
    ) : Resource<T>()
    
    data class Error<T>(
        val error: Throwable,
        val data: T? = null  // Stale cached data
    ) : Resource<T>()
}
```

**Benefits:**
- ✅ Preserves type information (vs `DataState<Unit>`)
- ✅ Shows cached data during loading/errors
- ✅ Explicit source tracking (CACHE vs NETWORK)
- ✅ No manual `isLoading` flags needed

### 2. Repository as Single Source of Truth

No `forceRefresh` flags - use reactive streams instead:

```kotlin
class ContentRepositoryImpl {
    private val movieRefreshTrigger = MutableSharedFlow<Unit>(replay = 1)
    
    // Expose reactive stream
    override fun observeMovies(): Flow<Resource<NavigationTree>> = 
        movieRefreshTrigger
            .onStart { emit(Unit) }  // Auto-trigger on subscribe
            .flatMapLatest {
                flow {
                    // Emit loading with cached data
                    val cached = getCachedMoviesTree()
                    emit(Resource.Loading(data = cached))
                    
                    try {
                        // Fetch from API
                        val fresh = fetchMoviesFromApi()
                        cache(fresh)
                        emit(Resource.Success(fresh, NETWORK))
                    } catch (e: Exception) {
                        if (cached != null) {
                            emit(Resource.Error(e, data = cached))
                        } else {
                            emit(Resource.Error(e))
                        }
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    
    // Explicit refresh trigger
    override suspend fun refreshMovies() {
        movieRefreshTrigger.emit(Unit)
    }
}
```

**Benefits:**
- ✅ No manual cache TTL checks
- ✅ Cache automatically shown during loads
- ✅ Reactive - updates flow to all observers
- ✅ Testable - pure data transformations

### 3. ViewModel with Single UiState

Transform domain state to UI state:

```kotlin
data class MoviesUiState(
    val navigationTree: NavigationTree? = null,
    val currentLevel: NavigationLevel = NavigationLevel.GROUPS,
    val selectedGroup: GroupNode? = null,
    val selectedCategory: Category? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    // Derived properties (no imperative checks!)
    val showLoading: Boolean get() = isLoading && navigationTree == null
    val showContent: Boolean get() = navigationTree != null
    val showError: Boolean get() = error != null && navigationTree == null
}

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val observeMoviesUseCase: ObserveMoviesUseCase,
    private val refreshMoviesUseCase: RefreshMoviesUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()
    
    init {
        observeMovies()
    }
    
    private fun observeMovies() {
        viewModelScope.launch {
            observeMoviesUseCase()
                .collectLatest { resource ->
                    _uiState.update { state ->
                        when (resource) {
                            is Resource.Loading -> state.copy(
                                isLoading = true,
                                navigationTree = resource.data,
                                error = null
                            )
                            is Resource.Success -> state.copy(
                                isLoading = false,
                                navigationTree = resource.data,
                                error = null
                            )
                            is Resource.Error -> state.copy(
                                isLoading = false,
                                navigationTree = resource.data,
                                error = if (resource.data == null) 
                                    resource.error.message else null
                            )
                        }
                    }
                }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            refreshMoviesUseCase()
        }
    }
}
```

**Benefits:**
- ✅ Single `UiState` - easy to reason about
- ✅ Derived properties - no `isLoading` flags
- ✅ Survives configuration changes
- ✅ Testable with fake use cases

### 4. Fragment as Pure UI Renderer

```kotlin
@AndroidEntryPoint
class MoviesFragment : Fragment() {
    private val viewModel: MoviesViewModel by viewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        
        // Single state observer
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    renderUiState(state)
                }
            }
        }
    }
    
    private fun renderUiState(state: MoviesUiState) {
        progressBar.isVisible = state.showLoading
        recyclerView.isVisible = state.showContent
        errorText.isVisible = state.showError
        errorText.text = state.error
        
        when (state.currentLevel) {
            NavigationLevel.GROUPS -> showGroups(state.navigationTree)
            NavigationLevel.CATEGORIES -> showCategories(state.selectedGroup)
            NavigationLevel.CONTENT -> showContent(state.selectedCategory)
        }
    }
}
```

**Benefits:**
- ✅ Fragment is pure UI renderer
- ✅ No business logic
- ✅ Single state flow
- ✅ No manual state tracking

## Dependency Injection with Hilt

All dependencies are managed by Hilt. **NO singletons with getInstance() pattern.**

### Architecture Flow

```
UI Layer (Fragments/Activities)
  ↓ @AndroidEntryPoint + @Inject / @HiltViewModel
ViewModels
  ↓ @Inject constructor
UseCases
  ↓ @Inject constructor
Repository Interface (domain layer)
  ↓ @Binds in DataModule
Repository Implementation (data layer)
  ↓ @Inject constructor
Managers + Data Sources
  ↓ @Inject constructor / @Provides
Room, Retrofit, SharedPreferences
```

### Application Setup

```kotlin
@HiltAndroidApp
class Cactuvi : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

### Hilt Modules

#### DataModule (provides Repository + API)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository

    companion object {
        @Provides
        @Singleton
        fun provideXtreamApiService(): XtreamApiService {
            return Retrofit.Builder()
                .baseUrl("https://placeholder.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(OkHttpClient.Builder().build())
                .build()
                .create(XtreamApiService::class.java)
        }
    }
}
```

#### ManagerModule (provides Database + Managers)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "iptv_database")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
    }
}
```

### Common Patterns

#### ViewModels

```kotlin
@HiltViewModel
class MoviesViewModel @Inject constructor(
    repository: ContentRepository,
    preferencesManager: PreferencesManager,
) : ContentViewModel<Movie>(repository, preferencesManager) {

    override fun getContentType(): ContentType = ContentType.MOVIES

    override fun getPagedContent(categoryId: String): Flow<PagingData<Movie>> {
        return repository.getMoviesPaged(categoryId)
    }
}
```

#### UseCases

```kotlin
class ObserveMoviesUseCase @Inject constructor(
    private val repository: ContentRepository
) {
    operator fun invoke(): Flow<Resource<NavigationTree>> = repository.observeMovies()
}
```

#### Repository Implementation

```kotlin
class ContentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val sourceManager: SourceManager,
    private val preferencesManager: PreferencesManager,
    private val credentialsManager: CredentialsManager,
    private val syncPreferencesManager: SyncPreferencesManager,
) : ContentRepository {
    // All dependencies explicit in constructor
}
```

#### Managers

```kotlin
@Singleton
class SourceManager @Inject constructor(
    private val database: AppDatabase,
    private val sharedPreferences: SharedPreferences,
) {
    // No getInstance() - Hilt manages lifecycle
}
```

#### Activities

```kotlin
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var repository: ContentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fields already injected, use directly
    }
}
```

#### Fragments

```kotlin
@AndroidEntryPoint
class MoviesFragment : ContentNavigationFragment<Movie>() {
    private val viewModel: MoviesViewModel by viewModels()

    @Inject lateinit var preferencesManager: PreferencesManager
}
```

#### Workers (Background Tasks)

```kotlin
@HiltWorker
class BackgroundSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ContentRepository,
    private val preferencesManager: PreferencesManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Dependencies already injected
    }
}
```

**Important:** Disable default WorkManager initializer in `AndroidManifest.xml`:
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="remove" />
```

### Injection Rules

| Class Type | Annotation | Injection Method |
|------------|------------|------------------|
| Application | `@HiltAndroidApp` | N/A |
| Activity | `@AndroidEntryPoint` | `@Inject lateinit var` |
| Fragment | `@AndroidEntryPoint` | `@Inject lateinit var` or `by viewModels()` |
| ViewModel | `@HiltViewModel` | `@Inject constructor` |
| UseCase | None | `@Inject constructor` |
| Repository | `@Singleton` on impl | `@Inject constructor` + `@Binds` |
| Manager | `@Singleton` | `@Inject constructor` |
| Worker | `@HiltWorker` | `@AssistedInject constructor` |

### What NOT to Do

```kotlin
// ❌ DON'T: Singleton pattern
companion object {
    @Volatile private var INSTANCE: SomeManager? = null
    fun getInstance(context: Context): SomeManager { ... }
}

// ❌ DON'T: Call getInstance() anywhere
val manager = SomeManager.getInstance(context)

// ❌ DON'T: Create dependencies manually
val repository = ContentRepositoryImpl(context, ...)
```

### What TO Do

```kotlin
// ✅ DO: @Inject constructor
class SomeManager @Inject constructor(
    private val database: AppDatabase,
) { ... }

// ✅ DO: Field injection in Android components
@Inject lateinit var manager: SomeManager

// ✅ DO: Constructor injection in ViewModels/UseCases
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: ContentRepository,
) : ViewModel()
```

## Testing Strategy

### Why Constructor Injection Enables Testing

With Hilt DI, all dependencies are passed via constructor. This makes mocking trivial:

```kotlin
// Production: Hilt injects real dependencies
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: ContentRepository,
) : ViewModel()

// Test: You inject mocks directly
val mockRepository = mockk<ContentRepository>()
val viewModel = MoviesViewModel(mockRepository)
```

### Unit Test Patterns

#### ViewModel Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockRepository: ContentRepository
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var viewModel: MoviesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockRepository = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)

        // Default behavior
        every { mockPreferencesManager.isMoviesGroupingEnabled() } returns true
        every { mockPreferencesManager.getMoviesGroupingSeparator() } returns "-"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct defaults`() {
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Categories(emptyList())

        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isLeafLevel)
    }

    @Test
    fun `refresh reloads content`() = runTest {
        coEvery { mockRepository.getTopLevelNavigation(any(), any(), any()) } returns
            NavigationResult.Categories(emptyList())
        viewModel = MoviesViewModel(mockRepository, mockPreferencesManager)

        viewModel.refresh()

        coVerify(atLeast = 2) { mockRepository.getTopLevelNavigation(any(), any(), any()) }
    }
}
```

#### Manager Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SourceManagerTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockDatabase: AppDatabase
    private lateinit var mockSourceDao: SourceDao
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var sourceManager: SourceManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockDatabase = mockk(relaxed = true)
        mockSourceDao = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)

        every { mockDatabase.sourceDao() } returns mockSourceDao

        sourceManager = SourceManager(mockDatabase, mockSharedPreferences)
    }

    @Test
    fun `getAllSources returns empty list when no sources`() = runTest {
        coEvery { mockSourceDao.getAll() } returns emptyList()

        val result = sourceManager.getAllSources()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `setActiveSource updates database and preferences`() = runTest {
        val source = StreamSource(id = "1", ...)
        coEvery { mockSourceDao.update(any()) } just Runs
        every { mockSharedPreferences.edit().putString(any(), any()).apply() } just Runs

        sourceManager.setActiveSource(source)

        coVerify { mockSourceDao.update(any()) }
    }
}
```

#### UseCase Tests

```kotlin
class ObserveMoviesUseCaseTest {
    private lateinit var repository: ContentRepository
    private lateinit var useCase: ObserveMoviesUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = ObserveMoviesUseCase(repository)
    }

    @Test
    fun `invoke delegates to repository`() = runTest {
        val mockFlow = flowOf(Resource.Success(mockNavigationTree))
        every { repository.observeMovies() } returns mockFlow

        val result = useCase()

        assertEquals(mockFlow, result)
        verify { repository.observeMovies() }
    }
}
```

### Flow Testing with Turbine

```kotlin
@Test
fun `observeMovies emits Loading then Success`() = runTest {
    every { mockRepository.observeMovies() } returns flowOf(
        Resource.Loading(),
        Resource.Success(mockNavigationTree)
    )

    useCase().test {
        val loading = awaitItem()
        assertTrue(loading is Resource.Loading)

        val success = awaitItem()
        assertTrue(success is Resource.Success)

        awaitComplete()
    }
}
```

### Test Dependencies

```kotlin
// build.gradle.kts
testImplementation("junit:junit:4.13.2")
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("app.cash.turbine:turbine:1.0.0")
```

### Running Tests

```bash
# Run all unit tests
./gradlew testProdDebugUnitTest

# Run specific test class
./gradlew testProdDebugUnitTest --tests "*.MoviesViewModelTest"

# Run tests with coverage
./gradlew testDebugUnitTest jacocoTestReport

# View coverage report
open app/build/reports/jacoco/jacocoTestReport/html/index.html
```

### Test Coverage Goals

| Layer | Target | Key Classes |
|-------|--------|-------------|
| ViewModel | 80%+ | MoviesViewModel, SeriesViewModel, LiveTvViewModel |
| UseCase | 80%+ | All UseCases in domain/usecase/ |
| Repository | 70%+ | ContentRepositoryImpl |
| Manager | 80%+ | SourceManager, PreferencesManager |
| Utility | 90%+ | CategoryTreeBuilder, StreamingJsonParser |

## File Structure

```
app/src/main/java/com/cactuvi/app/
├── domain/                          # Business logic layer
│   ├── model/                       # Domain models
│   │   ├── Resource.kt              # Resource<T> sealed class
│   │   ├── NavigationTree.kt        # Domain navigation model
│   │   └── ContentCategory.kt       # Domain category
│   ├── repository/                  # Repository interfaces
│   │   └── ContentRepository.kt     # Interface (contract)
│   └── usecase/                     # Business logic
│       ├── ObserveMoviesUseCase.kt
│       ├── RefreshMoviesUseCase.kt
│       ├── ObserveSeriesUseCase.kt
│       └── RefreshSeriesUseCase.kt
│
├── data/                            # Data layer
│   ├── repository/                  # Repository implementations
│   │   └── ContentRepositoryImpl.kt
│   ├── source/                      # Data sources
│   │   ├── remote/
│   │   │   ├── MovieRemoteDataSource.kt
│   │   │   ├── SeriesRemoteDataSource.kt
│   │   │   └── LiveRemoteDataSource.kt
│   │   └── local/
│   │       ├── MovieLocalDataSource.kt
│   │       ├── SeriesLocalDataSource.kt
│   │       └── LiveLocalDataSource.kt
│   ├── db/                          # Room database
│   ├── api/                         # Retrofit
│   ├── models/                      # Data models
│   └── mappers/                     # Entity ↔ Domain mappers
│       └── CategoryMappers.kt
│
├── ui/                              # UI layer
│   ├── movies/
│   │   ├── MoviesFragment.kt        # Pure UI renderer
│   │   ├── MoviesViewModel.kt       # State management
│   │   └── MoviesUiState.kt         # UI state model
│   ├── series/
│   │   ├── SeriesFragment.kt
│   │   ├── SeriesViewModel.kt
│   │   └── SeriesUiState.kt
│   └── live/
│       ├── LiveTvFragment.kt
│       ├── LiveTvViewModel.kt
│       └── LiveTvUiState.kt
│
└── di/                              # Dependency injection
    ├── DataModule.kt                # Repository binding, API service
    └── ManagerModule.kt             # Database, SharedPreferences, Managers
```

## Migration History

### DI Migration (January 2026) - Completed

Migrated from singleton pattern (`getInstance()`) to full Hilt dependency injection.

**What was migrated:**

| Component | Before | After |
|-----------|--------|-------|
| ContentRepositoryImpl | `getInstance(context)` | `@Inject constructor` |
| SourceManager | `getInstance(context)` | `@Inject constructor` + `@Singleton` |
| PreferencesManager | `getInstance(context)` | `@Inject constructor` + `@Singleton` |
| CredentialsManager | `getInstance(context)` | `@Inject constructor` + `@Singleton` |
| SyncPreferencesManager | `getInstance(context)` | `@Inject constructor` + `@Singleton` |
| ReactiveUpdateManager | `getInstance()` | `@Inject constructor` + `@Singleton` |
| BackgroundSyncWorker | Manual deps | `@HiltWorker` + `@AssistedInject` |
| All Activities | Manual init | `@AndroidEntryPoint` |
| All Fragments | Manual init | `@AndroidEntryPoint` |

**Benefits achieved:**
- ✅ 200+ unit tests now possible (constructor injection enables mocking)
- ✅ No more `getInstance()` calls scattered through codebase
- ✅ Clear dependency graph visible in constructors
- ✅ Proper lifecycle management by Hilt
- ✅ Testable Workers with injected dependencies

## Architecture Evolution

### Phase 1: Foundation
1. Create `Resource<T>` wrapper
2. Extract DataSources (Remote/Local)
3. Add UseCases layer
4. Set up Hilt DI

### Phase 2: Repository Refactor
1. Convert to reactive streams (`Flow<Resource<T>>`)
2. Remove `forceRefresh` flags
3. Deprecate old methods

### Phase 3: ViewModel Migration
1. Create ViewModels + UiState
2. Update Fragments to observe ViewModels
3. Remove business logic from Fragments

### Phase 4: Cleanup
1. Remove deprecated methods
2. Add comprehensive tests
3. Update documentation

## Common Pitfalls

### ❌ DON'T

```kotlin
// DON'T: Imperative state flags
var isLoading = false
var hasCache = false

// DON'T: Expose mutable state
val uiState: MutableStateFlow<UiState>

// DON'T: Call repository from Fragment
lifecycleScope.launch {
    repository.getMovies()
}

// DON'T: Manual cache checks
if (System.currentTimeMillis() - lastUpdate < TTL) {
    showCache()
}
```

### ✅ DO

```kotlin
// DO: Derive state from streams
data class UiState(
    val isLoading: Boolean,
    val data: Data?
) {
    val showLoading get() = isLoading && data == null
}

// DO: Expose immutable state
val uiState: StateFlow<UiState>

// DO: Use ViewModel + UseCase
viewModel.refresh()

// DO: Let repository handle caching
flow {
    emit(Loading(cachedData))
    emit(Success(freshData))
}
```

## Performance Considerations

### 1. StateFlow Conflation
- StateFlow drops intermediate values - perfect for UI state
- Use SharedFlow for one-time events (navigation, toasts)

### 2. viewModelScope
- Automatically canceled when ViewModel cleared
- Survives configuration changes

### 3. Flow Operators
- Use `flowOn(Dispatchers.IO)` for background work
- Use `collectLatest` to cancel previous collection on new emission

### 4. Lazy Initialization
```kotlin
private val adapter by lazy { MovieAdapter() }
```

## References

- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Guide to app architecture](https://developer.android.com/jetpack/guide)
- [Unidirectional Data Flow](https://developer.android.com/jetpack/compose/architecture#udf)
- [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android)
