package com.cactuvi.app.data.models

import java.util.UUID

data class StreamSource(
    val id: String = UUID.randomUUID().toString(),
    val nickname: String,
    val server: String,
    val username: String,
    val password: String,
    val isActive: Boolean = false,
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
)
