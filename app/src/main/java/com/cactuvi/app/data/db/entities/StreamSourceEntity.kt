package com.cactuvi.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cactuvi.app.data.models.StreamSource

@Entity(
    tableName = "stream_sources",
    indices = [Index(value = ["isActive"])],
)
data class StreamSourceEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val server: String,
    val username: String,
    val password: String,
    val isActive: Boolean,
    val isPrimary: Boolean,
    val createdAt: Long,
    val lastUsed: Long?,
) {
    fun toModel(): StreamSource =
        StreamSource(
            id = id,
            nickname = nickname,
            server = server,
            username = username,
            password = password,
            isActive = isActive,
            isPrimary = isPrimary,
            createdAt = createdAt,
            lastUsed = lastUsed,
        )
}

fun StreamSource.toEntity(): StreamSourceEntity =
    StreamSourceEntity(
        id = id,
        nickname = nickname,
        server = server,
        username = username,
        password = password,
        isActive = isActive,
        isPrimary = isPrimary,
        createdAt = createdAt,
        lastUsed = lastUsed,
    )
