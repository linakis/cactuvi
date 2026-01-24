package com.iptv.app.data.models

/**
 * Sealed class representing the state of data loading.
 * Follows reactive state management principles for UI updates.
 * 
 * Used with StateFlow to provide a single source of truth for loading state.
 */
sealed class DataState<out T> {
    /**
     * Loading state - data is being fetched
     */
    object Loading : DataState<Nothing>()
    
    /**
     * Success state - data loaded successfully
     * @param data The loaded data
     */
    data class Success<T>(val data: T) : DataState<T>()
    
    /**
     * Error state - loading failed
     * @param error The error that occurred
     * @param cachedData Optional cached data that can be displayed
     */
    data class Error(val error: Throwable, val cachedData: Any? = null) : DataState<Nothing>()
    
    /**
     * Check if this state is loading
     */
    fun isLoading(): Boolean = this is Loading
    
    /**
     * Check if this state is success
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * Check if this state is error
     */
    fun isError(): Boolean = this is Error
    
    /**
     * Get data if available (from Success or Error with cached data)
     */
    fun getDataOrNull(): T? = when (this) {
        is Success -> data
        is Error -> cachedData as? T
        is Loading -> null
    }
}
