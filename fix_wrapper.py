with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    code = f.read()

code = code.replace('''                                    var isFocused by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .border(
                                            width = if (isFocused) 3.dp else 0.dp,
                                            color = if (isFocused) Color.Red else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    ) {
                                         MediaPosterCard(
                                            title = item.displayName,
                                            imageUrl = item.streamIcon,
                                            rating = item.formattedRating,
                                            badgeText = "PELÍCULA",
                                            onClick = { onNavigateMovie(item.id) }
                                        )
                                    }''', '''                                         MediaPosterCard(
                                            title = item.displayName,
                                            imageUrl = item.streamIcon,
                                            rating = item.formattedRating,
                                            badgeText = "PELÍCULA",
                                            onClick = { onNavigateMovie(item.id) }
                                        )''')

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'w') as f:
    f.write(code)

