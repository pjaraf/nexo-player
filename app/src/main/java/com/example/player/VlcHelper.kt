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
                    add("--avcodec-hw=any")         // Hardware nativo rápido
                    add("--network-caching=500")     // Carga instantánea (500ms en lugar de 2000ms)
                    add("--live-caching=500")
                    add("--drop-late-frames")        // Si hay lag, salta frames sin congelar
                    add("--skip-frames")
                    add("--no-stats")
                    add("--no-video-title-show")
                    add("--no-sub-autodetect-file")
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
        val lowerUrl = url.lowercase()
        val isHevcOrHeavy = lowerUrl.contains("hevc") || lowerUrl.contains("h265") || lowerUrl.contains("4k") || lowerUrl.contains("hevc/h265") || lowerUrl.contains("live") || lowerUrl.contains(".m3u8") || lowerUrl.contains(".ts")
        return Media(libVLC, cleanUri).apply {
            try {
                if (isHevcOrHeavy) {
                    setHWDecoderEnabled(false, false) // 100% Software para streams en vivo / HLS / TS para evitar crashes en TV Box
                } else {
                    setHWDecoderEnabled(true, false)
                }
            } catch (_: Throwable) {}
            
            addOption(":no-ssl-verify")
            addOption(":http-no-ssl-verify")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
            addOption(":network-caching=500")
            addOption(":live-caching=500")
            addOption(":file-caching=500")
            addOption(":sout-mux-caching=500")
            addOption(":drop-late-frames")
            addOption(":skip-frames")
            addOption(":http-reconnect")
            addOption(":http-continuous")
            addOption(":rtsp-tcp")
            addOption(":no-sub-autodetect-file")
        }
    }
}



