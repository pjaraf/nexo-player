package com.example.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.utils.DeviceUtils
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun VlcPlayerView(
    playerManager: PlayerManager,
    modifier: Modifier = Modifier,
    enableSubtitles: Boolean = true,
    useTextureView: Boolean = false
) {
    AndroidView(
        factory = { ctx ->
            val isTv = DeviceUtils.isTelevision(ctx)
            val actualUseTexture = if (isTv) false else useTextureView
            VLCVideoLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                keepScreenOn = true
                isFocusable = false
                isFocusableInTouchMode = false
                playerManager.attachViews(this, enableSubtitles, actualUseTexture)
            }
        },
        update = { layout ->
            val isTv = DeviceUtils.isTelevision(layout.context)
            val actualUseTexture = if (isTv) false else useTextureView
            layout.keepScreenOn = true
            playerManager.attachViews(layout, enableSubtitles, actualUseTexture)
        },
        onRelease = {
            playerManager.detachViews()
        },
        modifier = modifier
    )
}

