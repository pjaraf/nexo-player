package com.example.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    // Force SurfaceView (useTextureView = false) on Android TV / TV Boxes for zero-overhead HW composition and crash prevention
    val effectiveUseTextureView = if (isTv) false else useTextureView

    AndroidView(
        factory = { ctx ->
            VLCVideoLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                keepScreenOn = true
                isFocusable = false
                isFocusableInTouchMode = false
                playerManager.attachViews(this, enableSubtitles, effectiveUseTextureView)
            }
        },
        update = { layout ->
            layout.keepScreenOn = true
            playerManager.attachViews(layout, enableSubtitles, effectiveUseTextureView)
        },
        onRelease = {
            playerManager.detachViews()
        },
        modifier = modifier
    )
}


