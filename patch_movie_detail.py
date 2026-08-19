with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    lines = f.readlines()

import_line = "import com.example.ui.components.TvFullscreenPlayerOverlay\n"
if import_line not in lines:
    lines.insert(20, import_line)

start_idx = -1
end_idx = -1

for i, line in enumerate(lines):
    if "AnimatedVisibility(" in line and "visible = showPlayerControls," in lines[i+1]:
        start_idx = i
    elif start_idx != -1 and "        // Idioma y Subtítulos Dialog" in line:
        end_idx = i
        break

if start_idx != -1 and end_idx != -1:
    replacement = """                // Full Screen Controls Overlay
                AnimatedVisibility(
                    visible = showPlayerControls,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    TvFullscreenPlayerOverlay(
                        isPlaying = isPlaying,
                        title = title,
                        thumbnailUrl = relMovie.streamIcon?.ifBlank { POSTER_FALLBACK } ?: POSTER_FALLBACK,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onPlayPause = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlaying = true
                            }
                        },
                        onRewind = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) },
                        onForward = {
                            val d = exoPlayer.duration.coerceAtLeast(0L)
                            val target = if (d > 0) (exoPlayer.currentPosition + 10000).coerceAtMost(d) else (exoPlayer.currentPosition + 10000)
                            exoPlayer.seekTo(target)
                        },
                        onExit = { isFullScreenMode = false },
                        onSubtitles = { showTracksDialog = true }
                    )
                }
"""
    new_lines = lines[:start_idx] + [replacement] + lines[end_idx:]
    with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'w') as f:
        f.writelines(new_lines)
    print("Patched successfully")
else:
    print("Could not find blocks")

