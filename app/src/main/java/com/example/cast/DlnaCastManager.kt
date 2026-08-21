package com.example.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

data class CastDevice(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val locationUrl: String,
    val controlUrl: String,
    val isRoku: Boolean = false,
    val ipAddress: String = ""
)

sealed class CastPlaybackState {
    object Idle : CastPlaybackState()
    data class Connecting(val device: CastDevice) : CastPlaybackState()
    data class Playing(val device: CastDevice, val title: String, val url: String) : CastPlaybackState()
    data class Paused(val device: CastDevice, val title: String, val url: String) : CastPlaybackState()
    data class Error(val message: String) : CastPlaybackState()
}

object DlnaCastManager {
    private const val TAG = "DlnaCastManager"
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _playbackState = MutableStateFlow<CastPlaybackState>(CastPlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private var currentActiveDevice: CastDevice? = null
    private var currentMediaTitle: String = ""
    private var currentMediaUrl: String = ""

    private var searchJob: Job? = null

    fun startDiscovery(context: Context) {
        if (_isSearching.value) return
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            _isSearching.value = true
            val foundMap = mutableMapOf<String, CastDevice>()

            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("NexoDlnaLock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
            }

            try {
                // Send SSDP M-SEARCH requests
                val queries = listOf(
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "ssdp:all",
                    "roku:ecp"
                )

                DatagramSocket().use { socket ->
                    socket.soTimeout = 2000
                    socket.broadcast = true

                    for (target in queries) {
                        val mSearch = "M-SEARCH * HTTP/1.1\r\n" +
                                "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                                "MAN: \"ssdp:discover\"\r\n" +
                                "MX: 3\r\n" +
                                "ST: $target\r\n\r\n"
                        val data = mSearch.toByteArray()
                        val packet = DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName(SSDP_ADDRESS),
                            SSDP_PORT
                        )
                        socket.send(packet)
                    }

                    val buffer = ByteArray(4096)
                    val endTime = System.currentTimeMillis() + 4500L
                    while (System.currentTimeMillis() < endTime && isActive) {
                        try {
                            val receivePacket = DatagramPacket(buffer, buffer.size)
                            socket.receive(receivePacket)
                            val response = String(receivePacket.data, 0, receivePacket.length)
                            val location = extractHeader(response, "LOCATION") ?: extractHeader(response, "Location")

                            if (!location.isNullOrBlank() && !foundMap.containsKey(location)) {
                                val ip = receivePacket.address.hostAddress ?: ""
                                launch(Dispatchers.IO) {
                                    val dev = fetchDeviceDetails(location, ip)
                                    if (dev != null && !foundMap.containsKey(dev.id)) {
                                        foundMap[dev.id] = dev
                                        _discoveredDevices.value = foundMap.values.toList()
                                    }
                                }
                            }
                        } catch (_: java.net.SocketTimeoutException) {
                            // Loop until time expired
                        } catch (e: Exception) {
                            Log.e(TAG, "Socket receive error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error: ${e.message}")
            } finally {
                try {
                    multicastLock?.release()
                } catch (_: Exception) {}
                _isSearching.value = false
            }
        }
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val regex = Regex("(?i)$headerName:\\s*(.*)")
        return regex.find(response)?.groupValues?.get(1)?.trim()
    }

    private suspend fun fetchDeviceDetails(locationUrl: String, ip: String): CastDevice? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(locationUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val xml = response.body?.string() ?: return@withContext null

            var friendlyName = ""
            var manufacturer = ""
            var modelName = ""
            var udn = ""
            var avTransportControlUrl = ""
            var isMediaRenderer = false
            var isRoku = locationUrl.contains(":8060") || xml.contains("<device-type>roku")

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""
            var inService = false
            var currentServiceType = ""
            var currentControlUrl = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag.equals("service", ignoreCase = true)) {
                            inService = true
                            currentServiceType = ""
                            currentControlUrl = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            if (inService) {
                                when {
                                    currentTag.equals("serviceType", ignoreCase = true) -> currentServiceType = text
                                    currentTag.equals("controlURL", ignoreCase = true) -> currentControlUrl = text
                                }
                            } else {
                                when {
                                    currentTag.equals("friendlyName", ignoreCase = true) && friendlyName.isEmpty() -> friendlyName = text
                                    currentTag.equals("manufacturer", ignoreCase = true) && manufacturer.isEmpty() -> manufacturer = text
                                    currentTag.equals("modelName", ignoreCase = true) && modelName.isEmpty() -> modelName = text
                                    currentTag.equals("UDN", ignoreCase = true) && udn.isEmpty() -> udn = text
                                    currentTag.equals("deviceType", ignoreCase = true) && text.contains("MediaRenderer", ignoreCase = true) -> isMediaRenderer = true
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("service", ignoreCase = true)) {
                            if (currentServiceType.contains("AVTransport", ignoreCase = true)) {
                                avTransportControlUrl = currentControlUrl
                                isMediaRenderer = true
                            }
                            inService = false
                        }
                    }
                }
                eventType = parser.next()
            }

            if (isMediaRenderer || isRoku || avTransportControlUrl.isNotEmpty() || friendlyName.contains("TV", ignoreCase = true)) {
                val baseUri = java.net.URI(locationUrl)
                val fullControlUrl = if (avTransportControlUrl.startsWith("http")) {
                    avTransportControlUrl
                } else if (avTransportControlUrl.isNotEmpty()) {
                    baseUri.resolve(avTransportControlUrl).toString()
                } else {
                    locationUrl
                }

                val displayName = friendlyName.ifBlank {
                    if (isRoku) "Roku TV ($ip)" else "Smart TV ($ip)"
                }

                val cleanUdn = udn.ifBlank { "$ip-$displayName" }

                return@withContext CastDevice(
                    id = cleanUdn,
                    name = displayName,
                    manufacturer = manufacturer,
                    model = modelName,
                    locationUrl = locationUrl,
                    controlUrl = fullControlUrl,
                    isRoku = isRoku,
                    ipAddress = ip
                )
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cast only the video stream URL directly to the chosen Smart TV.
     */
    fun castToDevice(device: CastDevice, videoUrl: String, title: String, context: Context, onResult: (Boolean, String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            _playbackState.value = CastPlaybackState.Connecting(device)
            currentActiveDevice = device
            currentMediaTitle = title
            currentMediaUrl = videoUrl

            if (device.isRoku) {
                // Roku External Control Protocol (ECP) launch video
                val success = launchRokuVideo(device, videoUrl)
                withContext(Dispatchers.Main) {
                    if (success) {
                        _playbackState.value = CastPlaybackState.Playing(device, title, videoUrl)
                        onResult(true, null)
                    } else {
                        _playbackState.value = CastPlaybackState.Error("No se pudo iniciar reproducción en Roku TV")
                        onResult(false, "No se pudo conectar con Roku TV")
                    }
                }
                return@launch
            }

            // DLNA / UPnP AVTransport command
            val setUriSuccess = sendDlnaSetAvTransportUri(device.controlUrl, videoUrl, title)
            if (setUriSuccess) {
                val playSuccess = sendDlnaPlay(device.controlUrl)
                withContext(Dispatchers.Main) {
                    if (playSuccess) {
                        _playbackState.value = CastPlaybackState.Playing(device, title, videoUrl)
                        onResult(true, null)
                    } else {
                        _playbackState.value = CastPlaybackState.Error("Error al iniciar el comando Play en el televisor")
                        onResult(false, "El televisor recibió el video pero no inició la reproducción automática")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _playbackState.value = CastPlaybackState.Error("No se pudo transmitir el video al televisor")
                    onResult(false, "No se pudo comunicar con el reproductor del televisor (DLNA)")
                }
            }
        }
    }

    private fun launchRokuVideo(device: CastDevice, videoUrl: String): Boolean {
        return try {
            val encodedUrl = java.net.URLEncoder.encode(videoUrl, "UTF-8")
            val rokuUrl = "http://${device.ipAddress}:8060/input/15985?u=$encodedUrl&videoFormat=auto"
            val request = Request.Builder()
                .url(rokuUrl)
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Roku cast error: ${e.message}")
            false
        }
    }

    private fun sendDlnaSetAvTransportUri(controlUrl: String, videoUrl: String, title: String): Boolean {
        val xmlBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>${escapeXml(videoUrl)}</CurrentURI>
                        <CurrentURIMetaData>&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;&lt;item id="0" parentID="-1" restricted="1"&gt;&lt;dc:title&gt;${escapeXml(title)}&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;res&gt;${escapeXml(videoUrl)}&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>
                    </u:SetAVTransportURI>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"")
            .post(xmlBody.toRequestBody(mediaType))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "SetAVTransportURI error: ${e.message}")
            false
        }
    }

    private fun sendDlnaPlay(controlUrl: String): Boolean {
        val xmlBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <Speed>1</Speed>
                    </u:Play>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"")
            .post(xmlBody.toRequestBody(mediaType))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Play error: ${e.message}")
            false
        }
    }

    fun pause() {
        val device = currentActiveDevice ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val xmlBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                        </u:Pause>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()

            val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
            val request = Request.Builder()
                .url(device.controlUrl)
                .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#Pause\"")
                .post(xmlBody.toRequestBody(mediaType))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    _playbackState.value = CastPlaybackState.Paused(device, currentMediaTitle, currentMediaUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pause error: ${e.message}")
            }
        }
    }

    fun resume() {
        val device = currentActiveDevice ?: return
        CoroutineScope(Dispatchers.IO).launch {
            if (sendDlnaPlay(device.controlUrl)) {
                _playbackState.value = CastPlaybackState.Playing(device, currentMediaTitle, currentMediaUrl)
            }
        }
    }

    fun stop() {
        val device = currentActiveDevice ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val xmlBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                        </u:Stop>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()

            val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
            val request = Request.Builder()
                .url(device.controlUrl)
                .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#Stop\"")
                .post(xmlBody.toRequestBody(mediaType))
                .build()

            try {
                httpClient.newCall(request).execute()
            } catch (_: Exception) {}
            _playbackState.value = CastPlaybackState.Idle
            currentActiveDevice = null
        }
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
