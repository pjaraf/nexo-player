package com.example.data.link

import android.graphics.Bitmap
import android.util.Log
import com.example.data.storage.AppStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class TvLinkPayload(
    val isM3u: Boolean = false,
    val m3uUrl: String = "",
    val m3uName: String = "Lista M3U",
    val m3uContent: String? = null,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
)

object TvLinkManager {
    private const val TAG = "TvLinkManager"
    const val DEFAULT_PORT = 8990

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var currentPin: String = ""
        private set

    var currentTvUrl: String = ""
        private set

    private var failedPinAttempts = 0
    private var lastFailedAttemptMs = 0L
    private const val MAX_PIN_ATTEMPTS = 5
    private const val PIN_LOCKOUT_MS = 60_000L

    private var onLoginReceivedCallback: ((payload: TvLinkPayload) -> Unit)? = null

    /**
     * Start the local pairing server on the TV.
     */
    fun startTvPairingServer(
        onCredentialsReceived: (payload: TvLinkPayload) -> Unit
    ): Pair<String, String> {
        stopTvPairingServer()

        // Generate 6-digit random PIN
        currentPin = String.format("%06d", Random.nextInt(100000, 999999))
        onLoginReceivedCallback = onCredentialsReceived

        val localIp = getLocalIpAddress() ?: "127.0.0.1"
        currentTvUrl = "http://$localIp:$DEFAULT_PORT/link?pin=$currentPin"

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(localIp, DEFAULT_PORT))
                }
                Log.d(TAG, "TV Pairing Server listening on $localIp:$DEFAULT_PORT")

                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting pairing server", e)
            }
        }

        return Pair(currentPin, currentTvUrl)
    }

    /**
     * Stop the pairing server on TV
     */
    fun stopTvPairingServer() {
        try {
            serverJob?.cancel()
            serverSocket?.close()
            serverSocket = null
            serverJob = null
            onLoginReceivedCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10000
            val input: InputStream = socket.getInputStream()
            val output: OutputStream = socket.getOutputStream()

            // Read request line and headers byte by byte until \r\n\r\n
            val headerBytes = ByteArrayOutputStream()
            var b: Int
            var doubleNewlineFound = false
            while (input.read().also { b = it } != -1) {
                headerBytes.write(b)
                val size = headerBytes.size()
                val bytes = headerBytes.toByteArray()
                if (size >= 4 &&
                    bytes[size - 4] == '\r'.code.toByte() &&
                    bytes[size - 3] == '\n'.code.toByte() &&
                    bytes[size - 2] == '\r'.code.toByte() &&
                    bytes[size - 1] == '\n'.code.toByte()
                ) {
                    doubleNewlineFound = true
                    break
                }
                if (size > 16384) break
            }

            if (!doubleNewlineFound) {
                socket.close()
                return
            }

            val headerText = String(headerBytes.toByteArray(), Charsets.UTF_8)
            val headerLines = headerText.lines()
            if (headerLines.isEmpty()) {
                socket.close()
                return
            }

            val requestLine = headerLines[0]
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0].uppercase()
            val path = parts[1]

            var contentLength = 0
            for (line in headerLines) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (method == "OPTIONS") {
                val response = "HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                        "Content-Length: 0\r\n" +
                        "\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()
            } else if (method == "GET" && (path.startsWith("/link") || path == "/" || path.startsWith("/?"))) {
                val html = generateWebPairingPage(currentPin)
                val htmlBytes = html.toByteArray(Charsets.UTF_8)
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: ${htmlBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.write(htmlBytes)
                output.flush()
            } else if (method == "GET" && path.startsWith("/api/status")) {
                val json = """{"status":"ready","device":"nexo-tv"}"""
                val jsonBytes = json.toByteArray(Charsets.UTF_8)
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${jsonBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "\r\n" + json
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()
            } else if (method == "POST" && path.startsWith("/api/link")) {
                val bodyBytes = ByteArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val bytesRead = input.read(bodyBytes, totalRead, contentLength - totalRead)
                    if (bytesRead <= 0) break
                    totalRead += bytesRead
                }
                val body = String(bodyBytes, 0, totalRead, Charsets.UTF_8)

                try {
                    val jsonObj = gson.fromJson(body, JsonObject::class.java)
                    val pin = jsonObj.get("pin")?.asString?.replace(" ", "") ?: ""
                    val serverUrl = jsonObj.get("serverUrl")?.asString?.trim() ?: ""
                    val username = jsonObj.get("username")?.asString?.trim() ?: ""
                    val password = jsonObj.get("password")?.asString?.trim() ?: ""
                    val m3uUrl = jsonObj.get("m3uUrl")?.asString?.trim() ?: ""
                    val m3uName = jsonObj.get("m3uName")?.asString?.trim() ?: ""
                    val m3uContent = jsonObj.get("m3uContent")?.asString

                    val isExplicitM3u = jsonObj.get("isM3u")?.asBoolean == true
                    val isM3uDetected = isExplicitM3u ||
                            m3uUrl.isNotBlank() ||
                            !m3uContent.isNullOrBlank() ||
                            password == "m3u_direct" ||
                            password == "m3u_local_file" ||
                            serverUrl.contains(".m3u", ignoreCase = true) ||
                            serverUrl.contains(".m3u8", ignoreCase = true) ||
                            serverUrl.startsWith("local://", ignoreCase = true)

                    val cleanM3uUrl = when {
                        m3uUrl.isNotBlank() -> m3uUrl
                        serverUrl.startsWith("http://", ignoreCase = true) || serverUrl.startsWith("https://", ignoreCase = true) -> serverUrl
                        else -> ""
                    }
                    val playlistName = m3uName.ifBlank { username.ifBlank { "Lista M3U" } }

                    val isValidM3u = isM3uDetected && (cleanM3uUrl.isNotBlank() || !m3uContent.isNullOrBlank())
                    val isValidXtream = username.isNotBlank() && password.isNotBlank()

                    val now = System.currentTimeMillis()
                    if (failedPinAttempts >= MAX_PIN_ATTEMPTS && now - lastFailedAttemptMs < PIN_LOCKOUT_MS) {
                        val respJson = """{"success":false,"message":"Demasiados intentos. Espera un minuto."}"""
                        val respBytes = respJson.toByteArray(Charsets.UTF_8)
                        val response = "HTTP/1.1 429 Too Many Requests\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${respBytes.size}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "\r\n" + respJson
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.flush()
                        return
                    }

                    if (pin == currentPin && (isValidM3u || isValidXtream)) {
                        failedPinAttempts = 0
                        val respJson = """{"success":true,"message":"Vinculación exitosa. Iniciando en la TV..."}"""
                        val respBytes = respJson.toByteArray(Charsets.UTF_8)
                        val response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${respBytes.size}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "\r\n" + respJson
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.flush()

                        val payload = TvLinkPayload(
                            isM3u = isM3uDetected,
                            m3uUrl = cleanM3uUrl,
                            m3uName = playlistName,
                            m3uContent = m3uContent,
                            serverUrl = serverUrl,
                            username = username,
                            password = password
                        )

                        // Notify TV to login immediately
                        CoroutineScope(Dispatchers.Main).launch {
                            onLoginReceivedCallback?.invoke(payload)
                        }
                    } else {
                        failedPinAttempts++
                        lastFailedAttemptMs = System.currentTimeMillis()
                        val respJson = """{"success":false,"message":"Código PIN inválido o datos incompletos"}"""
                        val respBytes = respJson.toByteArray(Charsets.UTF_8)
                        val response = "HTTP/1.1 400 Bad Request\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${respBytes.size}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "\r\n" + respJson
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                } catch (e: Exception) {
                    val respJson = """{"success":false,"message":"Error de formato JSON"}"""
                    val respBytes = respJson.toByteArray(Charsets.UTF_8)
                    val response = "HTTP/1.1 400 Bad Request\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${respBytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "\r\n" + respJson
                    output.write(response.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            } else {
                val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                output.write(notFound.toByteArray())
                output.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    /**
     * Mobile App Client: Sends active session credentials or M3U list to a TV using IP / PIN.
     */
    suspend fun sendCurrentSessionToTv(
        tvIpOrUrl: String,
        pin: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanIp = tvIpOrUrl.replace("http://", "").replace("https://", "").substringBefore(":").substringBefore("/")
            val targetUrl = "http://$cleanIp:$DEFAULT_PORT/api/link"

            val isM3u = AppStorage.isM3uMode()
            val isLocalFile = AppStorage.isLocalM3uFile()
            val m3uUrl = AppStorage.getM3uUrl()
            val username = AppStorage.getUsername()
            val password = AppStorage.getPassword()
            val serverUrl = AppStorage.getServerUrl()

            val json = JsonObject().apply {
                addProperty("pin", pin.replace(" ", ""))
                if (isM3u) {
                    addProperty("isM3u", true)
                    addProperty("m3uName", username.ifBlank { "Lista M3U" })
                    if (isLocalFile) {
                        addProperty("m3uContent", AppStorage.getLocalM3uFileContent().orEmpty())
                        addProperty("m3uUrl", "")
                        addProperty("serverUrl", "local://custom_playlist.m3u")
                        addProperty("username", username)
                        addProperty("password", "m3u_local_file")
                    } else {
                        val finalUrl = m3uUrl.ifBlank { serverUrl }
                        addProperty("m3uUrl", finalUrl)
                        addProperty("serverUrl", finalUrl)
                        addProperty("username", username)
                        addProperty("password", "m3u_direct")
                    }
                } else {
                    addProperty("isM3u", false)
                    addProperty("serverUrl", serverUrl)
                    addProperty("username", username)
                    addProperty("password", password)
                }
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(targetUrl)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success("¡Sesión transferida con éxito! Tu televisor está iniciando...")
                } else {
                    val message = try {
                        gson.fromJson(respStr, JsonObject::class.java).get("message")?.asString ?: "Código PIN incorrecto"
                    } catch (e: Exception) {
                        "Error al conectar con la TV (Código ${response.code})"
                    }
                    Result.failure(Exception(message))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendCurrentSessionToTv failure", e)
            Result.failure(Exception("No se pudo contactar al televisor. Asegúrate de estar en la misma red Wi-Fi."))
        }
    }

    /**
     * Mobile App Client: Sends active session credentials to a TV using IP / PIN.
     */
    suspend fun sendCredentialsToTv(
        tvIpOrUrl: String,
        pin: String,
        serverUrl: String,
        username: String,
        password: String
    ): Result<String> {
        return sendCurrentSessionToTv(tvIpOrUrl, pin)
    }

    /**
     * Scan local Wi-Fi subnet for any active Nexus TV listening on port 8990.
     */
    suspend fun discoverTvOnLocalSubnet(): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DiscoveredTv>()
        val localIp = getLocalIpAddress() ?: return@withContext emptyList()
        val subnetPrefix = localIp.substringBeforeLast(".") + "."

        val jobs = (1..254).map { i ->
            async {
                val testIp = "$subnetPrefix$i"
                try {
                    val url = "http://$testIp:$DEFAULT_PORT/api/status"
                    val request = Request.Builder().url(url).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            val json = gson.fromJson(body, JsonObject::class.java)
                            val status = json.get("status")?.asString ?: ""
                            if (status == "ready") {
                                synchronized(discovered) {
                                    discovered.add(DiscoveredTv(ip = testIp))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignored (host not active)
                }
            }
        }
        jobs.awaitAll()
        discovered
    }

    data class DiscoveredTv(val ip: String, val pin: String = "")

    /**
     * Generates a high-contrast QR code bitmap using ZXing.
     */
    fun generateQrCodeBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        return bitmap
    }

    /**
     * Get the device's IPv4 address on Wi-Fi or Ethernet
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP", e)
        }
        return null
    }

    /**
     * Returns a responsive web app for phone browsers that scan the QR code.
     */
    private fun generateWebPairingPage(pin: String): String {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Vincular Nexo TV</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                    body { background: #0a0a0f; color: #ffffff; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
                    .card { background: #14141e; border: 1px solid rgba(255,255,255,0.12); border-radius: 20px; max-width: 440px; width: 100%; padding: 28px 22px; box-shadow: 0 20px 40px rgba(0,0,0,0.6); }
                    .logo { text-align: center; margin-bottom: 20px; }
                    .logo-badge { display: inline-flex; align-items: center; justify-content: center; width: 56px; height: 56px; background: radial-gradient(circle, #ab1225, #780c19); border-radius: 50%; box-shadow: 0 4px 15px rgba(229,9,20,0.4); font-size: 28px; font-weight: 900; color: white; margin-bottom: 10px; }
                    h1 { font-size: 20px; font-weight: 800; text-align: center; margin-bottom: 4px; }
                    p.sub { font-size: 13px; color: #a0a0b0; text-align: center; margin-bottom: 18px; }
                    .pin-box { background: rgba(229,9,20,0.12); border: 1px dashed #e50914; border-radius: 12px; padding: 10px; text-align: center; font-size: 22px; font-weight: 900; letter-spacing: 6px; color: #ffffff; margin-bottom: 18px; }
                    .tabs { display: flex; background: #1c1c28; border-radius: 10px; padding: 4px; margin-bottom: 18px; gap: 4px; }
                    .tab-btn { flex: 1; padding: 10px; border: none; background: transparent; color: #a0a0b0; font-size: 13px; font-weight: 700; border-radius: 8px; cursor: pointer; transition: all 0.2s; }
                    .tab-btn.active { background: #e50914; color: white; }
                    .field { margin-bottom: 14px; }
                    label { display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; color: #a0a0b0; margin-bottom: 6px; }
                    input { width: 100%; background: #1c1c28; border: 1px solid rgba(255,255,255,0.15); border-radius: 10px; padding: 13px; font-size: 14px; color: white; outline: none; }
                    input:focus { border-color: #e50914; }
                    button.submit-btn { width: 100%; background: #e50914; color: white; border: none; border-radius: 10px; padding: 15px; font-size: 15px; font-weight: 800; cursor: pointer; margin-top: 10px; transition: all 0.2s; }
                    button.submit-btn:active { transform: scale(0.98); opacity: 0.9; }
                    .alert { padding: 12px; border-radius: 8px; font-size: 13px; margin-top: 14px; text-align: center; display: none; }
                    .alert.success { background: rgba(16,185,129,0.2); border: 1px solid #10b981; color: #10b981; }
                    .alert.error { background: rgba(239,68,68,0.2); border: 1px solid #ef4444; color: #ef4444; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="logo">
                        <div class="logo-badge">N</div>
                        <h1>Iniciar Sesión en TV</h1>
                        <p class="sub">Transfiere tu lista M3U o cuenta al televisor</p>
                    </div>

                    <div class="pin-box">$pin</div>

                    <div class="tabs">
                        <button type="button" class="tab-btn active" id="tabM3uBtn" onclick="switchTab('m3u')">Lista M3U (URL)</button>
                        <button type="button" class="tab-btn" id="tabXtreamBtn" onclick="switchTab('xtream')">Usuario / Contraseña</button>
                    </div>

                    <div id="alert" class="alert"></div>

                    <!-- M3U Form -->
                    <form id="m3uForm" onsubmit="submitM3uForm(event)">
                        <div class="field">
                            <label>Enlace / URL de la Lista M3U</label>
                            <input type="url" id="m3uUrlInput" placeholder="http://servidor.com/playlist.m3u" required />
                        </div>
                        <div class="field">
                            <label>Nombre de la Lista (Opcional)</label>
                            <input type="text" id="m3uNameInput" placeholder="Mi Lista M3U" />
                        </div>
                        <button type="submit" class="submit-btn" id="submitM3uBtn">Iniciar Lista en Televisor</button>
                    </form>

                    <!-- Xtream Form -->
                    <form id="xtreamForm" style="display: none;" onsubmit="submitXtreamForm(event)">
                        <div class="field">
                            <label>Servidor / URL (Opcional - Nexo Fusion por defecto)</label>
                            <input type="text" id="serverUrlInput" placeholder="https://nexo.fusionx.cl" value="https://nexo.fusionx.cl" />
                        </div>
                        <div class="field">
                            <label>Usuario</label>
                            <input type="text" id="usernameInput" placeholder="Tu usuario" required />
                        </div>
                        <div class="field">
                            <label>Contraseña</label>
                            <input type="password" id="passwordInput" placeholder="Tu contraseña" required />
                        </div>
                        <button type="submit" class="submit-btn" id="submitXtreamBtn">Iniciar Sesión en Televisor</button>
                    </form>
                </div>

                <script>
                    let activeTab = 'm3u';

                    function switchTab(tab) {
                        activeTab = tab;
                        document.getElementById('tabM3uBtn').className = tab === 'm3u' ? 'tab-btn active' : 'tab-btn';
                        document.getElementById('tabXtreamBtn').className = tab === 'xtream' ? 'tab-btn active' : 'tab-btn';
                        document.getElementById('m3uForm').style.display = tab === 'm3u' ? 'block' : 'none';
                        document.getElementById('xtreamForm').style.display = tab === 'xtream' ? 'block' : 'none';
                        document.getElementById('alert').style.display = 'none';
                    }

                    async function submitM3uForm(e) {
                        e.preventDefault();
                        const btn = document.getElementById('submitM3uBtn');
                        const url = document.getElementById('m3uUrlInput').value.trim();
                        const name = document.getElementById('m3uNameInput').value.trim() || 'Lista M3U';

                        await sendPayload({
                            pin: '$pin',
                            isM3u: true,
                            m3uUrl: url,
                            m3uName: name,
                            serverUrl: url,
                            username: name,
                            password: 'm3u_direct'
                        }, btn);
                    }

                    async function submitXtreamForm(e) {
                        e.preventDefault();
                        const btn = document.getElementById('submitXtreamBtn');
                        const serverUrl = document.getElementById('serverUrlInput').value.trim();
                        const username = document.getElementById('usernameInput').value.trim();
                        const password = document.getElementById('passwordInput').value.trim();

                        await sendPayload({
                            pin: '$pin',
                            isM3u: false,
                            serverUrl: serverUrl,
                            username: username,
                            password: password
                        }, btn);
                    }

                    async function sendPayload(payload, btn) {
                        const alertBox = document.getElementById('alert');
                        btn.disabled = true;
                        const originalText = btn.innerText;
                        btn.innerText = 'Enviando a la TV...';
                        alertBox.style.display = 'none';

                        try {
                            const res = await fetch('/api/link', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(payload)
                            });
                            const data = await res.json();
                            alertBox.style.display = 'block';
                            if (data.success) {
                                alertBox.className = 'alert success';
                                alertBox.innerText = '¡Listo! Tu lista se está iniciando en la televisión.';
                                btn.innerText = '✓ Vinculado con Éxito';
                            } else {
                                alertBox.className = 'alert error';
                                alertBox.innerText = data.message || 'Error al vincular con la TV';
                                btn.disabled = false;
                                btn.innerText = originalText;
                            }
                        } catch (err) {
                            alertBox.style.display = 'block';
                            alertBox.className = 'alert error';
                            alertBox.innerText = 'Error de conexión con el televisor.';
                            btn.disabled = false;
                            btn.innerText = originalText;
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
