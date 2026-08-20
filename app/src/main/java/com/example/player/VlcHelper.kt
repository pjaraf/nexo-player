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
                val options = ArrayList<String>().apply {
                    add("--network-caching=3000")
                    add("--live-caching=1500")
                    add("--file-caching=3000")
                    add("--http-reconnect")
                    add("--no-drop-late-frames")
                    add("--no-skip-frames")
                    add("--audio-time-stretch")
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
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=3000")
            addOption(":http-reconnect")
        }
    }
}
