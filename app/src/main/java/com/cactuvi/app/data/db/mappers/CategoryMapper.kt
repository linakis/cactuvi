package com.cactuvi.app.data.db.mappers

import com.cactuvi.app.data.db.entities.CategoryEntity
import com.cactuvi.app.data.models.Category

fun Category.toEntity(sourceId: String, type: String): CategoryEntity {
    return CategoryEntity(
        sourceId = sourceId,
        categoryId = categoryId,
        categoryName = categoryName,
        parentId = parentId,
        type = type,
    )
}

fun CategoryEntity.toModel(): Category {
    return Category(
        categoryId = categoryId,
        categoryName = categoryName,
        parentId = parentId,
    )
}
