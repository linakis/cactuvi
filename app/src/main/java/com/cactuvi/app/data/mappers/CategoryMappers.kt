package com.cactuvi.app.data.mappers

import com.cactuvi.app.data.models.Category
import com.cactuvi.app.domain.model.ContentCategory

/**
 * Convert data layer Category to domain ContentCategory
 */
fun Category.toDomain(): ContentCategory = ContentCategory(
    categoryId = categoryId,
    categoryName = categoryName,
    parentId = parentId
)

/**
 * Convert list of Categories to domain models
 */
fun List<Category>.toDomain(): List<ContentCategory> = map { it.toDomain() }
