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

### Application Setup

```kotlin
@HiltAndroidApp
class IPTVApplication : Application()
```

### Modules

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = AppDatabase.getInstance(context)
    
    @Provides
    @Singleton
    fun provideContentRepository(
        @ApplicationContext context: Context
    ): ContentRepository = ContentRepositoryImpl(context)
}
```

### Usage in ViewModel

```kotlin
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val observeMoviesUseCase: ObserveMoviesUseCase,
    private val refreshMoviesUseCase: RefreshMoviesUseCase
) : ViewModel()
```

### Usage in Fragment

```kotlin
@AndroidEntryPoint
class MoviesFragment : Fragment() {
    private val viewModel: MoviesViewModel by viewModels()
}
```

## Testing Strategy

### ViewModel Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var observeMoviesUseCase: ObserveMoviesUseCase
    private lateinit var refreshMoviesUseCase: RefreshMoviesUseCase
    private lateinit var viewModel: MoviesViewModel
    
    @Before
    fun setup() {
        observeMoviesUseCase = mockk()
        refreshMoviesUseCase = mockk(relaxed = true)
        
        every { observeMoviesUseCase() } returns flowOf(
            Resource.Loading(),
            Resource.Success(mockNavigationTree)
        )
        
        viewModel = MoviesViewModel(observeMoviesUseCase, refreshMoviesUseCase)
    }
    
    @Test
    fun `observes movies and updates state`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertFalse(initial.isLoading)
            
            val loading = awaitItem()
            assertTrue(loading.isLoading)
            
            val success = awaitItem()
            assertFalse(success.isLoading)
            assertNotNull(success.navigationTree)
        }
    }
    
    @Test
    fun `refresh triggers use case`() = runTest {
        viewModel.refresh()
        
        coVerify { refreshMoviesUseCase() }
    }
}
```

### UseCase Tests

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

### Test Dependencies

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("app.cash.turbine:turbine:1.0.0")
```

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
    ├── DataModule.kt                # Repository, Database
    └── AppModule.kt                 # App-level singletons
```

## Migration Path

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
