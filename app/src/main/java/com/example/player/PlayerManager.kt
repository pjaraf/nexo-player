package com.example.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var libVLC: LibVLC? = VlcHelper.getLibVLC(context)
    var mediaPlayer: MediaPlayer = MediaPlayer(libVLC ?: VlcHelper.getLibVLC(context))
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
        setupEventListener(mediaPlayer)
    }

    private fun setupEventListener(player: MediaPlayer) {
        player.setEventListener { event ->
            try {
                when (event.type) {
                    MediaPlayer.Event.Buffering -> {
                        val percent = event.buffering
                        mainHandler.post {
                            try { onBuffering?.invoke(percent < 100.0f, percent) } catch (_: Throwable) {}
                        }
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
                        mainHandler.post {
                            try { onBuffering?.invoke(false, 100f) } catch (_: Throwable) {}
                            try { onPlayingChanged?.invoke(true) } catch (_: Throwable) {}
                            try { onTracksChanged?.invoke() } catch (_: Throwable) {}
                        }
                    }
                    MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                        mainHandler.post {
                            try { onPlayingChanged?.invoke(false) } catch (_: Throwable) {}
                        }
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        val currentTimeMs = event.timeChanged
                        // If there is still a pending start position and we get first time event, apply it
                        if (pendingStartPositionMs > 0L) {
                            val startPos = pendingStartPositionMs
                            pendingStartPositionMs = 0L
                            try { mediaPlayer.time = startPos } catch (_: Throwable) {}
                        }
                        mainHandler.post {
                            try { onTimeChanged?.invoke(currentTimeMs) } catch (_: Throwable) {}
                        }
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        val currentLenMs = event.lengthChanged
                        if (pendingStartPositionMs > 0L && currentLenMs > 0L) {
                            val startPos = pendingStartPositionMs
                            pendingStartPositionMs = 0L
                            try { mediaPlayer.time = startPos } catch (_: Throwable) {}
                        }
                        mainHandler.post {
                            try { onLengthChanged?.invoke(currentLenMs) } catch (_: Throwable) {}
                        }
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
                            mainHandler.post {
                                try { onEndReached?.invoke() } catch (_: Throwable) {}
                            }
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
                            mainHandler.post {
                                try { onBuffering?.invoke(false, 0f) } catch (_: Throwable) {}
                                try { onError?.invoke("Error de reproducción en VLC") } catch (_: Throwable) {}
                            }
                        }
                    }
                    MediaPlayer.Event.Vout -> {
                        try {
                            targetAspectRatio?.let { mediaPlayer.aspectRatio = it }
                            targetScale?.let { mediaPlayer.scale = it }
                        } catch (_: Throwable) {}
                        mainHandler.post {
                            try { onTracksChanged?.invoke() } catch (_: Throwable) {}
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w("PlayerManager", "Safe handling of VLC event exception: ${t.message}")
            }
        }
    }

    fun attachViews(layout: VLCVideoLayout, enableSubtitles: Boolean = true, useTextureView: Boolean = true) {
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
    fun changeChannelCompletely(newStreamUrl: String) {
        if (newStreamUrl.isBlank()) return
        try {
            currentUrl = newStreamUrl
            // 1. Limpiar reproductor anterior
            try {
                mediaPlayer.stop()
                detachViews()
                try {
                    mediaPlayer.setEventListener(null)
                } catch (_: Exception) {}
                val oldMedia = currentMedia
                currentMedia = null
                oldMedia?.release()
                mediaPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Inicializar LibVLC si es nulo
            if (libVLC == null) {
                val options = ArrayList<String>().apply {
                    add("--avcodec-hw=any")
                    add("--network-caching=1500")
                }
                libVLC = LibVLC(context.applicationContext, options)
            }

            val activeLibVLC = libVLC ?: VlcHelper.getLibVLC(context)

            // 3. Crear nuevo MediaPlayer
            val newPlayer = MediaPlayer(activeLibVLC).apply {
                mediaPlayer = this
                setupEventListener(this)

                attachedLayout?.let { layout ->
                    try {
                        attachViews(layout, null, false, false)
                    } catch (_: Exception) {}
                }

                val cleanUri = try {
                    Uri.parse(newStreamUrl.trim())
                } catch (_: Exception) {
                    Uri.EMPTY
                }

                val media = Media(activeLibVLC, cleanUri).apply {
                    // Si algún canal específico sigue dando pantalla negra / cierre,
                    // cambia temporalmente el primer valor a 'false' para usar decodificación por software
                    try {
                        setHWDecoderEnabled(true, false)
                    } catch (_: Throwable) {}
                    addOption(":network-caching=1500")
                    addOption(":no-ssl-verify")
                    addOption(":http-no-ssl-verify")
                }
                
                currentMedia = media
                this.media = media
                try {
                    media.release()
                } catch (_: Throwable) {}

                try {
                    targetAspectRatio?.let { aspectRatio = it }
                    targetScale?.let { scale = it }
                } catch (_: Throwable) {}

                play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PlayerManager", "Error in changeChannelCompletely: ${e.message}")
        }
    }

    @Synchronized
    fun changeChannelSafe(streamUrl: String) {
        changeChannelCompletely(streamUrl)
    }

    @Synchronized
    fun playStream(streamUrl: String) {
        changeChannelSafe(streamUrl)
    }

    @Synchronized
    fun play(url: String, startPositionMs: Long = 0L) {
        if (startPositionMs > 0L) {
            // For VOD / resume positions, use standard play with startPositionMs
            if (url.isBlank()) {
                mainHandler.post { onError?.invoke("URL de transmisión inválida") }
                return
            }
            try {
                currentUrl = url
                pendingStartPositionMs = startPositionMs.coerceAtLeast(0L)
                lastSeekTimestamp = 0L
                isSeekingInProgress = false
                Log.d("PlayerManager", "VLC Reproduciendo (VOD): $url (startPos=$startPositionMs)")

                try {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.stop()
                    }
                } catch (_: Throwable) {}

                val oldMedia = currentMedia
                currentMedia = null
                try {
                    if (oldMedia != null && !oldMedia.isReleased) {
                        oldMedia.release()
                    }
                } catch (_: Throwable) {}

                val newMedia = VlcHelper.createMedia(libVLC ?: VlcHelper.getLibVLC(context), url)
                currentMedia = newMedia
                mediaPlayer.media = newMedia

                try {
                    targetAspectRatio?.let { mediaPlayer.aspectRatio = it }
                    targetScale?.let { mediaPlayer.scale = it }
                } catch (_: Throwable) {}

                mediaPlayer.play()
            } catch (e: Throwable) {
                Log.e("PlayerManager", "Error al reproducir con VLC: $url", e)
                mainHandler.post {
                    onError?.invoke(e.localizedMessage ?: "Error al reproducir")
                }
            }
        } else {
            // For live streams and instant playback, use playStream with user-requested 1000ms caching and clock-jitter=0 / clock-synchro=0
            playStream(url)
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
                val mediaToRelease = currentMedia
                currentMedia = null
                mediaPlayer.media = null
                if (mediaToRelease != null && !mediaToRelease.isReleased) {
                    mediaToRelease.release()
                }
            } catch (_: Exception) {}
            mediaPlayer.release()
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al liberar VLC MediaPlayer", e)
        }
    }
}

