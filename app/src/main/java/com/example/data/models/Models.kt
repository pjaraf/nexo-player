package com.example.data.models

import com.google.gson.annotations.SerializedName

data class UserInfo(
    val username: String? = null,
    val status: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    @SerializedName("is_trial") val isTrial: String? = null,
    @SerializedName("active_cons") val activeCons: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("max_connections") val maxConnections: String? = null,
    val auth: Any? = null
)

data class ServerInfo(
    val url: String? = null,
    val port: String? = null,
    val httpsPort: String? = null,
    val serverProtocol: String? = null,
    val timezone: String? = null
)

data class LoginResponse(
    @SerializedName("user_info") val userInfo: UserInfo? = null,
    @SerializedName("server_info") val serverInfo: ServerInfo? = null
)

data class LiveCategory(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = ""
)

fun cleanId(value: Any?): String {
    if (value == null) return ""
    val s = value.toString().trim()
    if (s.isEmpty()) return ""
    return if (s.endsWith(".0") && s.toDoubleOrNull() != null) {
        s.substringBefore(".0")
    } else {
        s
    }
}

data class LiveChannel(
    @SerializedName("stream_id") val streamId: Any? = null,
    @SerializedName("num") val num: Any? = null,
    val name: String = "",
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    var groupName: String = "",
    @SerializedName("direct_stream_url") var directStreamUrl: String? = null
) {
    val id: String
        get() = cleanId(streamId)
}

data class VodCategory(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = ""
)

data class VodStream(
    @SerializedName("stream_id") val streamId: Any? = null,
    @SerializedName("num") val num: Any? = null,
    val name: String = "",
    val title: String? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    val rating: Any? = null,
    @SerializedName("rating_5based") val rating5Based: Any? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = "mp4",
    val added: String? = null
) {
    val id: String
        get() = cleanId(streamId)

    val displayName: String
        get() = name.ifBlank { title ?: "Película" }

    val formattedRating: String?
        get() = rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }
}

data class VodInfo(
    val name: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null,
    val plot: String? = null,
    val description: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("releasedate") val releasedateAlt: String? = null,
    val rating: Any? = null,
    val duration: String? = null
) {
    val coverImage: String?
        get() = movieImage ?: backdropPath?.firstOrNull()
}

data class MovieData(
    @SerializedName("stream_id") val streamId: Any? = null,
    val name: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = "mp4",
    @SerializedName("stream_icon") val streamIcon: String? = null
)

data class VodDetailResponse(
    val info: VodInfo? = null,
    @SerializedName("movie_data") val movieData: MovieData? = null
)

data class SeriesCategory(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = ""
)

data class SeriesItem(
    @SerializedName("series_id") val seriesId: Any? = null,
    @SerializedName("num") val num: Any? = null,
    val name: String = "",
    val title: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val rating: Any? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("last_modified") val lastModified: String? = null
) {
    val id: String
        get() = cleanId(seriesId)

    val displayName: String
        get() = name.ifBlank { title ?: "Serie" }

    val formattedRating: String?
        get() = rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }
}

data class SeriesDetailInfo(
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("releasedate") val releaseDateAlt: String? = null,
    val rating: Any? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null
)

data class EpisodeInfo(
    val plot: String? = null,
    val duration: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null
)

data class Episode(
    val id: Any? = null,
    @SerializedName("stream_id") val streamId: Any? = null,
    @SerializedName("episode_num") val episodeNum: Any? = null,
    val season: Any? = null,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = "mp4",
    val info: EpisodeInfo? = null
) {
    val epId: String
        get() = cleanId(id ?: streamId)

    val epNumber: Int
        get() = (episodeNum?.toString()?.toDoubleOrNull()?.toInt()) ?: 1

    val displayTitle: String
        get() = title ?: name ?: "Episodio $epNumber"
}

data class Season(
    @SerializedName("season_number") val seasonNumber: Int = 1,
    val name: String? = null,
    @SerializedName("episode_count") val episodeCount: Int = 0
)

data class Profile(
    val id: String,
    val name: String,
    val color: String,
    val isKids: Boolean = false
)

data class FavItem(
    val id: String,
    val title: String,
    val image: String? = null,
    val kind: String, // "live", "movies", "series"
    val addedAt: Long = System.currentTimeMillis()
)

data class ProgressItem(
    val key: String, // "${kind}:${id}"
    val kind: String, // "movie" or "series"
    val id: String,
    val episodeId: String? = null,
    val title: String,
    val image: String? = null,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)
