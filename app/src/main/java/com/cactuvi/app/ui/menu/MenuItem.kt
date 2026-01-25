package com.cactuvi.app.ui.menu

data class MenuItem(
    val id: String,
    val title: String,
    val icon: Int,
    val action: () -> Unit,
)
