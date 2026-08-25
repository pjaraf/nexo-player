package com.example.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

@Composable
fun ExoPlayerView(
    playerManager: ExoPlayerManager,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                playerManager.attachPlayerView(this)
            }
        },
        update = { playerView ->
            playerManager.attachPlayerView(playerView)
        },
        onRelease = {
            playerManager.detachPlayerView()
        },
        modifier = modifier
    )
}
