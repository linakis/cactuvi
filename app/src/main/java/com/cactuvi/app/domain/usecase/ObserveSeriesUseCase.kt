package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observe series navigation tree.
 * Returns reactive Flow that emits Resource states (Loading, Success, Error).
 * 
 * ViewModels should collect this Flow to reactively update UI based on data state.
 * 
 * Note: @Inject annotation will be added in Phase 1.4 (Hilt setup)
 */
class ObserveSeriesUseCase(
    private val contentRepository: ContentRepository
) {
    operator fun invoke(): Flow<Resource<NavigationTree>> {
        return contentRepository.observeSeries()
    }
}
