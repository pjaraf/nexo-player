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
                    add("--no-stats")
                    add("--no-video-title-show")
                    add("--no-sub-autodetect-file")
                    add("--no-audio-time-stretch")
                    add("--avcodec-fast")
                    add("--avcodec-skiploopfilter=4") // Desactiva filtros pesados de deblocking para evitar desbordamiento en CPU de TV Box
                    add("--network-caching=1500")
                    add("--live-caching=1500")
                    add("--file-caching=1500")
                    add("--sout-mux-caching=1500")
                    add("--drop-late-frames")
                    add("--skip-frames")
                    add("--http-reconnect")
                    add("--no-ssl-verify")
                    add("--http-no-ssl-verify")
                    add("--tls-no-verify")
                    add("--gnutls-system-trust=0")
                    add("--codec=mediacodec_ndk,mediacodec_jni,all")
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
        return Media(libVLC, cleanUri).apply {
            try {
                // Enable hardware decoding with software fallback (force = false)
                setHWDecoderEnabled(true, false)
            } catch (_: Throwable) {}
            
            addOption(":no-ssl-verify")
            addOption(":http-no-ssl-verify")
            addOption(":tls-no-verify")
            addOption(":gnutls-system-trust=0")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
            addOption(":network-caching=1500")
            addOption(":live-caching=1500")
            addOption(":file-caching=1500")
            addOption(":sout-mux-caching=1500")
            addOption(":avcodec-fast")
            addOption(":avcodec-skiploopfilter=4")
            addOption(":http-reconnect")
            addOption(":rtsp-tcp")
            addOption(":no-sub-autodetect-file")
            addOption(":codec=mediacodec_ndk,mediacodec_jni,all")
        }
    }
}





