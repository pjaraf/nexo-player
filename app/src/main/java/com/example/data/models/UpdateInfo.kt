package com.example.data.models

import com.google.gson.annotations.SerializedName

data class UpdateInfo(
    @SerializedName(value = "versionCode", alternate = ["version_code"]) val versionCode: Int = 0,
    @SerializedName(value = "versionName", alternate = ["version_name"]) val versionName: String = "",
    @SerializedName(value = "apkUrl", alternate = ["apk_url"]) val apkUrl: String = "",
    @SerializedName(value = "changelog", alternate = ["changelog"]) val changelog: String = "",
    @SerializedName(value = "isMandatory", alternate = ["is_mandatory", "isMandatory"]) val isMandatory: Boolean = false,
    @SerializedName(value = "releaseDate", alternate = ["release_date"]) val releaseDate: String? = null
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFilePath: String) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

