package com.iptv.app.data.models

import com.google.gson.annotations.SerializedName

data class Movie(
    val num: Int,
    val name: String,
    @SerializedName("stream_type")
    val streamType: String?,
    @SerializedName("stream_id")
    val streamId: Int,
    @SerializedName("stream_icon")
    val streamIcon: String?,
    @SerializedName("rating")
    val rating: String?,
    @SerializedName("rating_5based")
    val rating5Based: Double?,
    @SerializedName("added")
    val added: String?,
    @SerializedName("category_id")
    val categoryId: String,
    @SerializedName("container_extension")
    val containerExtension: String,
    @SerializedName("custom_sid")
    val customSid: String?,
    @SerializedName("direct_source")
    val directSource: String?,
    
    // Local properties
    @Transient
    var isFavorite: Boolean = false,
    @Transient
    var categoryName: String = "",
    @Transient
    var resumePosition: Long = 0
)

data class MovieInfo(
    val info: MovieDetails?,
    @SerializedName("movie_data")
    val movieData: MovieData?
)

data class MovieDetails(
    val name: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("youtube_trailer")
    val youtubeTrailer: String?,
    val rating: String?,
    @SerializedName("duration_secs")
    val durationSecs: Int?,
    val duration: String?,
    @SerializedName("tmdb_id")
    val tmdbId: String?
)

data class MovieData(
    @SerializedName("stream_id")
    val streamId: Int,
    val name: String,
    @SerializedName("container_extension")
    val containerExtension: String
)
