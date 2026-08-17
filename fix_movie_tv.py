import re

with open('movie_tv_draft.kt', 'r') as f:
    code = f.read()

# Replace the whole episode list and season picker
# We find "Season and Range pill selectors" and remove it up to the end of that column block
# Basically, replace the entire Column that contains Season picker and LazyRow

start_comment = '// Season and Range pill selectors'
end_string = 'if (!isFullScreenMode) {'

# Let's use regex to replace everything starting from the Column that contains Season and Range pill selectors
# up to the right before full screen mode.
# Actually, the entire block is:
# // Bottom Row: Seasons & Episodes [+] (Only when not in full screen)

search = r'// Bottom Row: Seasons & Episodes[\s\S]*?if \(!isFullScreenMode\) \{'
replace = '''// Bottom Row: Movies Carousel (Only when not in full screen)
        if (!isFullScreenMode && latestMovies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Películas más nuevas",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp, end = 28.dp)
                ) {
                    items(latestMovies, key = { it.id }) { item ->
                        com.example.ui.components.MediaPosterCard(
                            title = item.displayName,
                            imageUrl = item.streamIcon,
                            rating = item.formattedRating,
                            badgeText = "PELÍCULA",
                            onClick = { onNavigateMovie(item.id) }
                        )
                    }
                }
            }
        }
        
        if (!isFullScreenMode) {'''

code = re.sub(search, replace, code)


with open('movie_tv_draft2.kt', 'w') as f:
    f.write(code)

