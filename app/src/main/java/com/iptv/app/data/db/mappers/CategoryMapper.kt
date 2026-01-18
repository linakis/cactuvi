package com.iptv.app.data.db.mappers

import com.iptv.app.data.db.entities.CategoryEntity
import com.iptv.app.data.models.Category

fun Category.toEntity(type: String): CategoryEntity {
    return CategoryEntity(
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
