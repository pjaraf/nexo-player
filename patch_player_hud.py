import re

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

# Make sure to import TvFullscreenPlayerOverlay
if "import com.example.ui.components.TvFullscreenPlayerOverlay" not in content:
    content = content.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\nimport com.example.ui.components.TvFullscreenPlayerOverlay")


start_marker = "            } else {\n                Box(\n                    modifier = Modifier\n                        .fillMaxSize()\n                        .background(Color.Black.copy(alpha = 0.45f))\n                        .padding(horizontal = 24.dp, vertical = 16.dp)\n                ) {"

end_marker = "                }\n            }\n        }\n\n        // Audio & Subtitles Selection Dialog"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker, start_idx)

if start_idx != -1 and end_idx != -1:
    replacement = """            } else {
                TvFullscreenPlayerOverlay(
                    isPlaying = isPlaying,
                    title = currentTitle,
                    thumbnailUrl = currentCoverImage ?: "",
                    currentPositionMs = currentPosition,
                    durationMs = duration,
                    onPlayPause = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    onRewind = {
                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                    },
                    onForward = {
                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(duration)
                        exoPlayer.seekTo(target)
                    },
                    onExit = onClose,
                    onSubtitles = { showAudioSubtitlesDialog = true },
                    onSkipNext = if (kind == "series" && (currentEpisodeIndex + 1 < seriesEpisodes.size || !nextUrl.isNullOrBlank())) {
                        { playNextEpisode() }
                    } else null,
                    onAspectRatio = { cycleResizeMode() }
                )
"""
    new_content = content[:start_idx] + replacement + end_marker
    with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
        f.write(new_content)
    print("Patched Player HUD successfully")
else:
    print("Could not find HUD boundaries")
    print(f"Start index: {start_idx}, End index: {end_idx}")

