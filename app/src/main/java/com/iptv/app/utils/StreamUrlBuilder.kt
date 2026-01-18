package com.iptv.app.utils

object StreamUrlBuilder {
    
    fun buildLiveUrl(
        server: String,
        username: String,
        password: String,
        streamId: Int,
        extension: String = "ts"
    ): String {
        return "$server/live/$username/$password/$streamId.$extension"
    }
    
    fun buildMovieUrl(
        server: String,
        username: String,
        password: String,
        streamId: Int,
        extension: String
    ): String {
        return "$server/movie/$username/$password/$streamId.$extension"
    }
    
    fun buildSeriesUrl(
        server: String,
        username: String,
        password: String,
        episodeId: String,
        extension: String
    ): String {
        return "$server/series/$username/$password/$episodeId.$extension"
    }
}
