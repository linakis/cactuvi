package com.cactuvi.app.domain.usecase

/**
 * Base interface for use cases.
 * Invoke operator makes use cases callable like functions.
 * 
 * Example:
 * ```
 * class GetMoviesUseCase : UseCase<CategoryId, List<Movie>> {
 *     override suspend fun invoke(params: CategoryId): List<Movie> {
 *         // Implementation
 *     }
 * }
 * 
 * // Usage:
 * val movies = getMoviesUseCase(categoryId)
 * ```
 */
interface UseCase<in Params, out Result> {
    suspend operator fun invoke(params: Params): Result
}

/**
 * Use case with no parameters
 * 
 * Example:
 * ```
 * class RefreshDataUseCase : NoParamsUseCase<Unit> {
 *     override suspend fun invoke() {
 *         // Implementation
 *     }
 * }
 * 
 * // Usage:
 * refreshDataUseCase()
 * ```
 */
interface NoParamsUseCase<out Result> {
    suspend operator fun invoke(): Result
}
