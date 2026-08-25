package com.example.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class ExoPlayerManager(private val context: Context) {
    var exoPlayer: ExoPlayer? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        exoPlayer = ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10000)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        mainHandler.post {
                            Toast.makeText(context, "Canal no disponible", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
    }

    fun attachPlayerView(playerView: PlayerView) {
        playerView.player = exoPlayer
        playerView.useController = false
        playerView.isFocusable = false
    }

    fun detachPlayerView() {
        // Optional cleanup
    }

    fun playChannel(url: String) {
        exoPlayer?.let { player ->
            if (url.isBlank()) return
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    fun playUrl(url: String) {
        playChannel(url)
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}

