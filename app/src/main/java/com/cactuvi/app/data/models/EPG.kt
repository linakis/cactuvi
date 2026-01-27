package com.cactuvi.app.data.models

import com.google.gson.annotations.SerializedName

/** EPG (Electronic Program Guide) response models */
data class EPGResponse(
    @SerializedName("epg_listings") val epgListings: List<EPGListing>,
)

data class EPGListing(
    val id: String,
    @SerializedName("epg_id") val epgId: String,
    val title: String, // Base64 encoded
    val lang: String,
    val start: String, // Format: "YYYY-MM-DD HH:MM:SS"
    val end: String, // Format: "YYYY-MM-DD HH:MM:SS"
    val description: String, // Base64 encoded
    @SerializedName("channel_id") val channelId: String,
    @SerializedName("start_timestamp") val startTimestamp: String,
    @SerializedName("stop_timestamp") val stopTimestamp: String,
    @SerializedName("stream_id") val streamId: String? = null, // Only in short EPG
    @SerializedName("now_playing") val nowPlaying: Int? = null, // Only in full EPG (0 or 1)
    @SerializedName("has_archive") val hasArchive: Int? = null, // Only in full EPG (0 or 1)
)
