package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
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

    private val _activeProfileFlow = MutableStateFlow<Profile?>(null)
    val activeProfileFlow: StateFlow<Profile?> = _activeProfileFlow.asStateFlow()

    init {
        _activeProfileFlow.value = getActiveProfile()
    }

    // --- Session ---
    fun saveSession(username: String, pass: String, user: UserInfo?, serverUrl: String = "") {
        prefs.edit()
            .putString("username", username)
            .putString("password", pass)
            .putString("server_url", serverUrl)
            .putString("user_json", user?.let { gson.toJson(it) } ?: "")
            .putBoolean("is_logged_in", true)
            .apply()
    }

    fun getUsername(): String = prefs.getString("username", "") ?: ""
    fun getPassword(): String = prefs.getString("password", "") ?: ""
    fun getServerUrl(): String = prefs.getString("server_url", "http://xtream-server:8080") ?: "http://xtream-server:8080"
    fun setServerUrl(url: String) {
        prefs.edit().putString("server_url", url).apply()
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
        prefs.edit()
            .remove("username")
            .remove("password")
            .remove("user_json")
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
