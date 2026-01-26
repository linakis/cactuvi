package com.cactuvi.app.data.models

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int,
    val childrenCount: Int = 0, // Pre-computed count of direct children (or content items for leaf)
    val isLeaf: Boolean = false, // True if this category contains content items, not subcategories
)
