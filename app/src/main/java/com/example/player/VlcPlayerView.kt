package com.example.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun VlcPlayerView(
    playerManager: PlayerManager,
    modifier: Modifier = Modifier,
    enableSubtitles: Boolean = true
) {
    AndroidView(
        factory = { ctx ->
            VLCVideoLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                keepScreenOn = true
                playerManager.attachViews(this, enableSubtitles)
            }
        },
        update = { layout ->
            layout.keepScreenOn = true
            playerManager.attachViews(layout, enableSubtitles)
        },
        modifier = modifier.fillMaxSize()
    )
}
