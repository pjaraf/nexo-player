package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media

object VlcHelper {
    private var libVLCInstance: LibVLC? = null

    fun getLibVLC(context: Context): LibVLC {
        return libVLCInstance ?: synchronized(this) {
            libVLCInstance ?: run {
                val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
                val options = ArrayList<String>().apply {
                    // Global caching buffers optimized for high stability and smooth decoding
                    add("--network-caching=2500")
                    add("--live-caching=2000")
                    add("--file-caching=3000")
                    add("--sout-mux-caching=2000")
                    add("--http-reconnect")
                    add("--http-continuous")
                    add("--rtsp-tcp")
                    add("--clock-jitter=0")
                    add("--clock-synchro=0")
                    add("--avcodec-threads=$numCores")
                    add("--audio-time-stretch")
                    add("--no-sub-autodetect-file")
                    add("--no-video-title-show")
                    add("--no-stats")
                    add("--no-osd")
                    add("--drop-late-frames")
                    add("--skip-frames")
                    add("--http-user-agent=Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 VLC/3.0.18 LibVLC/3.5.4")
                }
                LibVLC(context.applicationContext, options).also {
                    libVLCInstance = it
                }
            }
        }
    }

    fun createMedia(libVLC: LibVLC, url: String): Media {
        val cleanUri = try {
            Uri.parse(url.trim())
        } catch (_: Exception) {
            Uri.EMPTY
        }
        val isLiveStream = url.contains("/live/") || url.endsWith(".m3u8") || url.contains(".m3u8?") || url.endsWith(".ts") || url.contains(".ts?")
        return Media(libVLC, cleanUri).apply {
            // Enable HW decoding with software fallback so all IPTV & VOD codecs play smoothly without lag
            setHWDecoderEnabled(true, false)
            if (isLiveStream) {
                // Stable Live TV buffer (2.0s buffer prevents stuttering and handles network jitter seamlessly)
                addOption(":network-caching=2000")
                addOption(":live-caching=2000")
                addOption(":http-reconnect")
                addOption(":http-continuous")
                addOption(":clock-jitter=0")
            } else {
                // Generous buffer for VOD (Movies & Series): 4.5s buffer prevents freezing / stuttering on high bitrate movies
                addOption(":network-caching=4500")
                addOption(":file-caching=4500")
                addOption(":http-reconnect")
                addOption(":http-continuous")
            }
            addOption(":no-sub-autodetect-file")
        }
    }
}


