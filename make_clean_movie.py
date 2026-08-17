import re

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    code = f.read()

# Grab everything up to MovieDetailTvScreen
movie_phone = re.search(r'(@Composable\nprivate fun MovieDetailPhoneScreen\([\s\S]*)$', code).group(1)
imports_and_main = re.search(r'([\s\S]*?)@Composable\nprivate fun MovieDetailPhoneScreen', code).group(1)

tv_screen = '''
@OptIn(UnstableApi::class)
@Composable
private fun MovieDetailTvScreen(
    movieId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateMovie: (movieId: String) -> Unit,
    onPlay: (url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var detail by remember { mutableStateOf<VodDetailResponse?>(null) }
    var latestMovies by remember { mutableStateOf<List<VodStream>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var isFullScreenMode by remember { mutableStateOf(false) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isPreviewLoading by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                    isPlaying = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.play()
                    isPlaying = true
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.stop()
                    exoPlayer.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(movieId) {
        loading = true
        detail = XtreamApi.getVodDetail(movieId)
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

    LaunchedEffect(detail) {
        detail?.movieData?.let { data ->
            try {
                isPreviewLoading = true
                val ext = data.containerExtension ?: "mp4"
                val streamUrl = XtreamApi.getVodStreamUrl(movieId, ext)
                if (streamUrl.isNotBlank()) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                    if (savedProgress != null && savedProgress.positionMs > 5000) {
                         exoPlayer.seekTo(savedProgress.positionMs)
                    }
                    exoPlayer.prepare()
                    exoPlayer.play()
                    isPlaying = true
                }
                isPreviewLoading = false
            } catch (e: Exception) {
                isPreviewLoading = false
            }
        }
    }

    val info = detail?.info
    val movieData = detail?.movieData
    val title = info?.name ?: movieData?.name ?: "Película"
    val cover = info?.coverImage ?: movieData?.streamIcon ?: POSTER_FALLBACK
    val releaseYear = info?.releaseDate?.take(4) ?: ""
    val ratingStr = info?.rating?.toString() ?: "0"
    val rating = ratingStr.toDoubleOrNull() ?: 0.0
    val cleanRatingNumber = if (rating > 0) String.format("%.1f", rating) else "Nuevo"
    val plotText = info?.plot ?: info?.description ?: "No hay sinopsis disponible."
    val castText = info?.cast ?: "No disponible"
    val ext = movieData?.containerExtension ?: "mp4"

    val tvFocusBlue = Color(0xFF007AFF)
    val tvButtonDefaultBg = Color.White.copy(alpha = 0.14f)
    val tvButtonDefaultBorder = Color.White.copy(alpha = 0.22f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .testTag("movie_detail_tv_screen")
    ) {
        if (!isFullScreenMode) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color(0xFF0D0E15).copy(alpha = 0.90f), Color(0xFF08090E).copy(alpha = 0.98f)))))
                Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.65f), Color.Transparent))))
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = tvFocusBlue)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 28.sp,
                                        color = Color.White,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF00B0FF),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = cleanRatingNumber,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val dateLine = if (releaseYear.isNotBlank()) "$releaseYear | $title" else title
                            Text(text = dateLine, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(text = "Actores: ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = castText, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(text = "Sinopsis: ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = plotText, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isFullBtnFocused by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = {
                                        isFullScreenMode = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isFullBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isFullBtnFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder),
                                    modifier = Modifier
                                        .height(42.dp)
                                        .focusable()
                                        .onFocusChanged { isFullBtnFocused = it.isFocused }
                                        .testTag("btn_movie_fullscreen")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        Text(text = "Pantalla completa", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Right Column (Player)
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (latestMovies.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
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
                                    var isFocused by remember { mutableStateOf(false) }
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
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
             // Full screen mode
             Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                 AndroidView(
                     factory = { ctx ->
                         PlayerView(ctx).apply {
                             player = exoPlayer
                             useController = true
                             resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                             layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                         }
                     },
                     modifier = Modifier.fillMaxSize()
                 )

                 BackHandler {
                     isFullScreenMode = false
                 }
             }
        }
    }
}
'''

# The full script
with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'w') as f:
    f.write(imports_and_main + tv_screen + '\n' + movie_phone)

