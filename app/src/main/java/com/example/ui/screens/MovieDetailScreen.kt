package com.example.ui.screens
import androidx.compose.foundation.interaction.collectIsFocusedAsState

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import android.view.KeyEvent as AndroidKeyEvent
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.VodDetailResponse
import com.example.data.models.VodStream
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.POSTER_FALLBACK
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun MovieDetailScreen(
    movieId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateMovie: (movieId: String) -> Unit = {},
    onPlay: (url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = remember { com.example.utils.DeviceUtils.isTelevision(context) }
    val isTvOrLandscape = configuration.screenWidthDp >= 600 || isTv

    if (isTvOrLandscape) {
        MovieDetailTvScreen(
            movieId = movieId,
            viewModel = viewModel,
            onBack = onBack,
            onNavigateMovie = onNavigateMovie,
            onPlay = onPlay
        )
    } else {
        MovieDetailPhoneScreen(
            movieId = movieId,
            viewModel = viewModel,
            onBack = onBack,
            onNavigateMovie = onNavigateMovie,
            onPlay = onPlay
        )
    }
}



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
    var relatedMovies by remember { mutableStateOf<List<VodStream>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(viewModel.isFavorite("movies", movieId)) }

    // Dialogs & Expanded states
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

    val playButtonFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(loading) {
        if (!loading && !hasRequestedInitialFocus) {
            delay(250)
            try {
                playButtonFocusRequester.requestFocus()
                hasRequestedInitialFocus = true
            } catch (e: Exception) {
                // ignore
            }
        }
    }
    var previewError by remember { mutableStateOf(false) }

    // Audio & Subtitle tracks
    var availableAudioTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var availableSubtitleTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var isSubtitlesDisabled by remember { mutableStateOf(false) }

    val progressList by viewModel.progressList.collectAsState()
    val savedProgress = remember(progressList, movieId) {
        progressList.find { it.kind == "movie" && it.id == movieId }
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

    // ExoPlayer event listener
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
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("MovieDetailTv", "Player error: ${error.message}")
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

    // Load movie details & related movies
    LaunchedEffect(movieId) {
        loading = true
        detail = XtreamApi.getVodDetail(movieId)
        isFav = viewModel.isFavorite("movies", movieId)

        val movieData = detail?.movieData
        val ext = movieData?.containerExtension ?: "mp4"
        val streamUrl = XtreamApi.getVodStreamUrl(movieId, ext)

        try {
            if (streamUrl.isNotBlank()) {
                isPreviewLoading = true
                previewError = false
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                if (savedProgress != null && savedProgress.positionMs > 0) {
                    exoPlayer.seekTo(savedProgress.positionMs)
                }
                exoPlayer.prepare()
                exoPlayer.play()
                isPlaying = true
            }
        } catch (e: Exception) {
            Log.e("MovieDetailTv", "Error loading preview stream", e)
            isPreviewLoading = false
            previewError = true
        }

        try {
            val allMovies = XtreamApi.getVodStreams()
            val categoryId = movieData?.streamId?.let { sId ->
                allMovies.find { it.id == movieId }?.categoryId
            } ?: allMovies.find { it.id == movieId }?.categoryId
            val sameCat = if (!categoryId.isNullOrBlank()) allMovies.filter { it.categoryId == categoryId && it.id != movieId } else emptyList()
            relatedMovies = if (sameCat.isNotEmpty()) sameCat.take(20) else allMovies.filter { it.id != movieId }.take(20)
        } catch (e: Exception) {
            relatedMovies = emptyList()
        }

        loading = false
    }

    // Handle back button on TV remote: If in full screen, collapse to detail. Otherwise exit screen.
    BackHandler {
        if (isFullScreenMode) {
            isFullScreenMode = false
        } else {
            onBack()
        }
    }

    val info = detail?.info
    val movieData = detail?.movieData
    val title = info?.name ?: movieData?.name ?: "Película"
    val cover = info?.coverImage ?: movieData?.streamIcon ?: POSTER_FALLBACK
    val releaseYear = info?.releaseDate?.take(4) ?: ""
    val rawRating = info?.rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }
    val displayRating = rawRating?.take(3) ?: "7.0"
    val cleanRatingNumber = displayRating.replace(".0", "").replace(",", ".").toDoubleOrNull()?.toInt()?.toString() ?: "7"
    val plotText = info?.plot?.takeIf { it.isNotBlank() } ?: info?.description?.takeIf { it.isNotBlank() } ?: "Disfruta de esta película completa en alta definición y con la mejor calidad de audio."
    val castText = info?.cast?.takeIf { it.isNotBlank() } ?: "No disponible"
    val directorText = info?.director?.takeIf { it.isNotBlank() } ?: ""
    val genreText = info?.genre?.takeIf { it.isNotBlank() } ?: ""
    val durationText = info?.duration?.takeIf { it.isNotBlank() } ?: ""
    val ext = movieData?.containerExtension ?: "mp4"

    // Unified TV Colors
    val tvFocusBlue = Color(0xFF007AFF)      // AZUL al pasar el control remoto (Focus)
    val tvSelectedRed = Color(0xFFE50914)    // ROJO al seleccionarlo (Active / Selected)
    val tvButtonDefaultBg = Color.White.copy(alpha = 0.14f)
    val tvButtonDefaultBorder = Color.White.copy(alpha = 0.22f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("movie_detail_tv_screen")
    ) {
        if (!isFullScreenMode) {
            // Fondo dinámico basado en el póster de la película para mejor rendimiento (Evita OOM en TV)
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.35f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Black
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
                        // Left Column: Movie metadata & Actions
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

                            // Air Date / Subtitle line: "2026 | Película | Acción"
                            val metaLine = listOfNotNull(
                                releaseYear.takeIf { it.isNotBlank() },
                                durationText.takeIf { it.isNotBlank() },
                                genreText.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                            ).joinToString(" | ")
                            Text(
                                text = metaLine.ifBlank { title },
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Duration / Info tag in Orange / Amber
                            if (durationText.isNotBlank()) {
                                Text(
                                    text = durationText,
                                    color = Color(0xFFFF9800),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Actores / Reparto
                            if (castText.isNotBlank() && castText != "No disponible") {
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
                            }

                            // Director
                            if (directorText.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Dirección: ",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = directorText,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

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

                            // Action Buttons: [ ⛶ Pantalla completa ] and [ 💬 Idioma y subtítulos ] and [ ❤ Favorito ]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Button "Pantalla completa" (Blue on focus)
                                val fullInteractionSource = remember { MutableInteractionSource() }
                                val isFullBtnFocused by fullInteractionSource.collectIsFocusedAsState()
                                Surface(
                                    onClick = {
                                        isFullScreenMode = true
                                    },
                                    interactionSource = fullInteractionSource,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isFullBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isFullBtnFocused) {
                                        androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                    },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .focusRequester(playButtonFocusRequester)
                                        .testTag("btn_movie_fullscreen")
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
                                val tracksInteractionSource = remember { MutableInteractionSource() }
                                val isTracksBtnFocused by tracksInteractionSource.collectIsFocusedAsState()
                                Surface(
                                    onClick = { showTracksDialog = true },
                                    interactionSource = tracksInteractionSource,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isTracksBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isTracksBtnFocused) {
                                        androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                    },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("btn_movie_tracks")
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

                                // Favorite Button
                                val favInteractionSource = remember { MutableInteractionSource() }
                                val isFavFocused by favInteractionSource.collectIsFocusedAsState()
                                Surface(
                                    onClick = {
                                        isFav = viewModel.toggleFavorite("movies", movieId, title, cover)
                                    },
                                    interactionSource = favInteractionSource,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isFavFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isFavFocused) {
                                        androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                    },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("btn_movie_fav")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (isFav) NexusPrimary else Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = if (isFav) "Favorito" else "Añadir",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Middle Poster Artwork Banner (TV layout - strictly beside player)
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
                                model = cover,
                                contentDescription = title,
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
                                }
                                .testTag("movie_preview_player")
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

                    // Bottom Section: Related Movies Carousel (Matching Series bottom row position)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = tvButtonDefaultBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                            ) {
                                Text(
                                    text = "Películas relacionadas",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }

                            if (relatedMovies.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = tvButtonDefaultBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                ) {
                                    Text(
                                        text = "1-${relatedMovies.size}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        // Horizontal Row of Related Movies
                        if (relatedMovies.isEmpty()) {
                            Text(
                                text = "No hay películas relacionadas disponibles.",
                                color = NexusTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(relatedMovies, key = { it.id }) { relMovie ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isItemFocused by interactionSource.collectIsFocusedAsState()

                                    Surface(
                                        onClick = { onNavigateMovie(relMovie.id) },
                                        interactionSource = interactionSource,
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .testTag("related_movie_${relMovie.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isItemFocused) tvFocusBlue else tvButtonDefaultBg,
                                        border = if (isItemFocused) {
                                            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                        } else {
                                            androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Mini Poster Thumbnail
                                            AsyncImage(
                                                model = relMovie.streamIcon?.ifBlank { POSTER_FALLBACK } ?: POSTER_FALLBACK,
                                                contentDescription = relMovie.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .width(44.dp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(6.dp))
                                            )

                                            // Movie Title
                                            Text(
                                                text = relMovie.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
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
            // Full Screen Mode Player View
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showPlayerControls = !showPlayerControls }
                )

                // Loading spinner in full screen
                if (isPreviewLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = tvFocusBlue, modifier = Modifier.size(52.dp), strokeWidth = 4.dp)
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
                                        text = title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (genreText.isNotBlank()) {
                                        Text(
                                            text = genreText,
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

                        // Bottom Timeline Bar
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
                            }
                        }
                    }
                }
            }
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
                                                Log.e("MovieDetailTv", "Error selecting audio track", e)
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
                                    Log.e("MovieDetailTv", "Error disabling subtitles", e)
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
                                            Log.e("MovieDetailTv", "Error selecting subtitle", e)
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

    BackHandler(onBack = onBack)

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

            // Full Atmospheric Background Poster / Backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
            ) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f),
                                    NexusBackground.copy(alpha = 0.85f),
                                    NexusBackground
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Generous top spacing so the background poster is shown clearly
                Spacer(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(200.dp)
                )

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

