package com.cactuvi.app.domain.model

/** Domain model for content categories. Separate from data layer Category entity. */
data class ContentCategory(
    val categoryId: String,
    val categoryName: String,
    val parentId: Int,
    val itemCount: Int = 0,
)
