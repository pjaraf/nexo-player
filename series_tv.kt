private fun SeriesDetailTvScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var loading by remember { mutableStateOf(true) }

    // Dialogs & Expanded states
    var showSeasonPicker by remember { mutableStateOf(false) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    // Full screen in-place mode
    var isFullScreenMode by remember { mutableStateOf(false) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // ExoPlayer for continuous preview and full screen playback
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var isPreviewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }

    // Audio & Subtitle tracks
    var availableAudioTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var availableSubtitleTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var isSubtitlesDisabled by remember { mutableStateOf(false) }

    val currentEpisodes = remember(episodesMap, selectedSeason) {
        episodesMap[selectedSeason] ?: emptyList()
    }

    // Stop playback when leaving foreground
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                    exoPlayer.playWhenReady = false
                    isPlaying = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.playWhenReady = true
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

    // ExoPlayer event listener & Auto-advance next episode
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isPreviewLoading = true
                    Player.STATE_READY -> {
                        isPreviewLoading = false
                        previewError = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isPreviewLoading = false
                        // Automatically play next episode
                        val epIndex = currentEpisodes.indexOfFirst { it.epId == selectedEpisode?.epId }
                        if (epIndex >= 0 && epIndex + 1 < currentEpisodes.size) {
                            selectedEpisode = currentEpisodes[epIndex + 1]
                        }
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("SeriesDetailTv", "Player error: ${error.message}")
                isPreviewLoading = false
                previewError = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioList = mutableListOf<MediaTrackOption>()
                val subList = mutableListOf<MediaTrackOption>()
                var subSelected = false

                for (groupIndex in 0 until tracks.groups.size) {
                    val group = tracks.groups[groupIndex]
                    val trackType = group.type
                    for (trackIndex in 0 until group.length) {
                        val isSelected = group.isTrackSelected(trackIndex)
                        val format = group.getTrackFormat(trackIndex)
                        val lang = format.language
                        val rawLabel = format.label
                        val label = getFriendlyTrackName(trackType, rawLabel, lang, trackIndex)

                        val option = MediaTrackOption(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = label,
                            language = lang,
                            isSelected = isSelected,
                            trackGroup = group
                        )

                        if (trackType == C.TRACK_TYPE_AUDIO) {
                            audioList.add(option)
                        } else if (trackType == C.TRACK_TYPE_TEXT) {
                            subList.add(option)
                            if (isSelected) subSelected = true
                        }
                    }
                }
                availableAudioTracks = audioList
                availableSubtitleTracks = subList
                isSubtitlesDisabled = subList.isEmpty() || !subSelected
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Periodic time tracker for playback position
    LaunchedEffect(isPlaying, isFullScreenMode) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide controls in full screen after 4 seconds
    LaunchedEffect(showPlayerControls, isFullScreenMode) {
        if (isFullScreenMode && showPlayerControls) {
            delay(4000)
            showPlayerControls = false
        }
    }

    // Load series details & episodes
    LaunchedEffect(seriesId) {
        loading = true
        val res = XtreamApi.getSeriesDetail(seriesId)
        info = res.first
        episodesMap = res.second
        if (res.second.isNotEmpty()) {
            val firstSeason = res.second.keys.sortedBy { it.toIntOrNull() ?: 99 }.firstOrNull() ?: "1"
            selectedSeason = firstSeason
            selectedEpisode = res.second[firstSeason]?.firstOrNull()
        }
        loading = false
    }

    // Ensure selectedEpisode is valid when season changes
    LaunchedEffect(currentEpisodes) {
        if (selectedEpisode == null || currentEpisodes.none { it.epId == selectedEpisode?.epId }) {
            selectedEpisode = currentEpisodes.firstOrNull()
        }
    }

    // Update preview player media item when selected episode changes
    LaunchedEffect(selectedEpisode) {
        selectedEpisode?.let { ep ->
            try {
                isPreviewLoading = true
                previewError = false
                val streamUrl = XtreamApi.getSeriesStreamUrl(ep.epId, ep.containerExtension ?: "mp4")
                if (streamUrl.isNotBlank()) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                    exoPlayer.prepare()
                    exoPlayer.play()
                    isPlaying = true
                }
            } catch (e: Exception) {
                Log.e("SeriesDetailTv", "Error loading episode stream for ${ep.displayTitle}", e)
                isPreviewLoading = false
                previewError = true
            }
        }
    }

    // Handle back button on TV remote: If in full screen, collapse to detail. Otherwise exit screen.
    BackHandler {
        if (isFullScreenMode) {
            isFullScreenMode = false
        } else {
            onBack()
        }
    }

    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK
    val releaseYear = info?.releaseDate?.takeIf { it.isNotBlank() } ?: info?.releaseDateAlt?.takeIf { it.isNotBlank() } ?: ""
    val rawRating = info?.rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }
    val displayRating = rawRating?.take(3) ?: "7.0"
    val cleanRatingNumber = displayRating.replace(".0", "").replace(",", ".").toDoubleOrNull()?.toInt()?.toString() ?: "7"
    val castText = info?.cast?.takeIf { it.isNotBlank() } ?: "Sebastián Martínez, Juliette Pardau, Hanny Vizcaíno, Carlos Camacho, Laura de León"
    val plotText = info?.plot?.takeIf { it.isNotBlank() } ?: "Disfruta de todos los episodios completos en alta definición y con la mejor calidad de audio."

    // Unified TV Colors
    val tvFocusBlue = Color(0xFF007AFF)      // AZUL al pasar el control remoto (Focus)
    val tvSelectedRed = Color(0xFFE50914)    // ROJO al seleccionarlo (Active / Selected)
    val tvButtonDefaultBg = Color.White.copy(alpha = 0.14f)
    val tvButtonDefaultBorder = Color.White.copy(alpha = 0.22f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .testTag("series_detail_tv_screen")
    ) {
        if (!isFullScreenMode) {
            // Full Backdrop Wallpaper with Dark Vignette Gradients
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = seriesCover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark Multilayer Vignette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.82f),
                                    Color(0xFF0D0E15).copy(alpha = 0.90f),
                                    Color(0xFF08090E).copy(alpha = 0.98f)
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.92f),
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Transparent
                                )
                            )
                        )
                )
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
                    // Main Info & Video Row (Top Half)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column: Series metadata & Actions
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Title + Rating Badge (Cyan square [ 7 ])
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = seriesTitle,
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

                                // Cyan/Sky-blue Rating Box
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

                            // Air Date / Subtitle line: "2026-07-20 | Pa' Seguirte Queriendo (2026)"
                            val dateLine = if (releaseYear.isNotBlank()) "$releaseYear | $seriesTitle" else seriesTitle
                            Text(
                                text = dateLine,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Episode Tag: "T1 - E1" in Orange / Amber
                            val epNumber = selectedEpisode?.epNumber ?: 1
                            Text(
                                text = "T$selectedSeason - E$epNumber",
                                color = Color(0xFFFF9800),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Actores
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Actores: ",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = castText,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Sinopsis
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Sinopsis: ",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = plotText,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    maxLines = if (isSynopsisExpanded) 4 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons: [ ⛶ Pantalla completa ] and [ 💬 Idioma y subtítulos ]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Button "Pantalla completa" (Same style as others, Blue on focus)
                                var isFullBtnFocused by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = {
                                        isFullScreenMode = true
                                        showPlayerControls = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isFullBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isFullBtnFocused) {
                                        androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                    },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .focusable()
                                        .onFocusChanged { isFullBtnFocused = it.isFocused }
                                        .testTag("btn_series_fullscreen")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Pantalla completa",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Secondary Button "Idioma y subtítulos" (Blue on focus)
                                var isTracksBtnFocused by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = { showTracksDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isTracksBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isTracksBtnFocused) {
                                        androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                    },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .focusable()
                                        .onFocusChanged { isTracksBtnFocused = it.isFocused }
                                        .testTag("btn_series_tracks")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "Idioma y subtítulos",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Middle Poster Artwork Banner (TV layout)
                        Surface(
                            modifier = Modifier
                                .width(120.dp)
                                .height(175.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .shadow(16.dp, RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black
                        ) {
                            AsyncImage(
                                model = seriesCover,
                                contentDescription = seriesTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Right Column: Embedded Live Video Preview Player
                        var isPreviewFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(210.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                                .border(
                                    if (isPreviewFocused) 2.5.dp else 1.5.dp,
                                    if (isPreviewFocused) tvFocusBlue else Color.White.copy(alpha = 0.25f),
                                    RoundedCornerShape(14.dp)
                                )
                                .focusable()
                                .onFocusChanged { isPreviewFocused = it.isFocused }
                                .clickable {
                                    isFullScreenMode = true
                                    showPlayerControls = true
                                }
                                .testTag("series_preview_player")
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        keepScreenOn = true
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { playerView ->
                                    playerView.player = exoPlayer
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Loading spinner
                            if (isPreviewLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = tvFocusBlue, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                                }
                            }

                            // Preview Error / Fallback
                            if (previewError) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Pulse para reproducir", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Fullscreen quick icon overlay in top-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla completa", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Section: Season & Range Selector + Horizontal Episode Numbers Carousel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Season and Range pill selectors
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Season Picker Button (Blue on focus)
                            var isSeasonBtnFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = {
                                    if (episodesMap.size > 1) {
                                        showSeasonPicker = true
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSeasonBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                border = if (isSeasonBtnFocused) {
                                    androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                } else {
                                    androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                },
                                modifier = Modifier
                                    .focusable()
                                    .onFocusChanged { isSeasonBtnFocused = it.isFocused }
                                    .testTag("btn_select_season")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Temporada $selectedSeason",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (episodesMap.size > 1) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Range pill: e.g. "1-13"
                            if (currentEpisodes.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = tvButtonDefaultBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                ) {
                                    Text(
                                        text = "1-${currentEpisodes.size}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        // Horizontal Row of Numbered Episode Blocks:
                        // Focus = AZUL (tvFocusBlue), Seleccionado = ROJO (tvSelectedRed)
                        if (currentEpisodes.isEmpty()) {
                            Text(
                                text = "No hay episodios disponibles para esta temporada.",
                                color = NexusTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(currentEpisodes, key = { it.epId }) { ep ->
                                    val isSelected = selectedEpisode?.epId == ep.epId
                                    var isFocused by remember { mutableStateOf(false) }

                                    Surface(
                                        modifier = Modifier
                                            .width(62.dp)
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .focusable()
                                            .onFocusChanged { state ->
                                                isFocused = state.isFocused
                                            }
                                            .clickable {
                                                if (isSelected) {
                                                    // Toggle in-place full screen seamlessly
                                                    isFullScreenMode = true
                                                    showPlayerControls = true
                                                } else {
                                                    // Select this episode (turns Red and loads preview)
                                                    selectedEpisode = ep
                                                }
                                            }
                                            .testTag("episode_block_${ep.epNumber}"),
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isFocused -> tvFocusBlue      // Foco azul con control remoto
                                            isSelected -> tvSelectedRed   // Seleccionado en ROJO
                                            else -> tvButtonDefaultBg    // Por defecto
                                        },
                                        border = if (isFocused) {
                                            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                        } else if (isSelected) {
                                            androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF5252))
                                        } else {
                                            androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                            }
                                            Text(
                                                text = "${ep.epNumber}",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // MODO PANTALLA COMPLETA IN SITU (SIN RECARGAR)
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showPlayerControls = !showPlayerControls
                    }
                    .testTag("tv_fullscreen_container")
            ) {
                // Video Player filling complete screen
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            keepScreenOn = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Buffering indicator
                if (isPreviewLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = tvFocusBlue, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                    }
                }

                // Full Screen Controls Overlay
                AnimatedVisibility(
                    visible = showPlayerControls,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.75f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    ) {
                        // Top Bar in Full Screen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Collapse / Exit Full Screen button (Blue on focus)
                                var isExitFocused by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = { isFullScreenMode = false },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isExitFocused) tvFocusBlue else Color.Black.copy(alpha = 0.5f),
                                    border = if (isExitFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .focusable()
                                        .onFocusChanged { isExitFocused = it.isFocused }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.FullscreenExit, contentDescription = "Salir", tint = Color.White, modifier = Modifier.size(18.dp))
                                        Text("Salir", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Column {
                                    Text(
                                        text = seriesTitle,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    selectedEpisode?.let { ep ->
                                        Text(
                                            text = "T${selectedSeason}:E${ep.epNumber} - ${ep.displayTitle}",
                                            color = Color(0xFFFF9800),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Audio & Subtitles button in Full Screen (Blue on focus)
                            var isTracksFsFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { showTracksDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isTracksFsFocused) tvFocusBlue else Color.Black.copy(alpha = 0.5f),
                                border = if (isTracksFsFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .focusable()
                                    .onFocusChanged { isTracksFsFocused = it.isFocused }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                    Text("Audio y Subtítulos", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Center Play / Pause and Skip 10s Controls
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -10s Rewind button
                            var isRewindFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (isRewindFocused) tvFocusBlue else Color.Black.copy(alpha = 0.55f))
                                    .border(if (isRewindFocused) 2.dp else 1.dp, Color.White, CircleShape)
                                    .focusable()
                                    .onFocusChanged { isRewindFocused = it.isFocused }
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            // Play / Pause center button
                            var isPlayCenterFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        exoPlayer.play()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlayCenterFocused) tvFocusBlue else Color.White.copy(alpha = 0.25f))
                                    .border(if (isPlayCenterFocused) 2.5.dp else 1.5.dp, Color.White, CircleShape)
                                    .focusable()
                                    .onFocusChanged { isPlayCenterFocused = it.isFocused }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            // +10s Forward button
                            var isForwardFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    val d = exoPlayer.duration.coerceAtLeast(0L)
                                    val target = if (d > 0) (exoPlayer.currentPosition + 10000).coerceAtMost(d) else (exoPlayer.currentPosition + 10000)
                                    exoPlayer.seekTo(target)
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (isForwardFocused) tvFocusBlue else Color.Black.copy(alpha = 0.55f))
                                    .border(if (isForwardFocused) 2.dp else 1.dp, Color.White, CircleShape)
                                    .focusable()
                                    .onFocusChanged { isForwardFocused = it.isFocused }
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }

                        // Bottom Timeline & Next Episode Bar
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 28.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Progress bar
                            val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = tvFocusBlue,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatPlaybackTime(currentPositionMs)} / ${formatPlaybackTime(durationMs)}",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                // Next episode quick button in full screen
                                val currentIdx = currentEpisodes.indexOfFirst { it.epId == selectedEpisode?.epId }
                                if (currentIdx >= 0 && currentIdx + 1 < currentEpisodes.size) {
                                    val nextEp = currentEpisodes[currentIdx + 1]
                                    var isNextEpFocused by remember { mutableStateOf(false) }
                                    Surface(
                                        onClick = {
                                            selectedEpisode = nextEp
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isNextEpFocused) tvFocusBlue else Color.White.copy(alpha = 0.18f),
                                        border = if (isNextEpFocused) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null,
                                        modifier = Modifier
                                            .focusable()
                                            .onFocusChanged { isNextEpFocused = it.isFocused }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Siguiente: E${nextEp.epNumber}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Season Picker Dialog
        if (showSeasonPicker && episodesMap.size > 1) {
            val sortedSeasons = episodesMap.keys.sortedBy { it.toIntOrNull() ?: 99 }
            AlertDialog(
                onDismissRequest = { showSeasonPicker = false },
                containerColor = Color(0xFF14151F),
                title = {
                    Text("Seleccionar Temporada", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortedSeasons.forEach { seasonKey ->
                            val isCurrent = seasonKey == selectedSeason
                            var isRowFocused by remember { mutableStateOf(false) }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .focusable()
                                    .onFocusChanged { isRowFocused = it.isFocused }
                                    .clickable {
                                        selectedSeason = seasonKey
                                        selectedEpisode = episodesMap[seasonKey]?.firstOrNull()
                                        showSeasonPicker = false
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    isRowFocused -> tvFocusBlue      // Foco azul
                                    isCurrent -> tvSelectedRed       // Seleccionado en rojo
                                    else -> Color.White.copy(alpha = 0.1f)
                                },
                                border = if (isRowFocused) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Temporada $seasonKey",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${episodesMap[seasonKey]?.size ?: 0} Episodios",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSeasonPicker = false }) {
                        Text("Cerrar", color = tvFocusBlue, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Idioma y Subtítulos Dialog
        if (showTracksDialog) {
            AlertDialog(
                onDismissRequest = { showTracksDialog = false },
                containerColor = Color(0xFF14151F),
                title = {
                    Text("Idioma y Subtítulos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Pistas de Audio
                        Text("PISTAS DE AUDIO", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        if (availableAudioTracks.isEmpty()) {
                            Text("Audio predeterminado", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        } else {
                            availableAudioTracks.forEach { track ->
                                var isTrackFocused by remember { mutableStateOf(false) }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .focusable()
                                        .onFocusChanged { isTrackFocused = it.isFocused }
                                        .clickable {
                                            try {
                                                val override = TrackSelectionOverride(track.trackGroup.mediaTrackGroup, listOf(track.trackIndex))
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(override)
                                                    .setPreferredAudioLanguage(track.language)
                                                    .build()
                                                availableAudioTracks = availableAudioTracks.map {
                                                    it.copy(isSelected = (it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex))
                                                }
                                            } catch (e: Exception) {
                                                Log.e("SeriesDetailTv", "Error selecting audio track", e)
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isTrackFocused -> tvFocusBlue
                                        track.isSelected -> tvSelectedRed
                                        else -> Color.White.copy(alpha = 0.1f)
                                    },
                                    border = if (isTrackFocused) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(track.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        if (track.isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.15f))

                        // Subtítulos
                        Text("SUBTÍTULOS", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        // Desactivar Subtítulos Option
                        var isDisableSubFocused by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .focusable()
                                .onFocusChanged { isDisableSubFocused = it.isFocused }
                                .clickable {
                                    try {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                        isSubtitlesDisabled = true
                                        availableSubtitleTracks = availableSubtitleTracks.map { it.copy(isSelected = false) }
                                    } catch (e: Exception) {
                                        Log.e("SeriesDetailTv", "Error disabling subtitles", e)
                                    }
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isDisableSubFocused -> tvFocusBlue
                                isSubtitlesDisabled -> tvSelectedRed
                                else -> Color.White.copy(alpha = 0.1f)
                            },
                            border = if (isDisableSubFocused) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Desactivados", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                if (isSubtitlesDisabled) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        availableSubtitleTracks.forEach { track ->
                            var isSubFocused by remember { mutableStateOf(false) }
                            val isChosen = track.isSelected && !isSubtitlesDisabled
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .focusable()
                                    .onFocusChanged { isSubFocused = it.isFocused }
                                    .clickable {
                                        try {
                                            val override = TrackSelectionOverride(track.trackGroup.mediaTrackGroup, listOf(track.trackIndex))
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(override)
                                                .setPreferredTextLanguage(track.language)
                                                .build()
                                            isSubtitlesDisabled = false
                                            availableSubtitleTracks = availableSubtitleTracks.map {
                                                it.copy(isSelected = (it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex))
                                            }
                                        } catch (e: Exception) {
                                            Log.e("SeriesDetailTv", "Error selecting subtitle", e)
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isSubFocused -> tvFocusBlue
                                    isChosen -> tvSelectedRed
                                    else -> Color.White.copy(alpha = 0.1f)
                                },
                                border = if (isSubFocused) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(track.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    if (isChosen) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTracksDialog = false }) {
                        Text("Aceptar", color = tvFocusBlue, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
