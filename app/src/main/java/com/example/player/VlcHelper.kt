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
                    // Optimized performance: fast channel startup & robust VOD seeking
                    add("--network-caching=500")
                    add("--live-caching=300")
                    add("--file-caching=1500")
                    add("--sout-mux-caching=500")
                    add("--http-reconnect")
                    add("--http-continuous")
                    add("--rtsp-tcp")
                    add("--avcodec-threads=$numCores")
                    add("--audio-time-stretch")
                    add("--no-sub-autodetect-file")
                    add("--no-video-title-show")
                    add("--no-stats")
                    add("--no-osd")
                    add("--avcodec-skiploopfilter=1")
                    add("--avcodec-fast")
                    add("--http-user-agent=Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 NexoPlayer/1.2")
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
            // Enable HW decoding with software fallback (force=false is crucial so all IPTV codecs play smoothly)
            setHWDecoderEnabled(true, false)
            if (isLiveStream) {
                // Ultra-low latency for Live TV channels: Instant channel zapping
                addOption(":network-caching=400")
                addOption(":live-caching=300")
                addOption(":http-reconnect")
            } else {
                // Generous buffer for VOD (Movies & Series): Seamless seeking and no buffering hiccups
                addOption(":network-caching=1800")
                addOption(":file-caching=1800")
                addOption(":http-reconnect")
                addOption(":http-continuous")
            }
            addOption(":no-sub-autodetect-file")
            addOption(":avcodec-skiploopfilter=1")
            addOption(":avcodec-fast")
        }
    }
}


