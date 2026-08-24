package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

data class VlcTrackInfo(
    val id: Int,
    val name: String,
    val isSelected: Boolean
)

/**
 * Gestor exclusivo y unificado de reproducción basado 100% en VLC (LibVLC).
 * Optimizado para Android TV y dispositivos móviles con decodificación por hardware.
 */
class PlayerManager(val context: Context) {

    private val libVLC: LibVLC = VlcHelper.getLibVLC(context)
    val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)
    private var attachedLayout: VLCVideoLayout? = null

    var onBuffering: ((Boolean, Float) -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var onTimeChanged: ((Long) -> Unit)? = null
    var onLengthChanged: ((Long) -> Unit)? = null
    var onEndReached: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onTracksChanged: (() -> Unit)? = null

    private var currentUrl: String? = null
    private var currentMedia: Media? = null

    private var targetAspectRatio: String? = null
    private var targetScale: Float? = null

    private var pendingStartPositionMs: Long = 0L
    private var lastSeekTimestamp: Long = 0L
    private var isSeekingInProgress: Boolean = false

    val isPlaying: Boolean
        get() = try {
            mediaPlayer.isPlaying
        } catch (_: Throwable) {
            false
        }

    val time: Long
        get() = try {
            mediaPlayer.time.coerceAtLeast(0L)
        } catch (_: Throwable) {
            0L
        }

    val length: Long
        get() = try {
            mediaPlayer.length.coerceAtLeast(0L)
        } catch (_: Throwable) {
            0L
        }

    init {
        mediaPlayer.setEventListener { event ->
            try {
                when (event.type) {
                    MediaPlayer.Event.Buffering -> {
                        val percent = event.buffering
                        try { onBuffering?.invoke(percent < 100.0f, percent) } catch (_: Throwable) {}
                    }
                    MediaPlayer.Event.Playing -> {
                        try {
                            targetAspectRatio?.let { mediaPlayer.aspectRatio = it }
                            targetScale?.let { mediaPlayer.scale = it }
                        } catch (_: Throwable) {}

                        // Safely apply pending resume start position now that player is actually decoding frames
                        if (pendingStartPositionMs > 0L) {
                            val startPos = pendingStartPositionMs
                            pendingStartPositionMs = 0L
                            try {
                                mediaPlayer.time = startPos
                            } catch (e: Throwable) {
                                Log.w("PlayerManager", "Delayed seek to $startPos failed: ${e.message}")
                            }
                        }

                        isSeekingInProgress = false
                        try { onBuffering?.invoke(false, 100f) } catch (_: Throwable) {}
                        try { onPlayingChanged?.invoke(true) } catch (_: Throwable) {}
                        try { onTracksChanged?.invoke() } catch (_: Throwable) {}
                    }
                    MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                        try { onPlayingChanged?.invoke(false) } catch (_: Throwable) {}
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        val currentTimeMs = event.timeChanged
                        // If there is still a pending start position and we get first time event, apply it
                        if (pendingStartPositionMs > 0L) {
                            val startPos = pendingStartPositionMs
                            pendingStartPositionMs = 0L
                            try { mediaPlayer.time = startPos } catch (_: Throwable) {}
                        }
                        try { onTimeChanged?.invoke(currentTimeMs) } catch (_: Throwable) {}
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        val currentLenMs = event.lengthChanged
                        if (pendingStartPositionMs > 0L && currentLenMs > 0L) {
                            val startPos = pendingStartPositionMs
                            pendingStartPositionMs = 0L
                            try { mediaPlayer.time = startPos } catch (_: Throwable) {}
                        }
                        try { onLengthChanged?.invoke(currentLenMs) } catch (_: Throwable) {}
                    }
                    MediaPlayer.Event.EndReached -> {
                        val now = System.currentTimeMillis()
                        val isRecentSeek = (now - lastSeekTimestamp) < 3000L || isSeekingInProgress
                        val currentPos = try { mediaPlayer.time } catch (_: Throwable) { 0L }
                        val totalLen = try { mediaPlayer.length } catch (_: Throwable) { 0L }
                        val isTrueEnd = totalLen > 0L && currentPos >= (totalLen - 5000L)

                        if (isRecentSeek || !isTrueEnd) {
                            Log.d("PlayerManager", "Ignored premature EndReached during seek/buffering (pos=$currentPos, len=$totalLen)")
                            try {
                                mediaPlayer.play()
                            } catch (_: Throwable) {}
                        } else {
                            try { onEndReached?.invoke() } catch (_: Throwable) {}
                        }
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        val now = System.currentTimeMillis()
                        val isRecentSeek = (now - lastSeekTimestamp) < 2500L
                        if (isRecentSeek) {
                            Log.w("PlayerManager", "Transient VLC error during seek ignored, attempting auto-resume...")
                            try {
                                mediaPlayer.play()
                            } catch (_: Throwable) {}
                        } else {
                            Log.e("PlayerManager", "VLC Error de reproducción en: $currentUrl")
                            try { onBuffering?.invoke(false, 0f) } catch (_: Throwable) {}
                            try { onError?.invoke("Error de reproducción en VLC") } catch (_: Throwable) {}
                        }
                    }
                    MediaPlayer.Event.Vout -> {
                        try {
                            targetAspectRatio?.let { mediaPlayer.aspectRatio = it }
                            targetScale?.let { mediaPlayer.scale = it }
                        } catch (_: Throwable) {}
                        try { onTracksChanged?.invoke() } catch (_: Throwable) {}
                    }
                }
            } catch (t: Throwable) {
                Log.w("PlayerManager", "Safe handling of VLC event exception: ${t.message}")
            }
        }
    }

    fun attachViews(layout: VLCVideoLayout, enableSubtitles: Boolean = true, useTextureView: Boolean = false) {
        try {
            if (attachedLayout != layout) {
                detachViews()
                attachedLayout = layout
                mediaPlayer.attachViews(layout, null, enableSubtitles, useTextureView)
                try {
                    targetAspectRatio?.let { mediaPlayer.aspectRatio = it }
                    targetScale?.let { mediaPlayer.scale = it }
                } catch (_: Throwable) {}
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error attaching VLC views", e)
        }
    }

    fun detachViews() {
        try {
            if (attachedLayout != null) {
                mediaPlayer.detachViews()
                attachedLayout = null
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error detaching VLC views", e)
        }
    }

    @Synchronized
    fun play(url: String, startPositionMs: Long = 0L) {
        if (url.isBlank()) {
            onError?.invoke("URL de transmisión inválida")
            return
        }
        try {
            currentUrl = url
            pendingStartPositionMs = startPositionMs.coerceAtLeast(0L)
            lastSeekTimestamp = 0L
            isSeekingInProgress = false
            Log.d("PlayerManager", "VLC Reproduciendo: $url (startPos=$startPositionMs)")

            // Cleanly stop prior playback before loading new media
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
            } catch (e: Throwable) {
                Log.w("PlayerManager", "Warning stopping prior playback: ${e.message}")
            }

            // Safely detach old media reference from player
            try {
                mediaPlayer.media = null
            } catch (_: Throwable) {}

            // Release previous media reference
            try {
                currentMedia?.release()
                currentMedia = null
            } catch (e: Throwable) {
                Log.w("PlayerManager", "Warning releasing old media: ${e.message}")
            }

            val newMedia = VlcHelper.createMedia(libVLC, url)
            currentMedia = newMedia
            mediaPlayer.media = newMedia

            try {
                targetAspectRatio?.let { mediaPlayer.aspectRatio = it }
                targetScale?.let { mediaPlayer.scale = it }
            } catch (_: Throwable) {}

            mediaPlayer.play()
        } catch (e: Throwable) {
            Log.e("PlayerManager", "Error al reproducir con VLC: $url", e)
            onError?.invoke(e.localizedMessage ?: "Error al reproducir")
        }
    }

    @Synchronized
    fun pause() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al pausar VLC", e)
        }
    }

    @Synchronized
    fun resume() {
        try {
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.play()
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al reanudar VLC", e)
        }
    }

    @Synchronized
    fun stop() {
        try {
            pendingStartPositionMs = 0L
            mediaPlayer.stop()
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al detener VLC", e)
        }
    }

    @Synchronized
    fun seekTo(timeMs: Long) {
        try {
            val totalLen = length
            val safeTarget = if (totalLen > 0L) {
                timeMs.coerceIn(0L, (totalLen - 2000L).coerceAtLeast(0L))
            } else {
                timeMs.coerceAtLeast(0L)
            }
            lastSeekTimestamp = System.currentTimeMillis()
            isSeekingInProgress = true
            mediaPlayer.time = safeTarget
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.play()
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al hacer seek en VLC", e)
        }
    }

    fun setAspectRatio(ratio: String?) {
        try {
            targetAspectRatio = ratio
            mediaPlayer.aspectRatio = ratio
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error setting aspect ratio: $ratio", e)
        }
    }

    fun setScale(scale: Float) {
        try {
            targetScale = scale
            mediaPlayer.scale = scale
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error setting scale: $scale", e)
        }
    }

    fun getAudioTracks(): List<VlcTrackInfo> {
        val tracks = mediaPlayer.audioTracks ?: return emptyList()
        val currentId = mediaPlayer.audioTrack
        return tracks.map { track ->
            VlcTrackInfo(
                id = track.id,
                name = track.name ?: "Pista #${track.id}",
                isSelected = track.id == currentId
            )
        }
    }

    fun setAudioTrack(trackId: Int) {
        try {
            mediaPlayer.audioTrack = trackId
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error setting audio track $trackId", e)
        }
    }

    fun getSubtitleTracks(): List<VlcTrackInfo> {
        val tracks = mediaPlayer.spuTracks ?: return emptyList()
        val currentId = mediaPlayer.spuTrack
        return tracks.map { track ->
            VlcTrackInfo(
                id = track.id,
                name = track.name ?: "Subtítulo #${track.id}",
                isSelected = track.id == currentId
            )
        }
    }

    fun setSubtitleTrack(trackId: Int) {
        try {
            mediaPlayer.spuTrack = trackId
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error setting subtitle track $trackId", e)
        }
    }

    @Synchronized
    fun release() {
        try {
            detachViews()
            try {
                mediaPlayer.setEventListener(null)
            } catch (_: Exception) {}
            try {
                mediaPlayer.stop()
            } catch (_: Exception) {}
            try {
                mediaPlayer.media = null
            } catch (_: Exception) {}
            try {
                currentMedia?.release()
                currentMedia = null
            } catch (_: Exception) {}
            mediaPlayer.release()
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al liberar VLC MediaPlayer", e)
        }
    }
}
