package com.cactuvi.app.domain.usecase

import com.cactuvi.app.domain.model.Resource
import com.cactuvi.app.data.models.Category
import com.cactuvi.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observe live TV categories.
 * Returns reactive Flow that emits Resource states (Loading, Success, Error).
 * 
 * ViewModels should collect this Flow to reactively update UI based on data state.
 */
class ObserveLiveUseCase @Inject constructor(
    private val contentRepository: ContentRepository
) {
    operator fun invoke(): Flow<Resource<List<Category>>> {
        return contentRepository.observeLive()
    }
}
