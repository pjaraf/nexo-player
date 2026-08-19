import re

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'r') as f:
    content = f.read()

# Replace getSeriesDetail with a streaming version
streaming_version = """    suspend fun getSeriesDetail(seriesId: String): Pair<SeriesDetailInfo?, Map<String, List<Episode>>> = withContext(Dispatchers.IO) {
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
            
            // Parse directly from stream to avoid OOM on large JSONs
            response.body!!.charStream().use { reader ->
                val jsonObj = gson.fromJson(reader, JsonObject::class.java)
                val infoObj = jsonObj.getAsJsonObject("info")
                val info = infoObj?.let { gson.fromJson(it, SeriesDetailInfo::class.java) }

                val episodesMap = mutableMapOf<String, List<Episode>>()
                val epElement = jsonObj.get("episodes")
                if (epElement != null && epElement.isJsonObject) {
                    val epObj = epElement.asJsonObject
                    for (seasonKey in epObj.keySet()) {
                        try {
                            val seasonArr = epObj.getAsJsonArray(seasonKey)
                            val type = object : TypeToken<List<Episode>>() {}.type
                            val list: List<Episode> = gson.fromJson(seasonArr, type) ?: emptyList()
                            episodesMap[seasonKey] = list
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing season $seasonKey: ${e.message}")
                        }
                    }
                }
                Pair(info, episodesMap)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "SeriesDetail stream parse error: ${e.message}")
            Pair(null, emptyMap())
        }
    }"""

old_method = re.search(r'suspend fun getSeriesDetail.*?catch \(e: Throwable\) \{.*?\}\n    \}', content, re.DOTALL).group(0)

content = content.replace(old_method, streaming_version)

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'w') as f:
    f.write(content)
