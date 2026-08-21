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
                    // Optimized buffer & stability options for low-RAM and older chipsets
                    add("--network-caching=2000")
                    add("--live-caching=1500")
                    add("--file-caching=2000")
                    add("--http-reconnect")
                    add("--drop-late-frames")
                    add("--skip-frames")
                    add("--avcodec-fast")
                    add("--avcodec-threads=$numCores")
                    add("--avcodec-skiploopfilter=4") // Skip non-ref deblocking for smooth 60fps on slow CPUs
                    add("--audio-time-stretch")
                    add("--aout=android_audiotrack")
                    add("--vout=android_display,none")
                    add("--no-sub-autodetect-file")
                    add("--no-stats") // Disable unnecessary logging/stats overhead
                    add("--http-user-agent=Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 VLC/3.0.18 LibVLC/3.0.18")
                }
                LibVLC(context.applicationContext, options).also {
                    libVLCInstance = it
                }
            }
        }
    }

    fun createMedia(libVLC: LibVLC, url: String): Media {
        return Media(libVLC, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, true) // Enable HW decoding WITH software fallback to prevent crashes on TV
            addOption(":network-caching=2000")
            addOption(":live-caching=1500")
            addOption(":http-reconnect")
            addOption(":sout-mux-caching=1500")
            addOption(":codec=mediacodec_ndk,mediacodec_jni,iomx,all")
            addOption(":avcodec-skiploopfilter=4")
            addOption(":avcodec-fast")
        }
    }
}
