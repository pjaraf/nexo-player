package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.NexusApp
import com.example.data.models.FavItem
import com.example.data.models.Profile
import com.example.data.models.ProgressItem
import com.example.data.models.UserInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

object AppStorage {

    private val prefs: SharedPreferences by lazy {
        NexusApp.instance.getSharedPreferences("nexo_prefs", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    val AVATAR_COLORS = listOf(
        "#E50914", "#00E5FF", "#F59E0B", "#22C55E",
        "#A855F7", "#EC4899", "#3B82F6", "#F97316"
    )

    const val SERVER_NEXO_FUSION = "https://nexo.fusionx.cl"
    const val SERVER_ELITE_PLUS = "http://eliteplusec.com:8080"

    val AUTO_DETECT_SERVERS = listOf(
        "https://nexo.fusionx.cl",
        "http://nexo.fusionx.cl",
        "http://nexo.fusionx.cl:8080",
        "https://nexo.fusionx.cl:8080",
        "https://nexo.fusionx.cl:8443",
        "http://eliteplusec.com:8080",
        "https://eliteplusec.com:8080",
        "http://eliteplusec.com"
    )

    val SERVER_PRESETS = listOf(
        "Nexo Fusion" to SERVER_NEXO_FUSION,
        "Elite Plus" to SERVER_ELITE_PLUS
    )

    private val _activeProfileFlow = MutableStateFlow<Profile?>(null)
    val activeProfileFlow: StateFlow<Profile?> = _activeProfileFlow.asStateFlow()

    init {
        _activeProfileFlow.value = getActiveProfile()
    }

    // --- Session ---
    fun saveSession(username: String, pass: String, user: UserInfo?, serverUrl: String = "") {
        val targetServer = if (serverUrl.isNotBlank()) serverUrl else getServerUrl()
        prefs.edit()
            .putString("username", username)
            .putString("password", pass)
            .putString("server_url", targetServer)
            .putString("user_json", user?.let { gson.toJson(it) } ?: "")
            .putBoolean("is_logged_in", true)
            .putBoolean("is_m3u_mode", false)
            .remove("m3u_url")
            .apply()
    }

    fun saveM3uSession(playlistUrl: String, playlistName: String = "Lista M3U") {
        val cleanUrl = playlistUrl.trim()
        val name = playlistName.trim().ifBlank { "Lista M3U" }
        val dummyUser = UserInfo(
            username = name,
            status = "Active",
            expDate = "Ilimitado",
            auth = 1
        )
        prefs.edit()
            .putString("username", name)
            .putString("password", "m3u_direct")
            .putString("server_url", cleanUrl)
            .putString("m3u_url", cleanUrl)
            .putBoolean("m3u_is_local_file", false)
            .remove("m3u_file_name")
            .putString("user_json", gson.toJson(dummyUser))
            .putBoolean("is_logged_in", true)
            .putBoolean("is_m3u_mode", true)
            .apply()
    }

    fun saveM3uLocalFileSession(fileName: String, content: String): Boolean {
        return try {
            val file = File(NexusApp.instance.filesDir, "custom_playlist.m3u")
            file.writeText(content)
            val name = fileName.trim().ifBlank { "Archivo M3U" }
            val dummyUser = UserInfo(
                username = name,
                status = "Active",
                expDate = "Ilimitado",
                auth = 1
            )
            prefs.edit()
                .putString("username", name)
                .putString("password", "m3u_local_file")
                .putString("server_url", "local://custom_playlist.m3u")
                .putString("m3u_url", "local://custom_playlist.m3u")
                .putString("m3u_file_name", name)
                .putBoolean("m3u_is_local_file", true)
                .putString("user_json", gson.toJson(dummyUser))
                .putBoolean("is_logged_in", true)
                .putBoolean("is_m3u_mode", true)
                .apply()
            true
        } catch (e: Exception) {
            Log.e("AppStorage", "Error saving local M3U file", e)
            false
        }
    }

    fun getLocalM3uFileContent(): String? {
        return try {
            val file = File(NexusApp.instance.filesDir, "custom_playlist.m3u")
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun isM3uMode(): Boolean = prefs.getBoolean("is_m3u_mode", false)
    fun isLocalM3uFile(): Boolean = prefs.getBoolean("m3u_is_local_file", false)
    fun getM3uUrl(): String = prefs.getString("m3u_url", "") ?: ""

    fun getUsername(): String = prefs.getString("username", "") ?: ""
    fun getPassword(): String = prefs.getString("password", "") ?: ""
    fun getServerUrl(): String = prefs.getString("server_url", SERVER_NEXO_FUSION)?.ifBlank { SERVER_NEXO_FUSION } ?: SERVER_NEXO_FUSION
    fun setServerUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        prefs.edit().putString("server_url", clean).apply()
    }

    fun getUserInfo(): UserInfo? {
        val json = prefs.getString("user_json", null) ?: return null
        return try {
            gson.fromJson(json, UserInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false) && getUsername().isNotBlank()

    fun clearSession() {
        try {
            val file = File(NexusApp.instance.filesDir, "custom_playlist.m3u")
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
        prefs.edit()
            .remove("username")
            .remove("password")
            .remove("user_json")
            .remove("is_m3u_mode")
            .remove("m3u_url")
            .remove("m3u_file_name")
            .remove("m3u_is_local_file")
            .putBoolean("is_logged_in", false)
            .remove("active_profile_id")
            .apply()
        _activeProfileFlow.value = null
    }

    // --- Profiles ---
    fun getProfiles(): List<Profile> {
        val json = prefs.getString("profiles_list", null)
        if (json.isNullOrBlank()) {
            val defaultProfile = Profile(
                id = UUID.randomUUID().toString(),
                name = getUsername().ifBlank { "Principal" },
                color = AVATAR_COLORS[0],
                isKids = false
            )
            val list = listOf(defaultProfile)
            saveProfiles(list)
            return list
        }
        return try {
            val type = object : TypeToken<List<Profile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProfiles(list: List<Profile>) {
        prefs.edit().putString("profiles_list", gson.toJson(list)).apply()
        val currentActiveId = getActiveProfileId()
        if (list.none { it.id == currentActiveId }) {
            setActiveProfileId(list.firstOrNull()?.id ?: "")
        } else {
            _activeProfileFlow.value = list.find { it.id == currentActiveId }
        }
    }

    fun addProfile(name: String, color: String, isKids: Boolean): Profile {
        val list = getProfiles().toMutableList()
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name,
            color = color,
            isKids = isKids
        )
        list.add(profile)
        saveProfiles(list)
        return profile
    }

    fun updateProfile(id: String, name: String, color: String, isKids: Boolean) {
        val list = getProfiles().map {
            if (it.id == id) it.copy(name = name, color = color, isKids = isKids) else it
        }
        saveProfiles(list)
    }

    fun deleteProfile(id: String) {
        val list = getProfiles().filter { it.id != id }
        if (list.isNotEmpty()) {
            saveProfiles(list)
        }
    }

    fun getActiveProfileId(): String = prefs.getString("active_profile_id", "") ?: ""

    fun setActiveProfileId(id: String) {
        prefs.edit().putString("active_profile_id", id).apply()
        _activeProfileFlow.value = getProfiles().find { it.id == id }
    }

    fun getActiveProfile(): Profile? {
        val id = getActiveProfileId()
        val profiles = getProfiles()
        return profiles.find { it.id == id } ?: profiles.firstOrNull()
    }

    // --- PIN ---
    fun getPin(): String = prefs.getString("adult_pin", "") ?: ""

    fun setPin(pin: String) {
        prefs.edit().putString("adult_pin", pin).apply()
    }

    fun clearPin() {
        prefs.edit().remove("adult_pin").apply()
    }

    fun hasPin(): Boolean = getPin().isNotBlank()

    // --- Kids Filtering ---
    private val KIDS_KEYWORDS = listOf(
        "infantil", "niños", "ninos", "kids", "dibujo", "cartoon", "disney jr",
        "disney junior", "nick jr", "nickelodeon", "baby", "senpai", "anim"
    )

    fun isKidsCategory(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return KIDS_KEYWORDS.any { lower.contains(it) }
    }

    // --- Favorites ---
    fun getFavorites(profileId: String, kind: String): List<FavItem> {
        val key = "favs_${kind}_$profileId"
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FavItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAllFavorites(profileId: String): List<FavItem> {
        val live = getFavorites(profileId, "live")
        val movies = getFavorites(profileId, "movies")
        val series = getFavorites(profileId, "series")
        return (live + movies + series).sortedByDescending { it.addedAt }
    }

    fun isFavorite(profileId: String, kind: String, id: String): Boolean {
        return getFavorites(profileId, kind).any { it.id == id }
    }

    fun toggleFavorite(profileId: String, kind: String, id: String, title: String, image: String?): Boolean {
        val list = getFavorites(profileId, kind).toMutableList()
        val existing = list.find { it.id == id }
        val isNowFav = if (existing != null) {
            list.remove(existing)
            false
        } else {
            list.add(0, FavItem(id = id, title = title, image = image, kind = kind))
            true
        }
        val key = "favs_${kind}_$profileId"
        prefs.edit().putString(key, gson.toJson(list)).apply()
        return isNowFav
    }

    // --- Progress (Continue Watching) ---
    fun getProgressList(profileId: String): List<ProgressItem> {
        val key = "progress_$profileId"
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ProgressItem>>() {}.type
            val list: List<ProgressItem> = gson.fromJson(json, type) ?: emptyList()
            list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun upsertProgress(profileId: String, item: ProgressItem) {
        val list = getProgressList(profileId).toMutableList()
        list.removeAll { it.key == item.key }
        list.add(0, item)
        val trimmed = list.take(30)
        val key = "progress_$profileId"
        prefs.edit().putString(key, gson.toJson(trimmed)).apply()
    }

    fun removeProgress(profileId: String, progressKey: String) {
        val list = getProgressList(profileId).toMutableList()
        list.removeAll { it.key == progressKey }
        val key = "progress_$profileId"
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }

    // --- In-App Updates ---
    const val DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/pjaraf/nexo-player/main/version.json"
    const val GITHUB_REPO_API_URL = "https://api.github.com/repos/pjaraf/nexo-player/releases/latest"

    fun getUpdateCheckUrl(): String {
        return prefs.getString("custom_update_url", null)?.ifBlank { null } ?: DEFAULT_UPDATE_URL
    }

    fun setUpdateCheckUrl(url: String) {
        prefs.edit().putString("custom_update_url", url.trim()).apply()
    }

    fun isAutoCheckUpdatesEnabled(): Boolean {
        return prefs.getBoolean("auto_check_updates", true)
    }

    fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_check_updates", enabled).apply()
    }

    fun isAutoDownloadUpdatesEnabled(): Boolean {
        return prefs.getBoolean("auto_download_updates", true)
    }

    fun setAutoDownloadUpdatesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_download_updates", enabled).apply()
    }

    fun getLastUpdateCheckTime(): Long {
        return prefs.getLong("last_update_check_time", 0L)
    }

    fun setLastUpdateCheckTime(timeMs: Long) {
        prefs.edit().putLong("last_update_check_time", timeMs).apply()
    }

    fun getDismissedUpdateVersion(): String? {
        return prefs.getString("dismissed_update_version", null)
    }

    fun setDismissedUpdateVersion(version: String?) {
        if (version == null) {
            prefs.edit().remove("dismissed_update_version").apply()
        } else {
            prefs.edit().putString("dismissed_update_version", version).apply()
        }
    }
}
