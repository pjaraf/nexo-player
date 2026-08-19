with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

import_line = "import com.example.ui.components.TvFullscreenPlayerOverlay\n"
if import_line not in content:
    content = content.replace("import com.example.ui.components.AppLogo", import_line + "import com.example.ui.components.AppLogo")

search_str = """            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))"""

replace_str = """            } else {
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                if (configuration.screenWidthDp > 600) {
                    TvFullscreenPlayerOverlay(
                        isPlaying = isPlaying,
                        title = currentTitle,
                        thumbnailUrl = image ?: "",
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onPlayPause = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onRewind = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)) },
                        onForward = {
                            val d = exoPlayer.duration.coerceAtLeast(0L)
                            val target = if (d > 0) (exoPlayer.currentPosition + 10000L).coerceAtMost(d) else (exoPlayer.currentPosition + 10000L)
                            exoPlayer.seekTo(target)
                        },
                        onExit = onClose,
                        onSubtitles = { showTracksDialog = true }
                    )
                } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))"""

content = content.replace(search_str, replace_str)

# Need to close the bracket for `if (isTv) else {` block.
# Let's find the end of the VOD Box.
# The VOD Box ends around line 1317.

