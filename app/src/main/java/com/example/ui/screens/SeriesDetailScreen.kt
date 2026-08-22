package com.example.ui.screens

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.Episode
import com.example.ui.components.ScreenCastDialog
import com.example.data.models.SeriesDetailInfo
import com.example.player.PlayerManager
import com.example.player.VlcPlayerView
import com.example.ui.components.CinematicBackground
import com.example.ui.components.POSTER_FALLBACK
import com.example.ui.components.TvFullscreenPlayerOverlay
import com.example.ui.components.TvAudioSubtitleDialog
import com.example.ui.components.TvMediaTrackOption
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay
import androidx.annotation.OptIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import android.view.KeyEvent as AndroidKeyEvent

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp > 600

    if (isTv) {
        SeriesDetailTvScreen(seriesId, viewModel, onBack, onPlayEpisode)
    } else {
        SeriesDetailPhoneScreen(seriesId, viewModel, onBack, onPlayEpisode)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailTvScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    
    val playerManager = remember {
        PlayerManager(context).apply {
            mediaPlayer.volume = 0 // Muted by default for preview
        }
    }
    var isPreviewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }

    val playButtonFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Full screen state and controls
    var isFullScreenMode by remember { mutableStateOf(false) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Audio and subtitle tracks
    var showTracksDialog by remember { mutableStateOf(false) }
    var availableAudioTracks by remember { mutableStateOf<List<TvMediaTrackOption>>(emptyList()) }
    var availableSubtitleTracks by remember { mutableStateOf<List<TvMediaTrackOption>>(emptyList()) }
    var isSubtitlesDisabled by remember { mutableStateOf(false) }
    var fullScreenResizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        Triple("Ajustar (Original)", null, 0f),
        Triple("Zoom (Pantalla Completa)", null, 1.25f),
        Triple("Estirar (16:9)", "16:9", 0f)
    )

    fun refreshTracks() {
        val audios = playerManager.getAudioTracks().map {
            TvMediaTrackOption(id = it.id, label = it.name, isSelected = it.isSelected)
        }
        val subs = playerManager.getSubtitleTracks().map {
            TvMediaTrackOption(id = it.id, label = it.name, isSelected = it.isSelected)
        }
        availableAudioTracks = audios
        availableSubtitleTracks = subs
        isSubtitlesDisabled = subs.none { it.isSelected } || playerManager.mediaPlayer.spuTrack == -1
    }

    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()

    fun playNextEpisode() {
        selectedEpisode?.let { ep ->
            val epIndex = currentEpisodes.indexOf(ep)
            val nextEp = currentEpisodes.getOrNull(epIndex + 1)
            if (nextEp != null) {
                selectedEpisode = nextEp
            }
        }
    }

    LaunchedEffect(playerManager) {
        playerManager.onBuffering = { isBuf, _ ->
            isPreviewLoading = isBuf
        }
        playerManager.onPlayingChanged = { playing ->
            isPlaying = playing
            if (playing) {
                previewError = false
                isPreviewLoading = false
                refreshTracks()
            }
        }
        playerManager.onTimeChanged = { t ->
            currentPositionMs = t
        }
        playerManager.onLengthChanged = { len ->
            durationMs = len
        }
        playerManager.onTracksChanged = {
            refreshTracks()
        }
        playerManager.onError = { _ ->
            previewError = true
            isPreviewLoading = false
        }
    }

    LaunchedEffect(playerManager, currentEpisodes, selectedEpisode) {
        playerManager.onEndReached = {
            playNextEpisode()
        }
    }

    // Auto-hide controls in full screen mode after 4s
    LaunchedEffect(showPlayerControls, isFullScreenMode, isPlaying) {
        if (isFullScreenMode && showPlayerControls && isPlaying) {
            delay(4000)
            showPlayerControls = false
        }
    }

    // Lifecycle
    DisposableEffect(lifecycleOwner, playerManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    playerManager.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    playerManager.resume()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerManager.release()
        }
    }

    LaunchedEffect(seriesId) {
        loading = true
        val res = XtreamApi.getSeriesDetail(seriesId)
        info = res.first
        episodesMap = res.second
        if (res.second.isNotEmpty()) {
            selectedSeason = res.second.keys.sortedBy { it.toIntOrNull() ?: 99 }.firstOrNull() ?: "1"
            selectedEpisode = res.second[selectedSeason]?.firstOrNull()
        }
        loading = false
    }

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

    LaunchedEffect(selectedSeason) {
        if (!loading && episodesMap.isNotEmpty()) {
            selectedEpisode = episodesMap[selectedSeason]?.firstOrNull()
        }
    }
    
    // Auto-play the selected episode preview
    LaunchedEffect(selectedEpisode) {
        if (selectedEpisode != null) {
            val url = XtreamApi.getSeriesStreamUrl(selectedEpisode!!.epId, selectedEpisode!!.containerExtension ?: "mp4")
            playerManager.play(url)
        } else {
            playerManager.pause()
        }
    }

    // BackHandler: if in fullscreen, collapse back to preview; else exit detail screen
    BackHandler {
        if (isFullScreenMode) {
            isFullScreenMode = false
        } else {
            onBack()
        }
    }

    // Unified movable content for the VLC player so it NEVER unmounts or reloads when expanding to fullscreen
    val playerVideoContent = remember(playerManager) {
        movableContentOf {
            VlcPlayerView(
                playerManager = playerManager,
                enableSubtitles = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    fun enterFullScreen() {
        try {
            playerManager.mediaPlayer.volume = 100
        } catch (_: Throwable) {}
        isFullScreenMode = true
        showPlayerControls = true
    }

    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK
    val plotText = info?.plot?.takeIf { it.isNotBlank() } ?: "Disfruta de todos los episodios completos en alta definición y con la mejor calidad de audio."
    val castText = info?.cast?.takeIf { it.isNotBlank() } ?: "No disponible"
    val releaseDate = info?.releaseDate?.takeIf { it.isNotBlank() } ?: info?.releaseDateAlt?.takeIf { it.isNotBlank() } ?: "2023"
    val releaseYear = releaseDate.take(4)
    
    val rawRating = info?.rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }
    val displayRating = rawRating?.take(3) ?: "7.0"
    val cleanRatingNumber = displayRating.replace(".0", "").replace(",", ".").toDoubleOrNull()?.toInt()?.toString() ?: "7"

    val tvFocusBlue = Color(0xFF007AFF)
    val tvButtonDefaultBg = Color.White.copy(alpha = 0.14f)
    val tvButtonDefaultBorder = Color.White.copy(alpha = 0.22f)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!isFullScreenMode) {
            // Shared Cinematic Background
            CinematicBackground()
            
            // Dark gradient at the bottom for seasons/episodes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Black),
                            startY = 200f
                        )
                    )
            )
            
            // Dark gradient on left for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent),
                            endX = 800f
                        )
                    )
            )

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
                    // TOP SECTION: Details | Poster | Player Preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LEFT: Details (Title, Info, Buttons)
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Title + Rating Badge
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
                                    color = Color(0xFFE50914),
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
                            
                            // Metadata Line
                            val metaLine = listOfNotNull(
                                releaseDate.takeIf { it.isNotBlank() },
                                "T$selectedSeason E${selectedEpisode?.epNumber ?: "1"}"
                            ).joinToString(" | ")
                            
                            Text(
                                text = metaLine,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Synopsis
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
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Buttons: [ ⛶ Pantalla completa ] and [ 💬 Idioma y subtítulos ] and [ ← Volver ]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // "Pantalla completa" button
                                val fullInteractionSource = remember { MutableInteractionSource() }
                                val isFullBtnFocused by fullInteractionSource.collectIsFocusedAsState()

                                Surface(
                                    onClick = { enterFullScreen() },
                                    interactionSource = fullInteractionSource,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isFullBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isFullBtnFocused) {
                                        androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFFFC107))
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                    },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .focusRequester(playButtonFocusRequester)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.nativeKeyEvent.keyCode) {
                                                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                    AndroidKeyEvent.KEYCODE_ENTER,
                                                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                        enterFullScreen()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
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
                                            imageVector = Icons.Default.PlayArrow,
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

                                // Idioma y Subtítulos Button
                                val langInteractionSource = remember { MutableInteractionSource() }
                                val isLangBtnFocused by langInteractionSource.collectIsFocusedAsState()
                                Surface(
                                    onClick = { showTracksDialog = true },
                                    interactionSource = langInteractionSource,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isLangBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isLangBtnFocused) {
                                        androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFFFC107))
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                    },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.nativeKeyEvent.keyCode) {
                                                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                    AndroidKeyEvent.KEYCODE_ENTER,
                                                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                        showTracksDialog = true
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Idioma y subtítulos",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // Back button (square)
                                val backInteractionSource = remember { MutableInteractionSource() }
                                val isBackBtnFocused by backInteractionSource.collectIsFocusedAsState()
                                
                                Surface(
                                    onClick = onBack,
                                    interactionSource = backInteractionSource,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isBackBtnFocused) tvFocusBlue else tvButtonDefaultBg,
                                    border = if (isBackBtnFocused) {
                                        androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFFFC107))
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, tvButtonDefaultBorder)
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.nativeKeyEvent.keyCode) {
                                                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                    AndroidKeyEvent.KEYCODE_ENTER,
                                                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                        onBack()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Volver",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // CENTER: Poster Banner
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
                        
                        // RIGHT: Embedded Live Video Preview Player
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
                                    enterFullScreen()
                                }
                                .testTag("series_preview_player")
                        ) {
                            playerVideoContent()
                            
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
                    
                    // BOTTOM SECTION: Seasons & Episodes (Horizontal list)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        // Seasons (Text labels)
                        if (episodesMap.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                items(episodesMap.keys.toList().sortedBy { it.toIntOrNull() ?: 0 }) { sKey ->
                                    val isSelected = sKey == selectedSeason
                                    
                                    val seasonInteractionSource = remember { MutableInteractionSource() }
                                    val isSeasonFocused by seasonInteractionSource.collectIsFocusedAsState()

                                    Surface(
                                        onClick = { selectedSeason = sKey },
                                        interactionSource = seasonInteractionSource,
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        border = if (isSeasonFocused) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFC107)) else null,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                        AndroidKeyEvent.KEYCODE_ENTER,
                                                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                            selectedSeason = sKey
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                    ) {
                                        Text(
                                            text = "Temporada $sKey",
                                            color = if (isSeasonFocused) Color(0xFFFFC107) else if (isSelected) Color.White else Color.Gray,
                                            fontSize = 16.sp,
                                            fontWeight = if (isSelected || isSeasonFocused) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Episodes (Square buttons with numbers)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(currentEpisodes) { ep ->
                                val isSelected = ep.epId == selectedEpisode?.epId
                                val epInteractionSource = remember { MutableInteractionSource() }
                                val isEpFocused by epInteractionSource.collectIsFocusedAsState()

                                Surface(
                                    onClick = { selectedEpisode = ep },
                                    interactionSource = epInteractionSource,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.nativeKeyEvent.keyCode) {
                                                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                    AndroidKeyEvent.KEYCODE_ENTER,
                                                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                        selectedEpisode = ep
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        },
                                    color = if (isSelected) Color.White else if (isEpFocused) tvFocusBlue else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (isEpFocused) {
                                        androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFFFC107))
                                    } else if (!isSelected) {
                                        androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    } else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = ep.epNumber.toString(),
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Full Screen Mode Player View (Seamlessly expanded from preview without reloading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showPlayerControls = !showPlayerControls }
            ) {
                playerVideoContent()

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
                val selectedEpTitle = selectedEpisode?.let { ep ->
                    "$seriesTitle - T${selectedSeason}E${ep.epNumber}: ${ep.displayTitle ?: "Episodio ${ep.epNumber}"}"
                } ?: seriesTitle
                val selectedEpThumb = selectedEpisode?.info?.movieImage?.ifBlank { seriesCover } ?: seriesCover
                val hasNextEp = selectedEpisode?.let { ep ->
                    val idx = currentEpisodes.indexOf(ep)
                    idx >= 0 && idx < currentEpisodes.size - 1
                } ?: false

                AnimatedVisibility(
                    visible = showPlayerControls,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    TvFullscreenPlayerOverlay(
                        isPlaying = isPlaying,
                        title = selectedEpTitle,
                        thumbnailUrl = selectedEpThumb,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onPlayPause = {
                            if (playerManager.mediaPlayer.isPlaying) {
                                playerManager.pause()
                                isPlaying = false
                            } else {
                                playerManager.resume()
                                isPlaying = true
                            }
                        },
                        onRewind = { playerManager.seekTo((playerManager.mediaPlayer.time - 10000).coerceAtLeast(0L)) },
                        onForward = {
                            val d = playerManager.mediaPlayer.length.coerceAtLeast(0L)
                            val target = if (d > 0) (playerManager.mediaPlayer.time + 10000).coerceAtMost(d) else (playerManager.mediaPlayer.time + 10000)
                            playerManager.seekTo(target)
                        },
                        onExit = { isFullScreenMode = false },
                        onSubtitles = { showTracksDialog = true },
                        onSkipNext = if (hasNextEp) { { playNextEpisode() } } else null,
                        onAspectRatio = {
                            fullScreenResizeModeIndex = (fullScreenResizeModeIndex + 1) % resizeModes.size
                            val mode = resizeModes[fullScreenResizeModeIndex]
                            playerManager.setAspectRatio(mode.second)
                            playerManager.setScale(mode.third)
                        }
                    )
                }
            }
        }

        // Idioma y Subtítulos Dialog (VLC) - 100% D-Pad Compatible
        TvAudioSubtitleDialog(
            show = showTracksDialog,
            availableAudioTracks = availableAudioTracks,
            availableSubtitleTracks = availableSubtitleTracks,
            isSubtitlesDisabled = isSubtitlesDisabled,
            onSelectAudioTrack = { trackId ->
                try {
                    playerManager.setAudioTrack(trackId)
                    refreshTracks()
                } catch (e: Exception) {
                    Log.e("SeriesDetailTv", "Error selecting audio track", e)
                }
            },
            onSelectSubtitleTrack = { trackId ->
                try {
                    playerManager.setSubtitleTrack(trackId)
                    isSubtitlesDisabled = false
                    refreshTracks()
                } catch (e: Exception) {
                    Log.e("SeriesDetailTv", "Error setting subtitle track", e)
                }
            },
            onDisableSubtitles = {
                try {
                    playerManager.setSubtitleTrack(-1)
                    isSubtitlesDisabled = true
                    refreshTracks()
                } catch (e: Exception) {
                    Log.e("SeriesDetailTv", "Error disabling subtitles", e)
                }
            },
            onDismiss = { showTracksDialog = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailPhoneScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var showScreenCastDialog by remember { mutableStateOf(false) }

    LaunchedEffect(seriesId) {
        loading = true
        val res = XtreamApi.getSeriesDetail(seriesId)
        info = res.first
        episodesMap = res.second
        if (res.second.isNotEmpty()) {
            selectedSeason = res.second.keys.sortedBy { it.toIntOrNull() ?: 99 }.firstOrNull() ?: "1"
            selectedEpisode = res.second[selectedSeason]?.firstOrNull()
        }
        loading = false
    }

    LaunchedEffect(selectedSeason) {
        if (!loading && episodesMap.isNotEmpty()) {
            selectedEpisode = episodesMap[selectedSeason]?.firstOrNull()
        }
    }

    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()
    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CinematicBackground()
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        AsyncImage(
                            model = selectedEpisode?.info?.movieImage ?: seriesCover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                        )
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(top = 40.dp, start = 16.dp).align(Alignment.TopStart)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }

                        IconButton(
                            onClick = { showScreenCastDialog = true },
                            modifier = Modifier.padding(top = 40.dp, end = 16.dp).align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Cast, contentDescription = "Transmitir a TV", tint = Color.White)
                        }
                        
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            selectedEpisode?.let { ep ->
                                IconButton(
                                    onClick = {
                                        val epIndex = currentEpisodes.indexOf(ep)
                                        val nextEp = currentEpisodes.getOrNull(epIndex + 1)
                                        val url = XtreamApi.getSeriesStreamUrl(ep.epId, ep.containerExtension ?: "mp4")
                                        val nextUrl = nextEp?.let { XtreamApi.getSeriesStreamUrl(it.epId, it.containerExtension ?: "mp4") }
                                        
                                        onPlayEpisode(
                                            url,
                                            "$seriesTitle - T${selectedSeason}E${ep.epNumber}: ${ep.displayTitle}",
                                            "series",
                                            seriesId,
                                            ep.info?.movieImage ?: seriesCover,
                                            0L,
                                            nextUrl,
                                            nextEp?.displayTitle,
                                            nextEp?.epId,
                                            nextEp?.info?.movieImage ?: seriesCover
                                        )
                                    },
                                    modifier = Modifier.size(64.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = seriesTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "T$selectedSeason - E${selectedEpisode?.epNumber ?: ""}",
                            color = Color(0xFFE50914),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                            Text(plot, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (episodesMap.size > 1) {
                            Text("Temporadas", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(episodesMap.keys.toList().sortedBy { it.toIntOrNull() ?: 0 }) { sKey ->
                                    val isSelected = sKey == selectedSeason
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedSeason = sKey },
                                        label = { Text("Temporada $sKey") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color(0xFF333333),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text("Episodios", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                itemsIndexed(currentEpisodes) { epIndex, ep ->
                    val isSelected = ep.epId == selectedEpisode?.epId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { selectedEpisode = ep }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ep.epNumber.toString(),
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.width(40.dp)
                        )
                        
                        val epImage = ep.info?.movieImage ?: seriesCover
                        AsyncImage(
                            model = epImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(100.dp)
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ep.displayTitle ?: "Episodio ${ep.epNumber}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (showScreenCastDialog) {
            val ep = selectedEpisode
            val url = ep?.let { XtreamApi.getSeriesStreamUrl(it.epId, it.containerExtension ?: "mp4") }
            ScreenCastDialog(
                streamUrl = url,
                title = "$seriesTitle - T${selectedSeason}E${ep?.epNumber ?: 1}: ${ep?.displayTitle ?: ""}",
                onDismiss = { showScreenCastDialog = false }
            )
        }
    }
}
