@Composable
private fun MovieDetailPhoneScreen(
    movieId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateMovie: (movieId: String) -> Unit = {},
    onPlay: (url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit
) {
    var detail by remember { mutableStateOf<VodDetailResponse?>(null) }
    var latestMovies by remember { mutableStateOf<List<VodStream>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(viewModel.isFavorite("movies", movieId)) }

    LaunchedEffect(movieId) {
        loading = true
        detail = XtreamApi.getVodDetail(movieId)
        isFav = viewModel.isFavorite("movies", movieId)

        try {
            val allMovies = XtreamApi.getVodStreams()
            latestMovies = allMovies.filter { it.id != movieId }.take(25)
        } catch (e: Exception) {
            latestMovies = emptyList()
        }

        loading = false
    }

    val progressList by viewModel.progressList.collectAsState()
    val savedProgress = remember(progressList, movieId) {
        progressList.find { it.kind == "movie" && it.id == movieId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .testTag("movie_detail_phone_screen")
    ) {
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else {
            val info = detail?.info
            val movieData = detail?.movieData
            val title = info?.name ?: movieData?.name ?: "Película"
            val image = info?.coverImage ?: movieData?.streamIcon ?: POSTER_FALLBACK
            val ext = movieData?.containerExtension ?: "mp4"

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero Backdrop
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    AsyncImage(
                        model = image,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient overlays
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent,
                                        NexusBackground
                                    )
                                )
                            )
                    )
                }

                // Info Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = Color.White
                        )
                    )

                    // Meta Chips (Year, Rating, Duration, Genre)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        info?.releaseDate?.takeIf { it.isNotBlank() }?.let { yr ->
                            Surface(
                                color = NexusSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder)
                            ) {
                                Text(
                                    text = yr.take(4),
                                    color = NexusTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        info?.rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }?.let { r ->
                            Surface(
                                color = Color(0xFF332000),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                                    Text(
                                        text = r.take(3),
                                        color = Color(0xFFFFC107),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        info?.duration?.takeIf { it.isNotBlank() }?.let { dur ->
                            Surface(
                                color = NexusSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder)
                            ) {
                                Text(
                                    text = dur,
                                    color = NexusTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        info?.genre?.takeIf { it.isNotBlank() }?.let { g ->
                            Text(
                                text = g.split(",").firstOrNull()?.trim() ?: g,
                                color = NexusTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Play Button
                    var isPlayBtnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            val streamUrl = XtreamApi.getVodStreamUrl(movieId, ext)
                            val resumeMs = savedProgress?.positionMs ?: 0L
                            onPlay(streamUrl, title, "movie", movieId, image, resumeMs)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlayBtnFocused) TvFocusBlue else Color.White,
                            contentColor = if (isPlayBtnFocused) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .onFocusChanged { isPlayBtnFocused = it.isFocused }
                            .testTag("movie_play_btn")
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isPlayBtnFocused) Color.White else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (savedProgress != null && savedProgress.positionMs > 5000) "Reanudar Reproducción" else "Reproducir Película",
                            color = if (isPlayBtnFocused) Color.White else Color.Black,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        )
                    }

                    // Synopsis / Plot
                    val plot = info?.plot ?: info?.description ?: "No hay sinopsis disponible."
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = NexusTextSecondary,
                            lineHeight = 22.sp,
                            fontSize = 13.sp
                        )
                    )

                    // Cast & Director
                    info?.cast?.takeIf { it.isNotBlank() }?.let { cast ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Reparto",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = cast,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = NexusTextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    info?.director?.takeIf { it.isNotBlank() }?.let { director ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Dirección",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = director,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = NexusTextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    // --- Películas Más Nuevas Carousel ---
                    if (latestMovies.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Películas más nuevas",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(latestMovies, key = { it.id }) { item ->
                                    MediaPosterCard(
                                        title = item.displayName,
                                        imageUrl = item.streamIcon,
                                        rating = item.formattedRating,
                                        badgeText = "PELÍCULA",
                                        onClick = {
                                            onNavigateMovie(item.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            // Top Bar Icons (Back & Fav)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("movie_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            isFav = viewModel.toggleFavorite("movies", movieId, title, image)
                        },
                        modifier = Modifier.testTag("movie_fav_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFav) NexusPrimary else Color.White
                        )
                    }
                }
            }
        }
    }
}

