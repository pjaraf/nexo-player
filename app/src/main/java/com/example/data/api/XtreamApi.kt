package com.example.data.api

import android.util.Log
import com.example.data.models.*
import com.example.data.storage.AppStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.TlsVersion
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object XtreamApi {
    private const val TAG = "XtreamApi"
    
    var SERVER_HOST: String
        get() = AppStorage.getServerUrl()
        set(value) { AppStorage.setServerUrl(value) }

    val baseUrl: String
        get() {
            val stored = AppStorage.getServerUrl()
            if (stored.isNotBlank()) {
                return stored.trimEnd('/')
            }
            return AppStorage.SERVER_NEXO_FUSION
        }

    private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 VLC/3.0.18 LibVLC/3.5.4"

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
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // In-memory caching for faster screen transitions
    private val memoryCache = mutableMapOf<String, Any>()

    // Dedicated M3U Playlist for Legacy Live TV Server only
    const val LIVE_PLAYLIST_URL = "https://www.dropbox.com/scl/fi/c13v3nfgw2x81qfy0v1i6/CLIENTES.txt?rlkey=ge7xlg9kew1iaslgj68cgn59h&st=18m5wcdh&dl=1"
    private var parsedLiveChannels: List<LiveChannel>? = null
    private var parsedLiveCategories: List<LiveCategory>? = null

    fun clearCache() {
        memoryCache.clear()
        parsedLiveChannels = null
        parsedLiveCategories = null
    }

    private suspend fun fetch(
        action: String? = null,
        extraParams: Map<String, String> = emptyMap(),
        customUser: String? = null,
        customPass: String? = null,
        customBaseUrl: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val username = customUser ?: AppStorage.getUsername()
        val password = customPass ?: AppStorage.getPassword()

        if (username.isBlank() || password.isBlank()) {
            return@withContext null
        }

        val effectiveBase = (customBaseUrl ?: baseUrl).trim().trimEnd('/')
        val urlBuilder = "$effectiveBase/player_api.php".toHttpUrlOrNull()?.newBuilder()
            ?: return@withContext null

        urlBuilder.addQueryParameter("username", username)
        urlBuilder.addQueryParameter("password", password)

        action?.let { urlBuilder.addQueryParameter("action", it) }
        extraParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", DEFAULT_UA)
            .header("Accept", "*/*")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                Log.d(TAG, "Fetch success ($effectiveBase, action=$action): length=${bodyStr?.length}")
                bodyStr
            } else {
                Log.w(TAG, "Fetch failed for action=$action code=${response.code} from $effectiveBase")
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Network error for action=$action on $effectiveBase: ${e.message}")
            null
        }
    }

    suspend fun login(username: String, pass: String, customBaseUrl: String? = null): LoginResponse? {
        val json = fetch(customUser = username, customPass = pass, customBaseUrl = customBaseUrl) ?: return null
        return try {
            val res = gson.fromJson(json, LoginResponse::class.java)
            val userInfo = res.userInfo
            if (userInfo == null) return null

            val authRaw = userInfo.auth?.toString()?.trim()
            val status = userInfo.status?.trim()
            val user = userInfo.username?.trim()

            // Verify authentication explicitly
            val isAuthNegative = authRaw == "0" || authRaw == "0.0" || authRaw.equals("false", ignoreCase = true)
            val isStatusDisabled = status.equals("Disabled", ignoreCase = true) ||
                    status.equals("Banned", ignoreCase = true) ||
                    status.equals("Expired", ignoreCase = true)

            if (!isAuthNegative && !isStatusDisabled) {
                res
            } else {
                Log.w(TAG, "Login rejected by server validation. auth=$authRaw, status=$status, user=$user")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Login parse error", e)
            null
        }
    }

    // --- Live TV from Custom Playlist / M3U / Xtream Server ---
    suspend fun loadM3uContent(customUrl: String? = null): String? = withContext(Dispatchers.IO) {
        val targetUrl = (customUrl ?: AppStorage.getM3uUrl()).ifBlank { LIVE_PLAYLIST_URL }
        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_UA)
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.w(TAG, "Failed to download M3U live list code=${response.code} from $targetUrl")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error downloading M3U live list from $targetUrl", e)
            null
        }
    }

    private suspend fun fetchLiveM3uContent(): String? = loadM3uContent(LIVE_PLAYLIST_URL)

    fun parseM3uText(content: String): Pair<List<LiveChannel>, List<LiveCategory>> {
        val entries = content.split("#EXTINF:")
        val channels = mutableListOf<LiveChannel>()
        val groupCategoriesMap = linkedMapOf<String, String>()

        val logoRegex = Regex("""tvg-logo=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val groupRegex = Regex("""group-title=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val urlRegex = Regex("""(https?://[^\s\r\n"'<>]+)""", RegexOption.IGNORE_CASE)

        for ((idx, entry) in entries.drop(1).withIndex()) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue

            val logoMatch = logoRegex.find(trimmed)
            val logo = logoMatch?.groupValues?.get(1)?.trim().orEmpty()

            val groupMatch = groupRegex.find(trimmed)
            val rawGroup = groupMatch?.groupValues?.get(1)?.trim()
            val group = if (rawGroup.isNullOrBlank()) "GENERAL" else rawGroup

            val commaIdx = trimmed.indexOf(',')
            var name = "Canal ${idx + 1}"
            var streamUrl = ""

            if (commaIdx != -1) {
                val afterComma = trimmed.substring(commaIdx + 1).trim()
                val urlMatch = urlRegex.find(afterComma)
                if (urlMatch != null) {
                    streamUrl = urlMatch.groupValues[1].trim()
                    name = afterComma.substring(0, urlMatch.range.first).trim()
                } else {
                    name = afterComma.lines().firstOrNull()?.trim() ?: name
                }
            }

            name = name.replace(Regex("""https?://.*"""), "").trim()
            if (name.isBlank()) name = "Canal ${idx + 1}"

            val categorySlug = group.lowercase()
                .replace(" ", "_")
                .replace("/", "_")
                .replace("&", "_")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")

            groupCategoriesMap[group] = categorySlug

            channels.add(
                LiveChannel(
                    streamId = "live_${idx + 1}",
                    num = idx + 1,
                    name = name,
                    streamIcon = logo.ifBlank { null },
                    categoryId = categorySlug,
                    groupName = group,
                    directStreamUrl = streamUrl
                )
            )
        }

        val cats = groupCategoriesMap.map { (grp, slug) ->
            LiveCategory(categoryId = slug, categoryName = grp)
        }
        return Pair(channels, sortCategories(cats))
    }

    private suspend fun parseAndCacheLiveList(): List<LiveChannel> = withContext(Dispatchers.IO) {
        parsedLiveChannels?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        if (AppStorage.isM3uMode()) {
            val content = if (AppStorage.isLocalM3uFile()) {
                AppStorage.getLocalM3uFileContent()
            } else {
                loadM3uContent(AppStorage.getM3uUrl())
            }
            if (!content.isNullOrBlank()) {
                val (channels, categories) = parseM3uText(content)
                if (channels.isNotEmpty()) {
                    parsedLiveChannels = channels
                    parsedLiveCategories = categories
                    return@withContext channels
                }
            }
            return@withContext emptyList()
        }

        val currentUrl = baseUrl

        // 1. Try Xtream get_live_streams from current server
        try {
            val xtreamJson = fetch(action = "get_live_streams")
            if (!xtreamJson.isNullOrBlank()) {
                val type = object : TypeToken<List<LiveChannel>>() {}.type
                val list: List<LiveChannel>? = gson.fromJson(xtreamJson, type)
                if (!list.isNullOrEmpty()) {
                    // Ensure categories are cached to match group names
                    val cats = getLiveCategories()
                    val catMap = cats.associate { it.categoryId to it.categoryName }
                    list.forEach { ch ->
                        if (ch.groupName.isBlank()) {
                            ch.groupName = catMap[ch.categoryId] ?: "GENERAL"
                        }
                    }
                    parsedLiveChannels = list
                    return@withContext list
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to parse xtream live streams for $currentUrl, using live playlist fallback", e)
        }

        // 2. Comprehensive fallback (as in v1.2.60): Load all live channels from Dropbox M3U playlist
        try {
            val content = fetchLiveM3uContent()
            if (!content.isNullOrBlank()) {
                val (channels, categories) = parseM3uText(content)
                if (channels.isNotEmpty()) {
                    parsedLiveChannels = channels
                    parsedLiveCategories = categories
                    return@withContext channels
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse live fallback M3U playlist", e)
        }

        return@withContext emptyList()
    }

    suspend fun getLiveCategories(): List<LiveCategory> = withContext(Dispatchers.IO) {
        parsedLiveCategories?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        if (AppStorage.isM3uMode()) {
            parseAndCacheLiveList()
            return@withContext parsedLiveCategories ?: emptyList()
        }

        val currentUrl = baseUrl

        // 1. Try get_live_categories from Xtream server
        try {
            val json = fetch(action = "get_live_categories")
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<LiveCategory>>() {}.type
                val list: List<LiveCategory>? = gson.fromJson(json, type)
                if (!list.isNullOrEmpty()) {
                    val sorted = sortCategories(list)
                    parsedLiveCategories = sorted
                    return@withContext sorted
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse live categories for $currentUrl from server", e)
        }

        // 2. Fallback to categories from Live list
        parseAndCacheLiveList()
        return@withContext parsedLiveCategories ?: emptyList()
    }

    private fun sortCategories(list: List<LiveCategory>): List<LiveCategory> {
        val priorityKeywords = listOf(
            "nacionales", "chile", "futbol", "fútbol", "espn", "tnt sports",
            "deportes motor", "tyc", "otros deportes", "cine premium", "hbo",
            "universal", "sony", "starz", "fox", "cine y series", "24/7",
            "cine 24/7", "infantil", "entretenimiento", "cultura", "musica", "música",
            "emisora", "eventos", "dgo", "claro", "fmh", "freetv"
        )
        return list.sortedWith(compareBy<LiveCategory> { cat ->
            val low = cat.categoryName.lowercase()
            val idx = priorityKeywords.indexOfFirst { low.contains(it) }
            if (idx >= 0) idx else priorityKeywords.size + 1
        }.thenBy { it.categoryName.lowercase() })
    }

    suspend fun getLiveChannels(categoryId: String? = null): List<LiveChannel> = withContext(Dispatchers.IO) {
        val allChannels = parseAndCacheLiveList()
        return@withContext if (categoryId.isNullOrBlank() || categoryId == "ALL") {
            allChannels
        } else {
            allChannels.filter { it.categoryId == categoryId }
        }
    }

    suspend fun getVodCategories(): List<VodCategory> = withContext(Dispatchers.IO) {
        val cacheKey = "vod_cats"
        (memoryCache[cacheKey] as? List<VodCategory>)?.let { return@withContext it }

        val json = fetch(action = "get_vod_categories") ?: return@withContext emptyList()
        val result = try {
            val type = object : TypeToken<List<VodCategory>>() {}.type
            val list: List<VodCategory> = gson.fromJson(json, type) ?: emptyList()
            list
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing vod categories", e)
            emptyList()
        }

        if (result.isNotEmpty()) {
            memoryCache[cacheKey] = result
        }
        return@withContext result
    }

    suspend fun getVodStreams(categoryId: String? = null): List<VodStream> = withContext(Dispatchers.IO) {
        val cacheKey = "vod_streams_${categoryId ?: "ALL"}"
        (memoryCache[cacheKey] as? List<VodStream>)?.let { return@withContext it }

        val params = mutableMapOf<String, String>()
        if (!categoryId.isNullOrBlank() && categoryId != "ALL") {
            params["category_id"] = categoryId
        }

        val json = fetch(action = "get_vod_streams", extraParams = params) ?: return@withContext emptyList()
        val result = try {
            val type = object : TypeToken<List<VodStream>>() {}.type
            val list: List<VodStream> = gson.fromJson(json, type) ?: emptyList()
            list
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing vod streams", e)
            emptyList()
        }

        if (result.isNotEmpty()) {
            memoryCache[cacheKey] = result
        }
        return@withContext result
    }

    suspend fun getVodDetail(streamId: String): VodDetailResponse? = withContext(Dispatchers.IO) {
        val json = fetch(action = "get_vod_info", extraParams = mapOf("vod_id" to streamId)) ?: return@withContext null
        return@withContext try {
            gson.fromJson(json, VodDetailResponse::class.java)
        } catch (e: Throwable) {
            Log.w(TAG, "VodDetail parse error: ${e.message}")
            null
        }
    }

    suspend fun getSeriesCategories(): List<SeriesCategory> = withContext(Dispatchers.IO) {
        val cacheKey = "series_cats"
        (memoryCache[cacheKey] as? List<SeriesCategory>)?.let { return@withContext it }

        val json = fetch(action = "get_series_categories") ?: return@withContext emptyList()
        val result = try {
            val type = object : TypeToken<List<SeriesCategory>>() {}.type
            val list: List<SeriesCategory> = gson.fromJson(json, type) ?: emptyList()
            list
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing series categories", e)
            emptyList()
        }

        if (result.isNotEmpty()) {
            memoryCache[cacheKey] = result
        }
        return@withContext result
    }

    suspend fun getSeriesList(categoryId: String? = null): List<SeriesItem> = withContext(Dispatchers.IO) {
        val cacheKey = "series_list_${categoryId ?: "ALL"}"
        (memoryCache[cacheKey] as? List<SeriesItem>)?.let { return@withContext it }

        val params = mutableMapOf<String, String>()
        if (!categoryId.isNullOrBlank() && categoryId != "ALL") {
            params["category_id"] = categoryId
        }

        val json = fetch(action = "get_series", extraParams = params) ?: return@withContext emptyList()
        val result = try {
            val type = object : TypeToken<List<SeriesItem>>() {}.type
            val list: List<SeriesItem> = gson.fromJson(json, type) ?: emptyList()
            list
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing series list", e)
            emptyList()
        }

        if (result.isNotEmpty()) {
            memoryCache[cacheKey] = result
        }
        return@withContext result
    }

                suspend fun getSeriesDetail(seriesId: String): Pair<SeriesDetailInfo?, Map<String, List<Episode>>> = withContext(Dispatchers.IO) {
        val username = AppStorage.getUsername()
        val password = AppStorage.getPassword()
        if (username.isBlank() || password.isBlank()) {
            return@withContext Pair(null, emptyMap())
        }
        val urlBuilder = "$baseUrl/player_api.php".toHttpUrlOrNull()?.newBuilder() ?: return@withContext Pair(null, emptyMap())
        urlBuilder.addQueryParameter("username", username)
        urlBuilder.addQueryParameter("password", password)
        urlBuilder.addQueryParameter("action", "get_series_info")
        urlBuilder.addQueryParameter("series_id", seriesId)

        val request = Request.Builder().url(urlBuilder.build()).header("User-Agent", DEFAULT_UA).build()
        
        return@withContext try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return@withContext Pair(null, emptyMap())
            }
            
            response.body!!.charStream().use { reader ->
                val jsonReader = com.google.gson.stream.JsonReader(reader)
                jsonReader.isLenient = true
                
                var info: SeriesDetailInfo? = null
                val episodesMap = mutableMapOf<String, List<Episode>>()
                
                jsonReader.beginObject()
                while (jsonReader.hasNext()) {
                    val name = jsonReader.nextName()
                    when (name) {
                        "info" -> {
                            try {
                                val infoJson = com.google.gson.JsonParser.parseReader(jsonReader)
                                info = gson.fromJson(infoJson, SeriesDetailInfo::class.java)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing info", e)
                            }
                        }
                        "episodes" -> {
                            try {
                                val peek = jsonReader.peek()
                                if (peek == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                                    jsonReader.beginObject()
                                    while (jsonReader.hasNext()) {
                                        val seasonKey = jsonReader.nextName()
                                        try {
                                            val seasonPeek = jsonReader.peek()
                                            val list = mutableListOf<Episode>()
                                            
                                            if (seasonPeek == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                                                jsonReader.beginArray()
                                                while (jsonReader.hasNext()) {
                                                    try {
                                                        val epJson = com.google.gson.JsonParser.parseReader(jsonReader)
                                                        val ep = gson.fromJson(epJson, Episode::class.java)
                                                        if (ep != null) list.add(ep)
                                                    } catch (e: Exception) { }
                                                }
                                                jsonReader.endArray()
                                            } else if (seasonPeek == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                                                jsonReader.beginObject()
                                                while (jsonReader.hasNext()) {
                                                    jsonReader.nextName() // key
                                                    try {
                                                        val epJson = com.google.gson.JsonParser.parseReader(jsonReader)
                                                        val ep = gson.fromJson(epJson, Episode::class.java)
                                                        if (ep != null) list.add(ep)
                                                    } catch (e: Exception) { }
                                                }
                                                jsonReader.endObject()
                                            } else {
                                                jsonReader.skipValue()
                                            }
                                            episodesMap[seasonKey] = list
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing season $seasonKey", e)
                                        }
                                    }
                                    jsonReader.endObject()
                                } else {
                                    jsonReader.skipValue()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing episodes", e)
                            }
                        }
                        else -> {
                            jsonReader.skipValue()
                        }
                    }
                }
                jsonReader.endObject()
                
                Pair(info, episodesMap)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "SeriesDetail stream parse error: ${e.message}")
            Pair(null, emptyMap())
        }
    }

    // --- Real Stream URLs ---
    fun getLiveStreamUrl(channelId: String): String {
        val cid = cleanId(channelId)
        val ch = parsedLiveChannels?.find { it.id == cid || it.streamId?.toString() == cid || it.name == cid }
        if (ch != null && !ch.directStreamUrl.isNullOrBlank()) {
            return ch.directStreamUrl!!
        }
        val u = AppStorage.getUsername()
        val p = AppStorage.getPassword()
        if (u.isNotBlank() && p.isNotBlank() && cid.isNotBlank()) {
            return "$baseUrl/live/$u/$p/$cid.ts"
        }
        return ""
    }

    private fun getHostCandidates(base: String): List<String> {
        val list = mutableListOf<String>()
        list.add(base)
        if (base.startsWith("https://", ignoreCase = true)) {
            val noScheme = base.substring(8).trimEnd('/')
            val hostOnly = noScheme.substringBefore(':')
            list.add("http://$noScheme")
            if (!noScheme.contains(":")) {
                list.add("http://$hostOnly:8080")
                list.add("https://$hostOnly:8080")
            }
        } else if (base.startsWith("http://", ignoreCase = true)) {
            val noScheme = base.substring(7).trimEnd('/')
            val hostOnly = noScheme.substringBefore(':')
            list.add("https://$noScheme")
            if (!noScheme.contains(":")) {
                list.add("http://$hostOnly:8080")
            }
        }
        return list.distinct()
    }

    fun getLiveStreamCandidates(channelId: String): List<String> {
        val cid = cleanId(channelId)
        val ch = parsedLiveChannels?.find { it.id == cid || it.streamId?.toString() == cid || it.name == cid }
        val list = mutableListOf<String>()
        if (ch != null && !ch.directStreamUrl.isNullOrBlank()) {
            list.add(ch.directStreamUrl!!)
        }
        val u = AppStorage.getUsername()
        val p = AppStorage.getPassword()
        if (u.isNotBlank() && p.isNotBlank() && cid.isNotBlank()) {
            val hosts = getHostCandidates(baseUrl)
            for (h in hosts) {
                list.add("$h/live/$u/$p/$cid.ts")
                list.add("$h/live/$u/$p/$cid.m3u8")
                list.add("$h/$u/$p/$cid")
                list.add("$h/live/$u/$p/$cid")
            }
        }
        return list.distinct()
    }

    fun getVodStreamUrl(streamId: String, extension: String = "mp4"): String {
        val sid = cleanId(streamId)
        val u = AppStorage.getUsername()
        val p = AppStorage.getPassword()
        val ext = extension.ifBlank { "mp4" }
        if (u.isBlank() || p.isBlank() || sid.isBlank()) {
            return ""
        }
        return "$baseUrl/movie/$u/$p/$sid.$ext"
    }

    fun getVodStreamCandidates(streamId: String, extension: String = "mp4"): List<String> {
        val sid = cleanId(streamId)
        val u = AppStorage.getUsername()
        val p = AppStorage.getPassword()
        if (u.isBlank() || p.isBlank() || sid.isBlank()) return emptyList()
        val ext = extension.ifBlank { "mp4" }
        val list = mutableListOf<String>()
        val hosts = getHostCandidates(baseUrl)
        for (h in hosts) {
            list.add("$h/movie/$u/$p/$sid.$ext")
            if (ext != "mp4") list.add("$h/movie/$u/$p/$sid.mp4")
            if (ext != "mkv") list.add("$h/movie/$u/$p/$sid.mkv")
            if (ext != "ts") list.add("$h/movie/$u/$p/$sid.ts")
        }
        return list.distinct()
    }

    fun getSeriesStreamUrl(episodeId: String, extension: String = "mp4"): String {
        val eid = cleanId(episodeId)
        val u = AppStorage.getUsername()
        val p = AppStorage.getPassword()
        val ext = extension.ifBlank { "mp4" }
        if (u.isBlank() || p.isBlank() || eid.isBlank()) {
            return ""
        }
        return "$baseUrl/series/$u/$p/$eid.$ext"
    }

    fun getSeriesStreamCandidates(episodeId: String, extension: String = "mp4"): List<String> {
        val eid = cleanId(episodeId)
        val u = AppStorage.getUsername()
        val p = AppStorage.getPassword()
        if (u.isBlank() || p.isBlank() || eid.isBlank()) return emptyList()
        val ext = extension.ifBlank { "mp4" }
        val list = mutableListOf<String>()
        val hosts = getHostCandidates(baseUrl)
        for (h in hosts) {
            list.add("$h/series/$u/$p/$eid.$ext")
            if (ext != "mp4") list.add("$h/series/$u/$p/$eid.mp4")
            if (ext != "mkv") list.add("$h/series/$u/$p/$eid.mkv")
        }
        return list.distinct()
    }
}
