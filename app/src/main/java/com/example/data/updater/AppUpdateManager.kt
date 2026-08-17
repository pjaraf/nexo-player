package com.example.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.models.UpdateDownloadState
import com.example.data.models.UpdateInfo
import com.example.data.storage.AppStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val _latestUpdateInfo = MutableStateFlow<UpdateInfo?>(null)
    val latestUpdateInfo: StateFlow<UpdateInfo?> = _latestUpdateInfo.asStateFlow()

    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME

    /**
     * Accurately compares two semantic versions (e.g. "1.1.16" > "1.1.15")
     */
    fun isVersionHigher(remoteVersion: String, currentVersion: String): Boolean {
        val remoteClean = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val currentClean = currentVersion.trim().removePrefix("v").removePrefix("V")
        if (remoteClean.isBlank() || currentClean.isBlank()) return false
        if (remoteClean.equals(currentClean, ignoreCase = true)) return false

        val remoteParts = remoteClean.split(".").mapNotNull { it.trim().toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.trim().toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Normalizes URLs for popular hosting providers (GitHub, Dropbox)
     */
    fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.isBlank()) return clean

        // Convert Dropbox preview link to direct raw download
        if (clean.contains("dropbox.com")) {
            clean = clean.replace("www.dropbox.com", "dl.dropboxusercontent.com")
            clean = clean.replace("?dl=0", "?dl=1")
            if (!clean.contains("?dl=1") && !clean.contains("&dl=1")) {
                clean = if (clean.contains("?")) "$clean&dl=1" else "$clean?dl=1"
            }
        }

        // Convert GitHub blob preview link to raw content
        if (clean.contains("github.com") && clean.contains("/blob/")) {
            clean = clean.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/")
        }

        return clean
    }

    /**
     * Checks both version.json and GitHub Releases API for a newer version.
     */
    suspend fun checkForUpdates(customUrl: String? = null, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking update. Current version: $currentVersionName (code $currentVersionCode)")

            // 1. First attempt: Check version.json
            val versionInfo = fetchFromVersionJson(customUrl)
            if (versionInfo != null) {
                val isNewer = (versionInfo.versionCode > currentVersionCode) ||
                        isVersionHigher(versionInfo.versionName, currentVersionName)

                if (isNewer) {
                    val dismissed = AppStorage.getDismissedUpdateVersion()
                    if (!force && dismissed == versionInfo.versionName) {
                        Log.d(TAG, "Update v${versionInfo.versionName} was previously dismissed")
                        _latestUpdateInfo.value = null
                        return@withContext null
                    }
                    Log.i(TAG, "New update found via version.json: v${versionInfo.versionName} (code=${versionInfo.versionCode})")
                    _latestUpdateInfo.value = versionInfo
                    AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
                    return@withContext versionInfo
                }
            }

            // 2. Second attempt: Check GitHub Releases API directly if version.json didn't yield an update
            val releaseInfo = fetchFromGitHubReleases()
            if (releaseInfo != null) {
                val isNewer = (releaseInfo.versionCode > currentVersionCode) ||
                        isVersionHigher(releaseInfo.versionName, currentVersionName)

                if (isNewer) {
                    val dismissed = AppStorage.getDismissedUpdateVersion()
                    if (!force && dismissed == releaseInfo.versionName) {
                        _latestUpdateInfo.value = null
                        return@withContext null
                    }
                    Log.i(TAG, "New update found via GitHub Releases API: v${releaseInfo.versionName}")
                    _latestUpdateInfo.value = releaseInfo
                    AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
                    return@withContext releaseInfo
                }
            }

            AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
            _latestUpdateInfo.value = null
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error during update check: ${e.message}")
            return@withContext null
        }
    }

    private fun fetchFromVersionJson(customUrl: String?): UpdateInfo? {
        return try {
            val rawUrl = customUrl?.ifBlank { null } ?: AppStorage.getUpdateCheckUrl()
            val normalized = normalizeUrl(rawUrl)
            val cacheBuster = if (normalized.contains("?")) "&_cb=${System.currentTimeMillis()}" else "?_cb=${System.currentTimeMillis()}"
            val targetUrl = "$normalized$cacheBuster"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Nexo-Updater/${currentVersionName}")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val update = gson.fromJson(body, UpdateInfo::class.java)
                    if (update != null && update.versionName.isNotBlank()) {
                        val resolvedApkUrl = if (update.apkUrl.isNotBlank()) {
                            normalizeUrl(update.apkUrl)
                        } else {
                            "https://github.com/pjaraf/nexo-player/releases/download/v${update.versionName}/app-debug.apk"
                        }
                        return update.copy(apkUrl = resolvedApkUrl)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch from version.json: ${e.message}")
            null
        }
    }

    private fun fetchFromGitHubReleases(): UpdateInfo? {
        return try {
            val apiUrl = "https://api.github.com/repos/pjaraf/nexo-player/releases/latest"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Nexo-Updater/${currentVersionName}")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val tagName = json.get("tag_name")?.asString?.removePrefix("v")?.removePrefix("V") ?: ""
                    val releaseNotes = json.get("body")?.asString ?: "Nueva versión con mejoras y correcciones."

                    var downloadUrl = ""
                    val assets = json.getAsJsonArray("assets")
                    if (assets != null && assets.size() > 0) {
                        for (assetElement in assets) {
                            val asset = assetElement.asJsonObject
                            val name = asset.get("name")?.asString ?: ""
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.get("browser_download_url")?.asString ?: ""
                                break
                            }
                        }
                    }

                    if (downloadUrl.isBlank() && tagName.isNotBlank()) {
                        downloadUrl = "https://github.com/pjaraf/nexo-player/releases/download/v$tagName/app-debug.apk"
                    }

                    if (tagName.isNotBlank()) {
                        return UpdateInfo(
                            versionCode = currentVersionCode + 1,
                            versionName = tagName,
                            apkUrl = downloadUrl,
                            changelog = releaseNotes,
                            isMandatory = true
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch from GitHub Releases API: ${e.message}")
            null
        }
    }

    /**
     * Downloads the APK with multi-URL fallback to guarantee reliable downloads.
     */
    suspend fun downloadUpdate(
        context: Context,
        update: UpdateInfo,
        autoInstall: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = UpdateDownloadState.Downloading(0, 0L, 0L)

            // Candidate URLs in priority order
            val cleanVersion = update.versionName.removePrefix("v").removePrefix("V")
            val candidateUrls = mutableListOf<String>()

            if (update.apkUrl.isNotBlank()) {
                candidateUrls.add(normalizeUrl(update.apkUrl))
            }
            candidateUrls.add("https://github.com/pjaraf/nexo-player/releases/download/v$cleanVersion/app-debug.apk")
            candidateUrls.add("https://github.com/pjaraf/nexo-player/releases/download/$cleanVersion/app-debug.apk")
            candidateUrls.add("https://github.com/pjaraf/nexo-player/releases/latest/download/app-debug.apk")

            var successfulResponse: Response? = null
            var lastErrorMessage = ""

            for (url in candidateUrls.distinct()) {
                try {
                    Log.d(TAG, "Attempting APK download from: $url")
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Nexo-Downloader")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        successfulResponse = response
                        Log.i(TAG, "Successfully connected to download source: $url")
                        break
                    } else {
                        lastErrorMessage = "HTTP ${response.code} en $url"
                        response.close()
                    }
                } catch (e: Exception) {
                    lastErrorMessage = e.message ?: "Error de red"
                }
            }

            if (successfulResponse == null || successfulResponse.body == null) {
                _downloadState.value = UpdateDownloadState.Error("No se pudo descargar el archivo APK ($lastErrorMessage)")
                return@withContext false
            }

            val responseBody = successfulResponse.body!!
            val totalLength = responseBody.contentLength()

            val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            // Clean up any old downloaded APK files
            downloadsDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("nexo_update_") && file.name.endsWith(".apk")) {
                    file.delete()
                }
            }

            val apkFile = File(downloadsDir, "nexo_update_${cleanVersion}.apk")
            if (apkFile.exists()) apkFile.delete()

            val inputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            var totalDownloaded = 0L
            var lastPercent = 0

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        if (totalLength > 0) {
                            val percent = ((totalDownloaded * 100) / totalLength).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _downloadState.value = UpdateDownloadState.Downloading(
                                    progressPercent = percent,
                                    bytesDownloaded = totalDownloaded,
                                    totalBytes = totalLength
                                )
                            }
                        }
                    }
                    output.flush()
                }
            }

            Log.i(TAG, "APK successfully downloaded to: ${apkFile.absolutePath}")
            _downloadState.value = UpdateDownloadState.ReadyToInstall(apkFile.absolutePath)

            if (autoInstall) {
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile.absolutePath)
                }
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Error(e.message ?: "Error desconocido en la descarga")
            return@withContext false
        }
    }

    /**
     * Launches the system Package Installer for the downloaded APK.
     */
    fun installApk(context: Context, apkFilePath: String) {
        try {
            val apkFile = File(apkFilePath)
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found at: $apkFilePath")
                return
            }

            // Check if unknown sources install permission is granted on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            _downloadState.value = UpdateDownloadState.Error("No se pudo abrir el instalador: ${e.message}")
        }
    }

    fun resetDownloadState() {
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun dismissUpdate() {
        val current = _latestUpdateInfo.value
        if (current != null) {
            AppStorage.setDismissedUpdateVersion(current.versionName)
        }
        _latestUpdateInfo.value = null
        _downloadState.value = UpdateDownloadState.Idle
    }
}
