package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.model.NavigationTree
import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observe live TV navigation tree.
 * Returns reactive Flow that emits Resource states (Loading, Success, Error).
 * 
 * Supports grouping based on user preferences (e.g., by language/region using "|" separator).
 * ViewModels should collect this Flow to reactively update UI based on data state.
 */
class ObserveLiveUseCase @Inject constructor(
    private val contentRepository: ContentRepository
) {
    operator fun invoke(): Flow<Resource<NavigationTree>> {
        return contentRepository.observeLive()
    }
}
