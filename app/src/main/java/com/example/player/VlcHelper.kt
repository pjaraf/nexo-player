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
                    // Desactiva el decodificador de hardware que causa la pantalla negra
                    add("--no-mediacodec-all")
                    add("--no-mediacodec-dr")
                    add("--no-omxil-dr")
                    add("--avcodec-hw=none") // Forzar decodificación por CPU/software
                    
                    // Forzar renderizado directo a Surface
                    add("--vout=android_display,none")
                    add("--network-caching=2000")
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
        val isLiveStream = url.contains("/live/") || url.endsWith(".m3u8") || url.contains(".m3u8?") || url.endsWith(".ts") || url.contains(".ts?") || url.contains("m3u8")
        return Media(libVLC, cleanUri).apply {
            // Regla de oro para Android TV: forzar decodificación por software (false, false) para evitar cierres en HEVC/H.265
            try {
                setHWDecoderEnabled(false, false)
            } catch (_: Throwable) {}
            
            addOption(":no-ssl-verify")
            addOption(":http-no-ssl-verify")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
            addOption(":network-caching=2000")
            addOption(":live-caching=2000")
            addOption(":file-caching=2000")
            addOption(":sout-mux-caching=2000")
            addOption(":http-reconnect")
            addOption(":http-continuous")
            addOption(":rtsp-tcp")
            addOption(":no-sub-autodetect-file")
        }
    }
}



