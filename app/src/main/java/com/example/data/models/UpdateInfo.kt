package com.example.data.models

import com.google.gson.annotations.SerializedName

data class UpdateInfo(
    @SerializedName("version_code") val versionCode: Int = 0,
    @SerializedName("version_name") val versionName: String = "",
    @SerializedName("apk_url") val apkUrl: String = "",
    @SerializedName("changelog") val changelog: String = "",
    @SerializedName("is_mandatory") val isMandatory: Boolean = false,
    @SerializedName("release_date") val releaseDate: String? = null
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFilePath: String) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}
