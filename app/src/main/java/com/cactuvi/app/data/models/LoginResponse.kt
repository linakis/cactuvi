package com.cactuvi.app.data.models

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("user_info")
    val userInfo: UserInfo,
    @SerializedName("server_info")
    val serverInfo: ServerInfo
)

data class UserInfo(
    val username: String,
    val password: String,
    val message: String?,
    val auth: Int,
    val status: String,
    @SerializedName("exp_date")
    val expDate: String,
    @SerializedName("is_trial")
    val isTrial: String,
    @SerializedName("active_cons")
    val activeCons: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("max_connections")
    val maxConnections: String,
    @SerializedName("allowed_output_formats")
    val allowedOutputFormats: List<String>
)

data class ServerInfo(
    val url: String,
    val port: String,
    @SerializedName("https_port")
    val httpsPort: String,
    @SerializedName("server_protocol")
    val serverProtocol: String,
    @SerializedName("rtmp_port")
    val rtmpPort: String,
    val timezone: String,
    @SerializedName("timestamp_now")
    val timestampNow: Long,
    @SerializedName("time_now")
    val timeNow: String,
    val process: Boolean? = null
)
