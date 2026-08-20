with open('app/src/main/java/com/example/ui/components/TvFullscreenPlayerOverlay.kt', 'r') as f:
    content = f.read()

# Replace the row setup for the info
target = """                // Left: Thumbnail + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {"""
replacement = """                // Left: Thumbnail + Title
                Row(
                    modifier = Modifier.weight(1f).padding(end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {"""

content = content.replace(target, replacement)

# Replace the text setup
target_text = """                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )"""
replacement_text = """                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )"""

content = content.replace(target_text, replacement_text)

with open('app/src/main/java/com/example/ui/components/TvFullscreenPlayerOverlay.kt', 'w') as f:
    f.write(content)
