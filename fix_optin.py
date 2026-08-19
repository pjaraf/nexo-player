with open('app/src/main/java/com/example/ui/screens/SeriesDetailScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)\n@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)', '@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)')

with open('app/src/main/java/com/example/ui/screens/SeriesDetailScreen.kt', 'w') as f:
    f.write(content)
