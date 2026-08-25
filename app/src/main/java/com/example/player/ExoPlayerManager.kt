package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class ExoPlayerManager(private val context: Context) {
    var exoPlayer: ExoPlayer? = null
        private set

    init {
        exoPlayer = ExoPlayer.Builder(context).build()
    }

    fun attachPlayerView(playerView: PlayerView) {
        playerView.player = exoPlayer
        playerView.useController = false
        playerView.isFocusable = false
    }

    fun detachPlayerView() {
        // Optional cleanup
    }

    fun playUrl(url: String) {
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
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
