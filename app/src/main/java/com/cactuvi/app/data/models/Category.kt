package com.cactuvi.app.data.models

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int,
)
