with open('app/src/main/java/com/example/ui/screens/SeriesDetailScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('@OptIn(UnstableApi::class)', '@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)')

with open('app/src/main/java/com/example/ui/screens/SeriesDetailScreen.kt', 'w') as f:
    f.write(content)
