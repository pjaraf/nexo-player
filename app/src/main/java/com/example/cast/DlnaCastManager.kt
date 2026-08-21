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
import java.net.Socket
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
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
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
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            _isSearching.value = true
            val foundMap = mutableMapOf<String, CastDevice>()

            // Keep any already known active device in map
            _discoveredDevices.value.forEach { foundMap[it.id] = it }

            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("NexoDlnaLock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
            }

            // 1. SSDP Multicast Search
            val ssdpJob = launch {
                try {
                    val queries = listOf(
                        "urn:schemas-upnp-org:device:MediaRenderer:1",
                        "urn:schemas-upnp-org:service:AVTransport:1",
                        "ssdp:all",
                        "roku:ecp",
                        "upnp:rootdevice"
                    )

                    DatagramSocket().use { socket ->
                        socket.soTimeout = 1500
                        socket.broadcast = true

                        for (target in queries) {
                            val mSearch = "M-SEARCH * HTTP/1.1\r\n" +
                                    "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                                    "MAN: \"ssdp:discover\"\r\n" +
                                    "MX: 2\r\n" +
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
                        val endTime = System.currentTimeMillis() + 4000L
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
                                // Continue until loop time ends
                            } catch (e: Throwable) {
                                Log.e(TAG, "Socket receive error: ${e.message}")
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "SSDP Discovery error: ${e.message}")
                }
            }

            // 2. Fast Subnet Port Probe (Fixes routers where UDP SSDP Multicast is blocked)
            val subnetJob = launch {
                try {
                    val localIp = getLocalWifiIpAddress(context)
                    if (localIp.isNotBlank() && localIp.contains(".")) {
                        val subnet = localIp.substringBeforeLast(".") + "."
                        val hostNum = localIp.substringAfterLast(".").toIntOrNull() ?: -1

                        // Scan candidate local IP addresses in parallel
                        val candidates = (1..254).filter { it != hostNum }
                        candidates.chunked(32).forEach { batch ->
                            if (!isActive) return@forEach
                            val batchJobs = batch.map { i ->
                                launch(Dispatchers.IO) {
                                    val ip = "$subnet$i"
                                    probeDirectIpInternal(ip, foundMap)
                                }
                            }
                            batchJobs.joinAll()
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Subnet probe error: ${e.message}")
                }
            }

            joinAll(ssdpJob, subnetJob)

            try {
                multicastLock?.release()
            } catch (_: Throwable) {}
            _isSearching.value = false
        }
    }

    private suspend fun probeDirectIpInternal(ip: String, foundMap: MutableMap<String, CastDevice>) {
        // Probe Roku (Port 8060)
        try {
            val req = Request.Builder().url("http://$ip:8060/query/device-info").build()
            val res = httpClient.newCall(req).execute()
            if (res.isSuccessful) {
                val xml = res.body?.string() ?: ""
                val modelName = extractXmlTag(xml, "model-name") ?: "Roku TV"
                val friendlyName = extractXmlTag(xml, "user-device-name") ?: extractXmlTag(xml, "friendly-device-name") ?: modelName
                val serial = extractXmlTag(xml, "serial-number") ?: ip
                val dev = CastDevice(
                    id = "roku-$serial",
                    name = "$friendlyName ($ip)",
                    manufacturer = "Roku",
                    model = modelName,
                    locationUrl = "http://$ip:8060/",
                    controlUrl = "http://$ip:8060/",
                    isRoku = true,
                    ipAddress = ip
                )
                if (!foundMap.containsKey(dev.id)) {
                    foundMap[dev.id] = dev
                    _discoveredDevices.value = foundMap.values.toList()
                    return
                }
            }
        } catch (_: Throwable) {}

        // Probe common DLNA / TV ports
        val commonUrls = listOf(
            "http://$ip:7676/smp_2_",
            "http://$ip:8080/description.xml",
            "http://$ip:9000/description.xml",
            "http://$ip:8008/ssdp/device-desc.xml",
            "http://$ip:1925/1/system"
        )

        for (url in commonUrls) {
            try {
                val dev = fetchDeviceDetails(url, ip)
                if (dev != null && !foundMap.containsKey(dev.id)) {
                    foundMap[dev.id] = dev
                    _discoveredDevices.value = foundMap.values.toList()
                    return
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Allows manual IP connection if router completely blocks broadcast discovery.
     */
    suspend fun addDirectIpDevice(ip: String): CastDevice? = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
        if (cleanIp.isBlank()) return@withContext null

        val foundMap = _discoveredDevices.value.associateBy { it.id }.toMutableMap()
        probeDirectIpInternal(cleanIp, foundMap)

        var found = foundMap.values.firstOrNull { it.ipAddress == cleanIp }
        if (found == null) {
            // Fallback: Add generic DLNA / Roku endpoint for this IP
            found = CastDevice(
                id = "manual-$cleanIp",
                name = "Smart TV / Receptor ($cleanIp)",
                manufacturer = "Smart TV",
                model = "DLNA / UPnP Direct",
                locationUrl = "http://$cleanIp:8060/",
                controlUrl = "http://$cleanIp:8060/",
                isRoku = true,
                ipAddress = cleanIp
            )
            foundMap[found.id] = found
            _discoveredDevices.value = foundMap.values.toList()
        }
        return@withContext found
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val pattern = "<$tag[^>]*>(.*?)</$tag>".toRegex(RegexOption.IGNORE_CASE)
        return pattern.find(xml)?.groupValues?.get(1)?.trim()
    }

    private fun getLocalWifiIpAddress(context: Context): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wm?.connectionInfo?.ipAddress ?: 0
            if (ipInt == 0) return ""
            String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (_: Throwable) {
            ""
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
            var isRoku = locationUrl.contains(":8060") || xml.contains("<device-type>roku", ignoreCase = true)

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

            if (isMediaRenderer || isRoku || avTransportControlUrl.isNotEmpty() || friendlyName.contains("TV", ignoreCase = true) || friendlyName.contains("Chromecast", ignoreCase = true)) {
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
        } catch (_: Throwable) {
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
                        // Try fallback via DLNA
                        val setUriSuccess = sendDlnaSetAvTransportUri(device.controlUrl, videoUrl, title)
                        if (setUriSuccess) {
                            sendDlnaPlay(device.controlUrl)
                            _playbackState.value = CastPlaybackState.Playing(device, title, videoUrl)
                            onResult(true, null)
                        } else {
                            _playbackState.value = CastPlaybackState.Error("No se pudo iniciar reproducción en Roku TV")
                            onResult(false, "No se pudo conectar con Roku TV. Prueba con el menú 'Transmitir con Web Video Cast / VLC'.")
                        }
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
                        _playbackState.value = CastPlaybackState.Playing(device, title, videoUrl)
                        onResult(true, null)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _playbackState.value = CastPlaybackState.Error("No se pudo transmitir el video al televisor")
                    onResult(false, "El televisor no aceptó la transmisión directa. Usa la opción 'Transmitir con Web Video Cast / VLC'.")
                }
            }
        }
    }

    private fun launchRokuVideo(device: CastDevice, videoUrl: String): Boolean {
        return try {
            val encodedUrl = java.net.URLEncoder.encode(videoUrl, "UTF-8")
            val rokuUrls = listOf(
                "http://${device.ipAddress}:8060/input/15985?u=$encodedUrl&videoFormat=auto",
                "http://${device.ipAddress}:8060/launch/15985?u=$encodedUrl&videoFormat=auto",
                "http://${device.ipAddress}:8060/launch/2213?u=$encodedUrl"
            )
            for (rokuUrl in rokuUrls) {
                try {
                    val request = Request.Builder()
                        .url(rokuUrl)
                        .post("".toRequestBody("text/plain".toMediaType()))
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) return true
                } catch (_: Throwable) {}
            }
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Roku cast error: ${e.message}")
            false
        }
    }

    private fun sendDlnaSetAvTransportUri(controlUrl: String, videoUrl: String, title: String): Boolean {
        val mimeType = when {
            videoUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            videoUrl.contains(".ts", ignoreCase = true) -> "video/mp2t"
            videoUrl.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
            else -> "video/mp4"
        }

        val xmlBodyStandard = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>${escapeXml(videoUrl)}</CurrentURI>
                        <CurrentURIMetaData>&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;&lt;item id="0" parentID="-1" restricted="1"&gt;&lt;dc:title&gt;${escapeXml(title)}&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;res protocolInfo="http-get:*:$mimeType:*"&gt;${escapeXml(videoUrl)}&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>
                    </u:SetAVTransportURI>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"")
            .post(xmlBodyStandard.toRequestBody(mediaType))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) return true
        } catch (_: Throwable) {}

        // Fallback: simplified SetAVTransportURI without complex metadata (fixes LG & Samsung DLNA errors)
        val xmlBodySimple = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>${escapeXml(videoUrl)}</CurrentURI>
                        <CurrentURIMetaData></CurrentURIMetaData>
                    </u:SetAVTransportURI>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val simpleRequest = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"")
            .post(xmlBodySimple.toRequestBody(mediaType))
            .build()

        return try {
            val response = httpClient.newCall(simpleRequest).execute()
            response.isSuccessful
        } catch (_: Throwable) {
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
        } catch (e: Throwable) {
            Log.e(TAG, "Play error: ${e.message}")
            false
        }
    }

    fun pause() {
        val device = currentActiveDevice ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val xmlBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/envelope/">
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
            } catch (e: Throwable) {
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
            } catch (_: Throwable) {}
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
