import re

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'r') as f:
    content = f.read()

# Fix login
login_bad = """    suspend fun login(username: String, pass: String): LoginResponse? {
        val json = fetch(
            action = "",
            extraParams = mapOf("username" to username, "password" to pass)
        ) ?: return@withContext null
        return@withContext try {
            val response = gson.fromJson(json, LoginResponse::class.java)
            response
        } catch (e: Throwable) {
            Log.e(TAG, "Login parse error: ${e.message}")
            return@withContext null
        }
    }"""
login_good = """    suspend fun login(username: String, pass: String): LoginResponse? {
        val json = fetch(
            action = "",
            extraParams = mapOf("username" to username, "password" to pass)
        ) ?: return null
        return try {
            val response = gson.fromJson(json, LoginResponse::class.java)
            response
        } catch (e: Throwable) {
            Log.e(TAG, "Login parse error: ${e.message}")
            return null
        }
    }"""
content = content.replace(login_bad, login_good)

# find functions without withContext that use return@withContext
content = re.sub(r'fun getLiveChannels\([^)]+\): List<LiveChannel> \{.*?\}', lambda m: m.group(0).replace('return@withContext', 'return'), content, flags=re.DOTALL)
content = re.sub(r'fun getLiveStreamUrl\([^)]+\): String \{.*?\}', lambda m: m.group(0).replace('return@withContext', 'return'), content, flags=re.DOTALL)
content = re.sub(r'fun getVodStreamUrl\([^)]+\): String \{.*?\}', lambda m: m.group(0).replace('return@withContext', 'return'), content, flags=re.DOTALL)
content = re.sub(r'fun getSeriesStreamUrl\([^)]+\): String \{.*?\}', lambda m: m.group(0).replace('return@withContext', 'return'), content, flags=re.DOTALL)


with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'w') as f:
    f.write(content)
