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
                val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
                val options = ArrayList<String>().apply {
                    // Maximum performance, low-latency live TV playback and jitter prevention
                    add("--network-caching=800")
                    add("--live-caching=600")
                    add("--file-caching=1000")
                    add("--sout-mux-caching=800")
                    add("--http-reconnect")
                    add("--http-continuous")
                    add("--drop-late-frames")
                    add("--skip-frames")
                    add("--avcodec-threads=$numCores")
                    add("--audio-time-stretch")
                    add("--clock-jitter=0")
                    add("--clock-synchro=0")
                    add("--no-sub-autodetect-file")
                    add("--no-video-title-show")
                    add("--no-stats")
                    add("--no-osd")
                    add("--avcodec-skiploopfilter=1")
                    add("--avcodec-fast")
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
            addOption(":network-caching=800")
            addOption(":live-caching=600")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
            addOption(":http-reconnect")
            addOption(":no-sub-autodetect-file")
            addOption(":avcodec-skiploopfilter=1")
            addOption(":avcodec-fast")
        }
    }
}

