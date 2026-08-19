with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    content = f.read()

bad_str = """                        onSubtitles = { showTracksDialog = true }
                    )
                }
        // Idioma y Subtítulos Dialog"""

good_str = """                        onSubtitles = { showTracksDialog = true }
                    )
                }
            }
        }

        // Idioma y Subtítulos Dialog"""

content = content.replace(bad_str, good_str)
content = content.replace("relMovie.streamIcon", "movieCover")

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'w') as f:
    f.write(content)

