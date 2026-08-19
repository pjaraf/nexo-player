import re

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'r') as f:
    content = f.read()

# Replace getSeriesDetail
old_get_series = """    suspend fun getSeriesDetail(seriesId: String): Pair<SeriesDetailInfo?, Map<String, List<Episode>>> {
        val json = fetch(action = "get_series_info", extraParams = mapOf("series_id" to seriesId)) ?: return Pair(null, emptyMap())
        return try {
            val jsonObj = gson.fromJson(json, JsonObject::class.java)
            val infoObj = jsonObj.getAsJsonObject("info")
            val info = infoObj?.let { gson.fromJson(it, SeriesDetailInfo::class.java) }

            val episodesMap = mutableMapOf<String, List<Episode>>()
            val epObj = jsonObj.getAsJsonObject("episodes")
            if (epObj != null) {
                for (seasonKey in epObj.keySet()) {
                    val seasonArr = epObj.getAsJsonArray(seasonKey)
                    val type = object : TypeToken<List<Episode>>() {}.type
                    val list: List<Episode> = gson.fromJson(seasonArr, type) ?: emptyList()
                    episodesMap[seasonKey] = list
                }
            }
            Pair(info, episodesMap)
        } catch (e: Throwable) {
            Log.w(TAG, "SeriesDetail parse error: ${e.message}")
            Pair(null, emptyMap())
        }
    }"""

new_get_series = """    suspend fun getSeriesDetail(seriesId: String): Pair<SeriesDetailInfo?, Map<String, List<Episode>>> = withContext(Dispatchers.IO) {
        val json = fetch(action = "get_series_info", extraParams = mapOf("series_id" to seriesId)) ?: return@withContext Pair(null, emptyMap())
        return@withContext try {
            val jsonObj = gson.fromJson(json, JsonObject::class.java)
            val infoObj = jsonObj.getAsJsonObject("info")
            val info = infoObj?.let { gson.fromJson(it, SeriesDetailInfo::class.java) }

            val episodesMap = mutableMapOf<String, List<Episode>>()
            val epObj = jsonObj.getAsJsonObject("episodes")
            if (epObj != null) {
                for (seasonKey in epObj.keySet()) {
                    val seasonArr = epObj.getAsJsonArray(seasonKey)
                    val type = object : TypeToken<List<Episode>>() {}.type
                    val list: List<Episode> = gson.fromJson(seasonArr, type) ?: emptyList()
                    episodesMap[seasonKey] = list
                }
            }
            Pair(info, episodesMap)
        } catch (e: Throwable) {
            Log.w(TAG, "SeriesDetail parse error: ${e.message}")
            Pair(null, emptyMap())
        }
    }"""

content = content.replace(old_get_series, new_get_series)

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'w') as f:
    f.write(content)
