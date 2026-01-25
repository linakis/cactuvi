package com.cactuvi.app.data.models

/**
 * Sealed class representing the state of data loading. Follows reactive state management principles
 * for UI updates.
 *
 * Used with StateFlow to provide a single source of truth for loading state.
 */
sealed class DataState<out T> {
    /**
     * Loading state - data is being fetched
     *
     * @param progress Optional progress percentage (0-100), null for indeterminate
     */
    data class Loading(val progress: Int? = null) : DataState<Nothing>()

    /**
     * Success state - data loaded successfully
     *
     * @param data The loaded data
     */
    data class Success<T>(val data: T) : DataState<T>()

    /**
     * Partial success state - some data loaded, some failed Used when async writes partially
     * succeed (e.g., 80k/90k items written)
     *
     * @param data The partial data that was successfully loaded
     * @param successCount Number of items successfully written
     * @param failedCount Number of items that failed to write
     * @param error The error that caused partial failure
     */
    data class PartialSuccess<T>(
        val data: T,
        val successCount: Int,
        val failedCount: Int,
        val error: Throwable,
    ) : DataState<T>()

    /**
     * Error state - loading failed completely
     *
     * @param error The error that occurred
     * @param cachedData Optional cached data that can be displayed
     */
    data class Error(val error: Throwable, val cachedData: Any? = null) : DataState<Nothing>()

    /** Check if this state is loading */
    fun isLoading(): Boolean = this is Loading

    /** Check if this state is success */
    fun isSuccess(): Boolean = this is Success

    /** Check if this state is error */
    fun isError(): Boolean = this is Error

    /** Get data if available (from Success, PartialSuccess, or Error with cached data) */
    fun getDataOrNull(): T? =
        when (this) {
            is Success -> data
            is PartialSuccess -> data
            is Error -> cachedData as? T
            is Loading -> null
        }
}
