package com.iptv.app.data.db.mappers

import com.iptv.app.data.db.entities.CategoryEntity
import com.iptv.app.data.models.Category

fun Category.toEntity(sourceId: String, type: String): CategoryEntity {
    return CategoryEntity(
        sourceId = sourceId,
        categoryId = categoryId,
        categoryName = categoryName,
        type = type
    )
}

fun CategoryEntity.toModel(): Category {
    return Category(
        categoryId = categoryId,
        categoryName = categoryName,
        parentId = 0
    )
}
