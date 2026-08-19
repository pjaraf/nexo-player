import re

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'r') as f:
    content = f.read()

streaming_version = """    // Data class to avoid JsonObject memory overhead
    private data class SeriesDetailResponse(
        val info: SeriesDetailInfo?,
        val episodes: Map<String, List<Episode>>?
    )

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
            
            // Parse directly from stream into mapped objects to prevent OOM
            response.body!!.charStream().use { reader ->
                val parsed = gson.fromJson(reader, SeriesDetailResponse::class.java)
                val info = parsed.info
                val episodesMap = parsed.episodes ?: emptyMap()
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
