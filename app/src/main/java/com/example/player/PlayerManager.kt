package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Gestor exclusivo de reproducción para Canales de TV en Vivo.
 * Utiliza ExoPlayer optimizado con soporte de redirecciones y streaming continuo.
 */
@OptIn(UnstableApi::class)
class PlayerManager(val context: Context) {

    val exoPlayer: ExoPlayer

    init {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    fun play(url: String) {
        try {
            Log.d("PlayerManager", "Reproduciendo canal en vivo: $url")
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al reproducir $url", e)
        }
    }

    fun pause() {
        try {
            exoPlayer.pause()
            exoPlayer.playWhenReady = false
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al pausar", e)
        }
    }

    fun stop() {
        try {
            exoPlayer.stop()
            exoPlayer.playWhenReady = false
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al detener", e)
        }
    }

    fun release() {
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error al liberar PlayerManager", e)
        }
    }
}
