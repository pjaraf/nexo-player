with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("""Key.DirectionLeft -> {
                            if (!isLive) {
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                showControls = true
                                true
                            } else false
                        }""", """Key.DirectionLeft -> {
                            if (!showControls && !isLive) {
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                showControls = true
                                true
                            } else false
                        }""")
content = content.replace("""Key.DirectionRight -> {
                            if (!isLive) {
                                val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(duration)
                                exoPlayer.seekTo(target)
                                showControls = true
                                true
                            } else false
                        }""", """Key.DirectionRight -> {
                            if (!showControls && !isLive) {
                                val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(duration)
                                exoPlayer.seekTo(target)
                                showControls = true
                                true
                            } else false
                        }""")

content = content.replace("""Key.DirectionUp -> {
                            if (isLive) {
                                jumpChannel(1, "up")
                                true
                            } else {
                                showControls = true
                                false
                            }
                        }""", """Key.DirectionUp -> {
                            if (isLive && !showControls) {
                                jumpChannel(1, "up")
                                true
                            } else false
                        }""")

content = content.replace("""Key.DirectionDown -> {
                            if (isLive) {
                                jumpChannel(-1, "down")
                                true
                            } else {
                                showControls = true
                                false
                            }
                        }""", """Key.DirectionDown -> {
                            if (isLive && !showControls) {
                                jumpChannel(-1, "down")
                                true
                            } else false
                        }""")

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
    f.write(content)

