package com.example.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.NexusApp
import com.example.data.models.UpdateDownloadState
import com.example.data.models.UpdateInfo
import com.example.data.storage.AppStorage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    fun isVersionHigher(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val remoteClean = remoteVersion.trim().removePrefix("v").removePrefix("V")
            val currentClean = currentVersion.trim().removePrefix("v").removePrefix("V")
            if (remoteClean.equals(currentClean, ignoreCase = true)) {
                return false
            }
            val remoteParts = remoteClean.split(".").mapNotNull { it.trim().toIntOrNull() }
            val currentParts = currentClean.split(".").mapNotNull { it.trim().toIntOrNull() }
            if (remoteParts.isEmpty() || currentParts.isEmpty()) {
                return false
            }
            val maxLength = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: ${e.message}")
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
     * Checks the remote server/GitHub for a newer version.
     * Returns the UpdateInfo if a newer version is available, or null if up to date.
     */
    suspend fun checkForUpdates(customUrl: String? = null, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val rawUrl = customUrl?.ifBlank { null } ?: AppStorage.getUpdateCheckUrl()
        val normalized = normalizeUrl(rawUrl)
        val cacheBuster = if (normalized.contains("?")) "&_cb=${System.currentTimeMillis()}" else "?_cb=${System.currentTimeMillis()}"
        val targetUrl = "$normalized$cacheBuster"

        try {
            Log.d(TAG, "Checking update. Current version: $currentVersionName (code $currentVersionCode). URL: $targetUrl")

            // 1. Direct GitHub Releases API Check (Instant, 0s cache delay)
            if (targetUrl.contains("pjaraf/nexo-player") || targetUrl.contains("pjaraf/nexo-app") || targetUrl.contains("pjaraf/nexo-updates") || targetUrl.contains("github.com")) {
                try {
                    val githubApiUrl = if (targetUrl.contains("nexo-updates")) {
                        "https://api.github.com/repos/pjaraf/nexo-updates/releases/latest"
                    } else if (targetUrl.contains("nexo-app")) {
                        "https://api.github.com/repos/pjaraf/nexo-app/releases/latest"
                    } else {
                        "https://api.github.com/repos/pjaraf/nexo-player/releases/latest"
                    }
                    Log.d(TAG, "Querying GitHub Releases API: $githubApiUrl")
                    val ghRequest = Request.Builder()
                        .url(githubApiUrl)
                        .header("User-Agent", "Nexo-Updater/${currentVersionName}")
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("Cache-Control", "no-cache, no-store, must-revalidate")
                        .header("Pragma", "no-cache")
                        .build()

                    val ghResponse = httpClient.newCall(ghRequest).execute()
                    if (ghResponse.isSuccessful) {
                        val ghBody = ghResponse.body?.string()
                        if (!ghBody.isNullOrBlank()) {
                            val jsonObject = com.google.gson.JsonParser.parseString(ghBody).asJsonObject
                            val tagName = jsonObject.get("tag_name")?.asString?.replace("v", "")?.trim() ?: ""
                            val releaseBody = jsonObject.get("body")?.asString ?: ""
                            val assets = jsonObject.getAsJsonArray("assets")

                            var apkDownloadUrl: String? = null
                            if (assets != null) {
                                for (element in assets) {
                                    val assetObj = element.asJsonObject
                                    val name = assetObj.get("name")?.asString ?: ""
                                    if (name.endsWith(".apk", ignoreCase = true)) {
                                        apkDownloadUrl = assetObj.get("browser_download_url")?.asString
                                        break
                                    }
                                }
                            }

                            if (!apkDownloadUrl.isNullOrBlank()) {
                                val cleanTag = tagName.trim().removePrefix("v").removePrefix("V")
                                val isNewer = isVersionHigher(cleanTag, currentVersionName)

                                if (isNewer) {
                                    val dismissed = AppStorage.getDismissedUpdateVersion()
                                    if (!force && dismissed == cleanTag) {
                                        Log.d(TAG, "Update v$cleanTag was previously dismissed")
                                        _latestUpdateInfo.value = null
                                        return@withContext null
                                    }
                                    val updateFromGh = UpdateInfo(
                                        versionCode = currentVersionCode + 1,
                                        versionName = if (cleanTag.isNotBlank()) cleanTag else "Nueva Versión",
                                        apkUrl = apkDownloadUrl,
                                        changelog = if (releaseBody.isNotBlank()) releaseBody else "Nueva versión disponible en GitHub",
                                        isMandatory = false
                                    )
                                    Log.i(TAG, "New update found via GitHub API! v${updateFromGh.versionName}")
                                    AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
                                    _latestUpdateInfo.value = updateFromGh
                                    return@withContext updateFromGh
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "GitHub Releases API check error: ${e.message}")
                }
            }

            // 2. Check version.json as primary/fallback source
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Nexo-Updater/${currentVersionName}")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                Log.d(TAG, "Update response from version.json: $body")
                if (!body.isNullOrBlank()) {
                    val update = try {
                        gson.fromJson(body, UpdateInfo::class.java)
                    } catch (e: Exception) {
                        null
                    }

                    if (update != null) {
                        AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
                        val remoteVer = update.versionName.trim()
                        val isNewer = isVersionHigher(remoteVer, currentVersionName) || (update.versionCode > currentVersionCode)

                        if (isNewer) {
                            val dismissed = AppStorage.getDismissedUpdateVersion()
                            if (!force && dismissed == remoteVer) {
                                Log.d(TAG, "Update v$remoteVer was previously dismissed")
                                _latestUpdateInfo.value = null
                                return@withContext null
                            }
                            val resolvedApkUrl = if (update.apkUrl.isNotBlank()) {
                                normalizeUrl(update.apkUrl)
                            } else {
                                "https://github.com/pjaraf/nexo-player/releases/download/v$remoteVer/app-debug.apk"
                            }
                            val normalizedUpdate = update.copy(apkUrl = resolvedApkUrl)
                            Log.i(TAG, "New update found via version.json! v${normalizedUpdate.versionName} (code=${normalizedUpdate.versionCode})")
                            _latestUpdateInfo.value = normalizedUpdate
                            return@withContext normalizedUpdate
                        }
                    }
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

    /**
     * Downloads the APK from the given UpdateInfo and reports progress via downloadState.
     */
    suspend fun downloadUpdate(
        context: Context,
        update: UpdateInfo,
        autoInstall: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = UpdateDownloadState.Downloading(0, 0L, 0L)

            val downloadUrl = normalizeUrl(update.apkUrl)
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Nexo-Downloader")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                _downloadState.value = UpdateDownloadState.Error("Error al descargar archivo (HTTP ${response.code})")
                return@withContext false
            }

            val responseBody = response.body!!
            val totalLength = responseBody.contentLength()

            val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            // Clean up any old downloaded APK files
            downloadsDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("nexo_update_") && file.name.endsWith(".apk")) {
                    file.delete()
                }
            }

            val apkFile = File(downloadsDir, "nexo_update_${update.versionCode}.apk")
            if (apkFile.exists()) apkFile.delete()

            val inputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(16 * 1024)
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
