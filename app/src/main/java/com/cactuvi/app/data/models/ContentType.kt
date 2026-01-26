package com.cactuvi.app.data.models

/**
 * Content type enum for type-safe content category management. Maps to database "type" field
 * values.
 */
enum class ContentType(val value: String) {
    MOVIES("vod"),
    SERIES("series"),
    LIVE("live");

    companion object {
        fun fromString(value: String): ContentType =
            values().find { it.value == value }
                ?: throw IllegalArgumentException("Unknown content type: $value")
    }
}
