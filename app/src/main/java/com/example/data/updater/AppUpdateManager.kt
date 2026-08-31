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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = try {
            SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }
        } catch (_: Exception) {
            SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, SecureRandom())
            }
        }

        val modernTlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .build()

        val compatibleTlsSpec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .build()

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectionSpecs(listOf(modernTlsSpec, compatibleTlsSpec, ConnectionSpec.CLEARTEXT))
            .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val _latestUpdateInfo = MutableStateFlow<UpdateInfo?>(null)
    val latestUpdateInfo: StateFlow<UpdateInfo?> = _latestUpdateInfo.asStateFlow()

    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME

    /**
     * Compares two semantic version strings (e.g., "1.1.16" vs "1.1.14").
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, and 0 if v1 == v2.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.trim().removePrefix("v").removePrefix("V")
        val clean2 = v2.trim().removePrefix("v").removePrefix("V")
        if (clean1.equals(clean2, ignoreCase = true)) return 0

        val parts1 = clean1.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.trim().toIntOrNull() ?: 0 }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    /**
     * Returns true if the remote version is newer by build code or semantic version name.
     */
    fun isUpdateNewer(
        remoteVersionName: String,
        remoteVersionCode: Int,
        currentVersionName: String,
        currentVersionCode: Int
    ): Boolean {
        if (remoteVersionName.isBlank() && remoteVersionCode <= 0) return false
        // If remote versionCode is strictly higher, it's definitely a newer build
        if (remoteVersionCode > 0 && currentVersionCode > 0 && remoteVersionCode > currentVersionCode) {
            return true
        }
        val comparison = compareVersions(remoteVersionName, currentVersionName)
        if (comparison > 0) {
            return true
        }
        if (comparison == 0) {
            return remoteVersionCode > currentVersionCode
        }
        return false
    }

    fun isVersionHigher(remoteVersion: String, currentVersion: String): Boolean {
        return compareVersions(remoteVersion, currentVersion) > 0
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
     * Checks both version.json (direct GitHub API + raw endpoints) and GitHub Releases API in parallel
     * for instantaneous detection the moment an update is uploaded to GitHub.
     */
    suspend fun checkForUpdates(customUrl: String? = null, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking update. Current version: $currentVersionName (code $currentVersionCode)")

            // Parallel lookup: Query both GitHub direct API and version.json endpoints simultaneously
            val candidate: UpdateInfo? = coroutineScope {
                val deferredVersion = async(Dispatchers.IO) { fetchFromVersionJson(customUrl) }
                val deferredReleases = async(Dispatchers.IO) { fetchFromGitHubReleases() }

                val versionInfo = deferredVersion.await()
                val releaseInfo = deferredReleases.await()

                // Pick candidate with higher version
                val validList: List<UpdateInfo> = listOfNotNull(versionInfo, releaseInfo)
                validList.maxWithOrNull { a, b ->
                    val cmp = compareVersions(a.versionName, b.versionName)
                    if (cmp != 0) cmp else a.versionCode.compareTo(b.versionCode)
                } ?: versionInfo ?: releaseInfo
            }

            if (candidate != null) {
                val isNewer = isUpdateNewer(
                    remoteVersionName = candidate.versionName,
                    remoteVersionCode = candidate.versionCode,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )

                if (isNewer) {
                    Log.i(TAG, "New update found instantly for all devices: v${candidate.versionName} (code=${candidate.versionCode})")
                    _latestUpdateInfo.value = candidate
                    AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
                    return@withContext candidate
                }
            }

            AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
            if (force) {
                _latestUpdateInfo.value = null
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error during update check: ${e.message}")
            return@withContext null
        }
    }

    private suspend fun fetchFromVersionJson(customUrl: String?): UpdateInfo? = coroutineScope {
        val configuredUrl = customUrl?.ifBlank { null } ?: AppStorage.getUpdateCheckUrl()
        val candidateUrls = mutableListOf<String>()
        val ts = System.currentTimeMillis()
        val nonce = (1000..9999).random()

        // 1. Direct raw github user content with unique timestamp + nonce cache-buster
        candidateUrls.add("https://raw.githubusercontent.com/pjaraf/nexo-player/main/version.json")
        candidateUrls.add("https://raw.githubusercontent.com/pjaraf/nexo-player/master/version.json")
        // 2. High-speed jsDelivr CDN mirrors
        candidateUrls.add("https://cdn.jsdelivr.net/gh/pjaraf/nexo-player@main/version.json")
        candidateUrls.add("https://cdn.jsdelivr.net/gh/pjaraf/nexo-player@master/version.json")
        candidateUrls.add("https://fastly.jsdelivr.net/gh/pjaraf/nexo-player@main/version.json")
        // 3. User configured / Custom URL
        if (!configuredUrl.isNullOrBlank()) {
            candidateUrls.add(configuredUrl)
        }
        // 4. GitHub API Contents endpoint
        candidateUrls.add("https://api.github.com/repos/pjaraf/nexo-player/contents/version.json")

        val distinctUrls = candidateUrls.distinct()
        val deferredList = distinctUrls.map { url ->
            async(Dispatchers.IO) {
                try {
                    val isGitHubApi = url.contains("api.github.com")
                    val targetUrl = if (isGitHubApi) {
                        if (url.contains("?")) "$url&_t=${ts}_$nonce" else "$url?_t=${ts}_$nonce"
                    } else {
                        val normalized = normalizeUrl(url)
                        if (normalized.contains("?")) "$normalized&_t=${ts}_$nonce" else "$normalized?_t=${ts}_$nonce"
                    }

                    val reqBuilder = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 Nexo-Updater/${currentVersionName}")
                        .header("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
                        .header("Pragma", "no-cache")
                        .header("Expires", "0")

                    if (isGitHubApi) {
                        reqBuilder.header("Accept", "application/vnd.github.v3.raw")
                    }

                    val request = reqBuilder.build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            var jsonToParse = body
                            // Handle GitHub API base64 fallback wrapper
                            if (body.contains("\"content\":") && body.contains("\"encoding\":")) {
                                try {
                                    val gitHubObj = gson.fromJson(body, JsonObject::class.java)
                                    val contentBase64 = gitHubObj.get("content")?.asString?.replace("\n", "")?.replace("\r", "")
                                    if (!contentBase64.isNullOrBlank()) {
                                        jsonToParse = String(android.util.Base64.decode(contentBase64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                                    }
                                } catch (_: Throwable) {}
                            }

                            val update = gson.fromJson(jsonToParse, UpdateInfo::class.java)
                            if (update != null && update.versionName.isNotBlank()) {
                                val resolvedApkUrl = if (update.apkUrl.isNotBlank()) {
                                    normalizeUrl(update.apkUrl)
                                } else {
                                    "https://github.com/pjaraf/nexo-player/releases/download/v${update.versionName}/app-debug.apk"
                                }
                                return@async update.copy(apkUrl = resolvedApkUrl, isMandatory = true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch from version.json ($url): ${e.message}")
                }
                null
            }
        }

        // Collect all results and pick the newest
        val results = deferredList.mapNotNull { it.await() }
        results.maxWithOrNull { a, b ->
            val cmp = compareVersions(a.versionName, b.versionName)
            if (cmp != 0) cmp else a.versionCode.compareTo(b.versionCode)
        }
    }

    private fun fetchFromGitHubReleases(): UpdateInfo? {
        return try {
            val ts = System.currentTimeMillis()
            val apiUrl = "https://api.github.com/repos/pjaraf/nexo-player/releases/latest?_t=$ts"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) Nexo-Updater/${currentVersionName}")
                .header("Accept", "application/vnd.github.v3+json")
                .header("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
                .header("Pragma", "no-cache")
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
                            versionCode = 0,
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

    fun setUpdateInfo(info: UpdateInfo?) {
        _latestUpdateInfo.value = info
    }
}
