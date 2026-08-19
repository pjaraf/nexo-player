import re

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'r') as f:
    content = f.read()

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
            
            // Using JsonReader directly to avoid loading entire string into memory and handle malformed fields safely
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
                                info = gson.fromJson(jsonReader, SeriesDetailInfo::class.java)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing info", e)
                                jsonReader.skipValue()
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
                                            val epType = object : TypeToken<List<Episode>>() {}.type
                                            val list: List<Episode> = gson.fromJson(jsonReader, epType) ?: emptyList()
                                            episodesMap[seasonKey] = list
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing season $seasonKey", e)
                                            jsonReader.skipValue()
                                        }
                                    }
                                    jsonReader.endObject()
                                } else {
                                    jsonReader.skipValue() // Skip if episodes is an array or string
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing episodes", e)
                                jsonReader.skipValue()
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
    }"""

old_method = re.search(r'    // Data class to avoid JsonObject memory overhead\n    private data class SeriesDetailResponse\(\n        val info: SeriesDetailInfo\?,\n        val episodes: Map<String, List<Episode>>\?\n    \)\n\n    suspend fun getSeriesDetail.*?catch \(e: Throwable\) \{.*?\}\n    \}', content, re.DOTALL)
if not old_method:
    print("Could not find method to replace")
else:
    content = content.replace(old_method.group(0), streaming_version)
    with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'w') as f:
        f.write(content)
