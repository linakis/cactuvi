package com.cactuvi.app.domain.model

/**
 * Wrapper for data that can be in different states (Loading, Success, Error).
 * Preserves type information and supports showing cached data during loads/errors.
 */
sealed class Resource<out T> {
    /**
     * Loading state - data is being fetched.
     * @param data Optional cached data to show while loading
     * @param progress Optional progress percentage (0-100), null for indeterminate
     */
    data class Loading<T>(
        val data: T? = null,
        val progress: Int? = null
    ) : Resource<T>()
    
    /**
     * Success state - data loaded successfully.
     * @param data The loaded data
     * @param source Where data came from (cache or network)
     */
    data class Success<T>(
        val data: T,
        val source: DataSource = DataSource.CACHE
    ) : Resource<T>()
    
    /**
     * Error state - loading failed.
     * @param error The exception that occurred
     * @param data Optional stale cached data to show
     */
    data class Error<T>(
        val error: Throwable,
        val data: T? = null
    ) : Resource<T>()
    
    /**
     * Get data regardless of state (from Loading.data, Success.data, or Error.data)
     */
    fun getDataOrNull(): T? = when (this) {
        is Loading -> data
        is Success -> data
        is Error -> data
    }
    
    /**
     * Check if this is a loading state
     */
    fun isLoading(): Boolean = this is Loading
    
    /**
     * Check if this is a success state
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * Check if this is an error state
     */
    fun isError(): Boolean = this is Error
}

/**
 * Data source indicator for Resource.Success
 */
enum class DataSource {
    CACHE,   // Data came from local cache
    NETWORK  // Data came from API
}
