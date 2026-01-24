package com.cactuvi.app.data.models

import com.google.gson.annotations.SerializedName

data class Series(
    val num: Int,
    val name: String,
    @SerializedName("series_id")
    val seriesId: Int,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("last_modified")
    val lastModified: String?,
    val rating: String?,
    @SerializedName("rating_5based")
    val rating5Based: Double?,
    @SerializedName("backdrop_path")
    val backdropPath: List<String>?,
    @SerializedName("youtube_trailer")
    val youtubeTrailer: String?,
    @SerializedName("episode_run_time")
    val episodeRunTime: String?,
    @SerializedName("category_id")
    val categoryId: String,
    
    // Local properties
    @Transient
    var isFavorite: Boolean = false,
    @Transient
    var categoryName: String = ""
)

data class SeriesInfo(
    val seasons: List<Season>?,
    val info: SeriesDetails?,
    val episodes: Map<String, List<Episode>>?
)

data class SeriesDetails(
    val name: String?,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("youtube_trailer")
    val youtubeTrailer: String?,
    val rating: String?,
    @SerializedName("episode_run_time")
    val episodeRunTime: String?
)

data class Season(
    @SerializedName("air_date")
    val airDate: String?,
    @SerializedName("episode_count")
    val episodeCount: Int,
    val id: Int,
    val name: String,
    val overview: String?,
    @SerializedName("season_number")
    val seasonNumber: Int,
    @SerializedName("cover")
    val cover: String?,
    @SerializedName("cover_big")
    val coverBig: String?
)

data class Episode(
    val id: String,
    @SerializedName("episode_num")
    val episodeNum: Int,
    val title: String,
    @SerializedName("container_extension")
    val containerExtension: String,
    val info: EpisodeInfo?,
    @SerializedName("custom_sid")
    val customSid: String?,
    @SerializedName("added")
    val added: String?,
    @SerializedName("season")
    val season: Int,
    @SerializedName("direct_source")
    val directSource: String?
)

data class EpisodeInfo(
    @SerializedName("air_date")
    val airDate: String?,
    @SerializedName("crew")
    val crew: String?,
    @SerializedName("rating")
    val rating: Double?,
    val name: String?,
    val overview: String?,
    @SerializedName("season_number")
    val seasonNumber: Int?,
    @SerializedName("episode_num")
    val episodeNum: Int?,
    @SerializedName("duration_secs")
    val durationSecs: Int?,
    val duration: String?,
    @SerializedName("movie_image")
    val movieImage: String?
)
