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
            libVLCInstance ?: try {
                val options = ArrayList<String>().apply {
                    // Global caching buffers optimized for high stability and smooth decoding
                    add("--network-caching=2500")
                    add("--live-caching=2000")
                    add("--file-caching=3000")
                    add("--sout-mux-caching=2000")
                    add("--http-reconnect")
                    add("--http-continuous")
                    add("--rtsp-tcp")
                    // Prevents audio stuttering/choppiness when synchronizing frame clocks
                    add("--no-audio-time-stretch")
                    add("--avcodec-skiploopfilter=1")
                    add("--avcodec-threads=0")
                    add("--avcodec-fast")
                    add("--android-display-chroma=RV32")
                    add("--no-sub-autodetect-file")
                    add("--no-video-title-show")
                    add("--no-stats")
                    add("--no-osd")
                    add("--http-user-agent=Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 VLC/3.0.18 LibVLC/3.5.4")
                }
                LibVLC(context.applicationContext, options)
            } catch (e: Throwable) {
                Log.e("VlcHelper", "Failed to create LibVLC with custom options, attempting default fallback", e)
                try {
                    LibVLC(context.applicationContext, ArrayList<String>())
                } catch (e2: Throwable) {
                    Log.e("VlcHelper", "Critical: Failed to create LibVLC with empty options, using basic constructor", e2)
                    LibVLC(context.applicationContext)
                }
            }.also {
                libVLCInstance = it
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
            // Enable HW decoding with software fallback so all IPTV & VOD codecs play smoothly without lag or crash
            setHWDecoderEnabled(true, false)
            addOption(":no-ssl-verify")
            addOption(":http-no-ssl-verify")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
            if (isLiveStream) {
                // Stable Live TV buffer (2.0s buffer prevents stuttering and handles network jitter seamlessly)
                addOption(":network-caching=2000")
                addOption(":live-caching=2000")
                addOption(":http-reconnect")
                addOption(":http-continuous")
            } else {
                // Generous buffer for VOD (Movies & Series): 3.0s buffer prevents freezing / stuttering on high bitrate movies
                addOption(":network-caching=3000")
                addOption(":file-caching=3000")
                addOption(":http-reconnect")
                addOption(":http-continuous")
            }
            addOption(":no-sub-autodetect-file")
        }
    }
}



