package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

object VlcHelper {
    private var libVLCInstance: LibVLC? = null

    fun getLibVLC(context: Context): LibVLC {
        return libVLCInstance ?: synchronized(this) {
            libVLCInstance ?: run {
                val numCores = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                val options = ArrayList<String>().apply {
                    // Maximum stability and compatibility options for Android TV and low-end chipsets
                    add("--network-caching=3000")
                    add("--live-caching=2000")
                    add("--file-caching=3000")
                    add("--http-reconnect")
                    add("--drop-late-frames")
                    add("--skip-frames")
                    add("--avcodec-threads=$numCores")
                    add("--audio-time-stretch")
                    add("--aout=android_audiotrack")
                    add("--vout=android_display,none")
                    add("--codec=mediacodec_jni,all")
                    add("--no-sub-autodetect-file")
                    add("--no-video-title-show")
                    add("--no-stats")
                    add("--http-user-agent=Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 VLC/3.0.18 LibVLC/3.0.18")
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
        return Media(libVLC, cleanUri).apply {
            // Enable HW decoding WITH software fallback (force=false is crucial to prevent TV crashes on unsupported channel codecs)
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=3000")
            addOption(":live-caching=2000")
            addOption(":http-reconnect")
            addOption(":sout-mux-caching=2000")
            addOption(":codec=mediacodec_jni,all")
            addOption(":no-sub-autodetect-file")
        }
    }
}

