package com.example.data.link

import android.graphics.Bitmap
import android.util.Log
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object TvLinkManager {
    private const val TAG = "TvLinkManager"
    const val DEFAULT_PORT = 8990

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    var currentPin: String = ""
        private set

    var currentTvUrl: String = ""
        private set

    private var onLoginReceivedCallback: ((serverUrl: String, user: String, pass: String) -> Unit)? = null

    /**
     * Start the local pairing server on the TV.
     */
    fun startTvPairingServer(
        onCredentialsReceived: (serverUrl: String, user: String, pass: String) -> Unit
    ): Pair<String, String> {
        stopTvPairingServer()

        // Generate 6-digit random PIN
        currentPin = String.format("%06d", Random.nextInt(100000, 999999))
        onLoginReceivedCallback = onCredentialsReceived

        val localIp = getLocalIpAddress() ?: "127.0.0.1"
        currentTvUrl = "http://$localIp:$DEFAULT_PORT/link?pin=$currentPin"

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(DEFAULT_PORT)
                Log.d(TAG, "TV Pairing Server listening on port $DEFAULT_PORT, IP: $localIp, PIN: $currentPin")

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
            socket.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output: OutputStream = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (method == "GET" && (path.startsWith("/link") || path == "/" || path.startsWith("/?"))) {
                // Return Mobile Web Pairing Page
                val html = generateWebPairingPage(currentPin)
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: ${html.toByteArray(Charsets.UTF_8).size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "\r\n" + html
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()
            } else if (method == "GET" && path.startsWith("/api/status")) {
                val json = """{"status":"ready","pin":"$currentPin"}"""
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${json.length}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "\r\n" + json
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()
            } else if (method == "POST" && path.startsWith("/api/link")) {
                val bodyChars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = reader.read(bodyChars, read, contentLength - read)
                    if (count <= 0) break
                    read += count
                }
                val body = String(bodyChars)

                try {
                    val jsonObj = gson.fromJson(body, JsonObject::class.java)
                    val pin = jsonObj.get("pin")?.asString?.replace(" ", "") ?: ""
                    val serverUrl = jsonObj.get("serverUrl")?.asString ?: ""
                    val username = jsonObj.get("username")?.asString ?: ""
                    val password = jsonObj.get("password")?.asString ?: ""

                    if (pin == currentPin && username.isNotBlank() && password.isNotBlank()) {
                        val respJson = """{"success":true,"message":"Vinculación exitosa"}"""
                        val response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${respJson.length}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "\r\n" + respJson
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.flush()

                        // Notify TV to login
                        CoroutineScope(Dispatchers.Main).launch {
                            onLoginReceivedCallback?.invoke(serverUrl, username, password)
                        }
                    } else {
                        val respJson = """{"success":false,"message":"Código PIN inválido"}"""
                        val response = "HTTP/1.1 400 Bad Request\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${respJson.length}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "\r\n" + respJson
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                } catch (e: Exception) {
                    val respJson = """{"success":false,"message":"Error de formato JSON"}"""
                    val response = "HTTP/1.1 400 Bad Request\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${respJson.length}\r\n" +
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
     * Mobile App Client: Sends active session credentials to a TV using IP / PIN.
     */
    suspend fun sendCredentialsToTv(
        tvIpOrUrl: String,
        pin: String,
        serverUrl: String,
        username: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanIp = tvIpOrUrl.replace("http://", "").replace("https://", "").substringBefore(":").substringBefore("/")
            val targetUrl = "http://$cleanIp:$DEFAULT_PORT/api/link"

            val json = JsonObject().apply {
                addProperty("pin", pin.replace(" ", ""))
                addProperty("serverUrl", serverUrl)
                addProperty("username", username)
                addProperty("password", password)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(targetUrl)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success("¡Sesión transferida con éxito al televisor!")
                } else {
                    val message = try {
                        gson.fromJson(respStr, JsonObject::class.java).get("message")?.asString ?: "PIN incorrecto"
                    } catch (e: Exception) {
                        "Error al conectar con la TV (Código ${response.code})"
                    }
                    Result.failure(Exception(message))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("No se pudo contactar al televisor. Asegúrate de estar en la misma red Wi-Fi."))
        }
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
                            val pin = json.get("pin")?.asString ?: ""
                            synchronized(discovered) {
                                discovered.add(DiscoveredTv(ip = testIp, pin = pin))
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

    data class DiscoveredTv(val ip: String, val pin: String)

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
                    .card { background: #14141e; border: 1px solid rgba(255,255,255,0.12); border-radius: 20px; max-width: 420px; width: 100%; padding: 32px 24px; box-shadow: 0 20px 40px rgba(0,0,0,0.6); }
                    .logo { text-align: center; margin-bottom: 24px; }
                    .logo-badge { display: inline-flex; align-items: center; justify-content: center; width: 64px; height: 64px; background: radial-gradient(circle, #ab1225, #780c19); border-radius: 50%; box-shadow: 0 4px 15px rgba(229,9,20,0.4); font-size: 32px; font-weight: 900; color: white; margin-bottom: 12px; }
                    h1 { font-size: 22px; font-weight: 800; text-align: center; margin-bottom: 6px; }
                    p.sub { font-size: 13px; color: #a0a0b0; text-align: center; margin-bottom: 24px; }
                    .pin-box { background: rgba(229,9,20,0.12); border: 1px dashed #e50914; border-radius: 12px; padding: 12px; text-align: center; font-size: 24px; font-weight: 900; letter-spacing: 6px; color: #ffffff; margin-bottom: 24px; }
                    .field { margin-bottom: 16px; }
                    label { display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; color: #a0a0b0; margin-bottom: 6px; }
                    input { width: 100%; background: #1c1c28; border: 1px solid rgba(255,255,255,0.15); border-radius: 10px; padding: 14px; font-size: 15px; color: white; outline: none; }
                    input:focus { border-color: #e50914; }
                    button { width: 100%; background: #e50914; color: white; border: none; border-radius: 10px; padding: 16px; font-size: 16px; font-weight: 800; cursor: pointer; margin-top: 12px; transition: all 0.2s; }
                    button:active { transform: scale(0.98); opacity: 0.9; }
                    .alert { padding: 12px; border-radius: 8px; font-size: 13px; margin-top: 16px; text-align: center; display: none; }
                    .alert.success { background: rgba(16,185,129,0.2); border: 1px solid #10b981; color: #10b981; }
                    .alert.error { background: rgba(239,68,68,0.2); border: 1px solid #ef4444; color: #ef4444; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="logo">
                        <div class="logo-badge">N</div>
                        <h1>Iniciar Sesión en TV</h1>
                        <p class="sub">Transfiere tus credenciales al televisor</p>
                    </div>

                    <div class="pin-box">$pin</div>

                    <div id="alert" class="alert"></div>

                    <form id="linkForm" onsubmit="submitForm(event)">
                        <input type="hidden" id="pin" value="$pin" />
                        <div class="field">
                            <label>Servidor / URL</label>
                            <input type="text" id="serverUrl" placeholder="http://servidor.com:8080" required />
                        </div>
                        <div class="field">
                            <label>Usuario</label>
                            <input type="text" id="username" placeholder="Tu usuario" required />
                        </div>
                        <div class="field">
                            <label>Contraseña</label>
                            <input type="password" id="password" placeholder="Tu contraseña" required />
                        </div>
                        <button type="submit" id="submitBtn">Vincular Televisor Ahora</button>
                    </form>
                </div>

                <script>
                    async function submitForm(e) {
                        e.preventDefault();
                        const btn = document.getElementById('submitBtn');
                        const alertBox = document.getElementById('alert');
                        btn.disabled = true;
                        btn.innerText = 'Vinculando con la TV...';
                        alertBox.style.display = 'none';

                        const payload = {
                            pin: document.getElementById('pin').value,
                            serverUrl: document.getElementById('serverUrl').value,
                            username: document.getElementById('username').value,
                            password: document.getElementById('password').value
                        };

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
                                alertBox.innerText = '¡Listo! Tu sesión se ha iniciado en la televisión.';
                                btn.innerText = '✓ Vinculado con Éxito';
                            } else {
                                alertBox.className = 'alert error';
                                alertBox.innerText = data.message || 'Error al vincular';
                                btn.disabled = false;
                                btn.innerText = 'Reintentar';
                            }
                        } catch (err) {
                            alertBox.style.display = 'block';
                            alertBox.className = 'alert error';
                            alertBox.innerText = 'Error de conexión con la TV.';
                            btn.disabled = false;
                            btn.innerText = 'Reintentar';
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
