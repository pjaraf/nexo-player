package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.Episode
import com.example.data.models.LiveChannel
import com.example.data.models.ProgressItem
import com.example.player.PlayerManager
import com.example.player.VlcPlayerView
import com.example.ui.components.BreakingNewsTvBanner
import com.example.ui.components.ScreenCastDialog
import com.example.ui.components.TvFullscreenPlayerOverlay
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Data structures for tracks and screen aspect ratio mode
data class MediaTrackOption(
    val id: Int,
    val label: String,
    val isSelected: Boolean
)

enum class ScreenResizeMode(val ratio: String?, val scale: Float, val title: String, val shortLabel: String) {
    FIT(null, 0f, "Ajustar (Original)", "Original"),
    ZOOM(null, 1.25f, "Pantalla Completa (Zoom)", "Zoom"),
    FILL("16:9", 0f, "Estirar Pantalla (16:9)", "Estirar")
}

@Composable
fun PlayerScreen(
    initialStreamUrl: String,
    initialTitle: String,
    isLive: Boolean,
    channelId: String? = null,
    categoryId: String? = null,
    kind: String? = null,
    contentId: String? = null,
    image: String? = null,
    resumeMs: Long = 0L,
    nextUrl: String? = null,
    nextTitle: String? = null,
    nextContentId: String? = null,
    nextEpImage: String? = null,
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onClose)

    var isLandscape by remember { mutableStateOf(true) }
    var showScreenCastDialog by remember { mutableStateOf(false) }

    // Immersive Fullscreen and Screen Orientation handling on phones/tablets
    DisposableEffect(context, isLandscape) {
        val activity = context as? Activity
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // Set fullscreen landscape orientation by default
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // Hide Android System Bars for fully immersive fullscreen playback
        window?.let { win ->
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window?.let { win ->
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            activity?.requestedOrientation = originalOrientation
        }
    }

    var currentTitle by remember { mutableStateOf(initialTitle) }
    var currentChannelId by remember { mutableStateOf(channelId) }
    var currentCoverImage by remember { mutableStateOf(image) }

    // Channel change banner state (Breaking News TV Lower-Third style)
    var showLiveChannelBanner by remember { mutableStateOf(false) }
    var bannerChannelNumber by remember { mutableStateOf<String?>(null) }
    var bannerCategoryName by remember { mutableStateOf<String?>(null) }
    var swipeBannerDirection by remember { mutableStateOf<String?>(null) }

    // Automatically trigger the TV lower third banner on initial live load or channel switch
    LaunchedEffect(currentChannelId, isLive) {
        if (isLive && !currentChannelId.isNullOrBlank()) {
            showLiveChannelBanner = true
            delay(4500)
            showLiveChannelBanner = false
        }
    }

    // Multi-candidate stream URLs for Live TV (tries .ts, .m3u8, raw /live/, raw)
    val candidates = remember(currentChannelId, initialStreamUrl, isLive) {
        if (isLive && !currentChannelId.isNullOrBlank()) {
            val list = XtreamApi.getLiveStreamCandidates(currentChannelId!!)
            if (list.isNotEmpty()) list else listOf(initialStreamUrl)
        } else {
            listOf(initialStreamUrl)
        }
    }
    var candidateIndex by remember(currentChannelId) { mutableIntStateOf(0) }
    var streamUrl by remember(currentChannelId) {
        mutableStateOf(candidates.getOrElse(0) { initialStreamUrl })
    }

    var isLoading by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }

    val playerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            playerFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val coroutineScope = rememberCoroutineScope()

    // Series episode navigation & auto-play state
    var seriesEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var seriesTitleName by remember { mutableStateOf("") }
    var currentEpisodeIndex by remember { mutableIntStateOf(-1) }
    var isNextLoading by remember { mutableStateOf(false) }
    var nextLoadingTitle by remember { mutableStateOf("") }

    // Load full series detail in background to enable auto-advancing next episodes
    LaunchedEffect(kind, contentId) {
        if (kind == "series" && contentId != null) {
            try {
                val detail = XtreamApi.getSeriesDetail(contentId)
                seriesTitleName = detail.first?.name ?: initialTitle
                val map = detail.second
                val flattened = map.values.flatten()
                seriesEpisodes = flattened
                val foundIdx = flattened.indexOfFirst { ep ->
                    val epUrl = XtreamApi.getSeriesStreamUrl(ep.epId, ep.containerExtension ?: "mp4")
                    epUrl == initialStreamUrl || (nextContentId != null && ep.epId == nextContentId) || initialTitle.contains(ep.displayTitle)
                }
                if (foundIdx >= 0) {
                    currentEpisodeIndex = foundIdx
                } else if (flattened.isNotEmpty()) {
                    currentEpisodeIndex = 0
                }
            } catch (e: Exception) {
                Log.e("PlayerScreen", "Failed to load series detail for autoplay", e)
            }
        }
    }

    // Dedicated VLC Player Manager
    val playerManager = remember { PlayerManager(context) }

    // VOD position tracking
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Audio, Subtitles, and Aspect Ratio / Fullscreen states
    var availableAudioTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var availableSubtitleTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var isSubtitlesDisabled by remember { mutableStateOf(false) }
    var showAudioSubtitlesDialog by remember { mutableStateOf(false) }
    var currentResizeMode by remember(isLive) {
        mutableStateOf(if (isLive) ScreenResizeMode.FILL else ScreenResizeMode.FIT)
    }
    var resizeToastText by remember { mutableStateOf<String?>(null) }

    fun refreshTracks() {
        val audios = playerManager.getAudioTracks().map {
            MediaTrackOption(id = it.id, label = it.name, isSelected = it.isSelected)
        }
        val subs = playerManager.getSubtitleTracks().map {
            MediaTrackOption(id = it.id, label = it.name, isSelected = it.isSelected)
        }
        availableAudioTracks = audios
        availableSubtitleTracks = subs
        isSubtitlesDisabled = subs.none { it.isSelected } || playerManager.mediaPlayer.spuTrack == -1
    }

    fun applyResizeMode(mode: ScreenResizeMode) {
        playerManager.setAspectRatio(mode.ratio)
        playerManager.setScale(mode.scale)
    }

    fun cycleResizeMode() {
        val nextMode = when (currentResizeMode) {
            ScreenResizeMode.FIT -> ScreenResizeMode.ZOOM
            ScreenResizeMode.ZOOM -> ScreenResizeMode.FILL
            ScreenResizeMode.FILL -> ScreenResizeMode.FIT
        }
        currentResizeMode = nextMode
        applyResizeMode(nextMode)
        resizeToastText = nextMode.title
    }

    fun selectAudioTrack(option: MediaTrackOption) {
        try {
            playerManager.setAudioTrack(option.id)
            refreshTracks()
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error setting audio track", e)
        }
    }

    fun selectSubtitleTrack(option: MediaTrackOption?) {
        try {
            if (option == null) {
                playerManager.setSubtitleTrack(-1)
                isSubtitlesDisabled = true
            } else {
                playerManager.setSubtitleTrack(option.id)
                isSubtitlesDisabled = false
            }
            refreshTracks()
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error setting subtitle track", e)
        }
    }

    // Function to advance to the next episode with clear user notification
    fun playNextEpisode() {
        if (kind != "series") return
        if (currentEpisodeIndex >= 0 && currentEpisodeIndex + 1 < seriesEpisodes.size) {
            val nextEp = seriesEpisodes[currentEpisodeIndex + 1]
            val nextUrlCalculated = XtreamApi.getSeriesStreamUrl(nextEp.epId, nextEp.containerExtension ?: "mp4")
            val seasonNum = nextEp.season ?: (currentEpisodeIndex + 1)
            val fullNextTitle = if (seriesTitleName.isNotBlank()) {
                "$seriesTitleName - T${seasonNum}E${nextEp.epNumber}: ${nextEp.displayTitle}"
            } else {
                "${nextEp.epNumber}. ${nextEp.displayTitle}"
            }

            coroutineScope.launch {
                isNextLoading = true
                nextLoadingTitle = fullNextTitle
                try {
                    playerManager.pause()
                } catch (_: Exception) {}
                delay(1800)
                currentEpisodeIndex += 1
                currentTitle = fullNextTitle
                if (!nextEp.info?.movieImage.isNullOrBlank()) {
                    currentCoverImage = nextEp.info.movieImage
                } else if (nextEpImage != null) {
                    currentCoverImage = nextEpImage
                }
                streamUrl = nextUrlCalculated
                isNextLoading = false
            }
        } else if (!nextUrl.isNullOrBlank()) {
            coroutineScope.launch {
                isNextLoading = true
                nextLoadingTitle = nextTitle ?: "Siguiente Episodio"
                try {
                    playerManager.pause()
                } catch (_: Exception) {}
                delay(1800)
                currentTitle = nextTitle ?: "Siguiente Episodio"
                if (nextEpImage != null) {
                    currentCoverImage = nextEpImage
                }
                streamUrl = nextUrl
                isNextLoading = false
            }
        } else {
            onClose()
        }
    }

    var liveChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var liveIndex by remember { mutableStateOf(-1) }

    // Stop playback immediately when app goes to background, release on dispose
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

    // Load category channels for Live switching
    LaunchedEffect(isLive, categoryId, currentChannelId) {
        if (isLive) {
            val list = XtreamApi.getLiveChannels(if (categoryId == "ALL" || categoryId == "__FAVS__") null else categoryId)
            liveChannels = list
            if (currentChannelId != null) {
                val idx = list.indexOfFirst { it.id == currentChannelId }
                if (idx >= 0) {
                    liveIndex = idx
                    val currentCh = list[idx]
                    bannerChannelNumber = (currentCh.num?.toString()?.takeIf { it.isNotBlank() }) ?: "${idx + 1}"
                    bannerCategoryName = currentCh.groupName.ifBlank { "TRANSMISIÓN EN VIVO HD" }
                }
            }
        }
    }

    // Setup VLC Player Callbacks
    LaunchedEffect(playerManager) {
        playerManager.onBuffering = { buffering, _ ->
            isLoading = buffering
        }
        playerManager.onPlayingChanged = { playing ->
            isPlaying = playing
            if (playing) {
                isLoading = false
                errorMsg = null
            }
        }
        playerManager.onTimeChanged = { timeMs ->
            currentPosition = timeMs.coerceAtLeast(0L)
        }
        playerManager.onLengthChanged = { lenMs ->
            duration = lenMs.coerceAtLeast(0L)
        }
        playerManager.onTracksChanged = {
            refreshTracks()
        }
        playerManager.onEndReached = {
            isLoading = false
            if (kind == "movie") {
                onClose()
            } else if (kind == "series") {
                playNextEpisode()
            }
        }
        playerManager.onError = { error ->
            Log.w("PlayerScreen", "VLC Player error on $streamUrl: $error")
            if (isLive && candidateIndex + 1 < candidates.size) {
                candidateIndex++
                streamUrl = candidates[candidateIndex]
            } else {
                isLoading = false
                errorMsg = if (isLive) "Canal en mantenimiento, cambie canal" else "Error al reproducir contenido"
            }
        }
    }

    // Play/Update stream URL in VLC
    LaunchedEffect(streamUrl) {
        isLoading = true
        errorMsg = null
        if (isLive) {
            // Give smooth debounce on live channel zap
            delay(200)
        }
        try {
            val startPos = if (candidateIndex == 0) resumeMs else 0L
            playerManager.play(streamUrl, startPos)
            applyResizeMode(currentResizeMode)
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error starting VLC stream $streamUrl", e)
            if (isLive && candidateIndex + 1 < candidates.size) {
                candidateIndex++
                streamUrl = candidates[candidateIndex]
            } else {
                errorMsg = if (isLive) "Canal en mantenimiento, cambie canal" else "Error de reproducción"
                isLoading = false
            }
        }
    }

    // Watchdog fallback for live streams stuck in buffering for > 6 seconds
    LaunchedEffect(streamUrl, isLive) {
        if (isLive && candidates.size > 1) {
            delay(6000)
            if (isLoading && !playerManager.mediaPlayer.isPlaying) {
                Log.w("PlayerScreen", "Watchdog triggered for $streamUrl, trying next candidate...")
                if (candidateIndex + 1 < candidates.size) {
                    candidateIndex++
                    streamUrl = candidates[candidateIndex]
                }
            }
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4500)
            showControls = false
        }
    }

    // Dismiss channel change broadcast banner after 4.5s
    LaunchedEffect(showLiveChannelBanner) {
        if (showLiveChannelBanner) {
            delay(4500)
            showLiveChannelBanner = false
            swipeBannerDirection = null
        }
    }

    // Dismiss resize mode toast banner after 2.2s
    LaunchedEffect(resizeToastText) {
        if (resizeToastText != null) {
            delay(2200)
            resizeToastText = null
        }
    }

    // Periodic Progress & Position Tracking
    LaunchedEffect(playerManager, isLive) {
        while (true) {
            delay(1000)
            if (playerManager.mediaPlayer.isPlaying) {
                currentPosition = playerManager.mediaPlayer.time.coerceAtLeast(0L)
                duration = playerManager.mediaPlayer.length.coerceAtLeast(0L)
            }
            if (!isLive && contentId != null && kind != null && playerManager.mediaPlayer.isPlaying) {
                val currentPos = playerManager.mediaPlayer.time
                val dur = playerManager.mediaPlayer.length
                if (currentPos > 3000 && dur > 10000) {
                    val item = ProgressItem(
                        key = "${kind}:${contentId}",
                        kind = kind,
                        id = contentId,
                        title = currentTitle,
                        image = image,
                        streamUrl = streamUrl,
                        positionMs = currentPos,
                        durationMs = dur
                    )
                    viewModel.saveProgress(item)
                }
            }
        }
    }

    // Channel jumping for Live TV
    fun jumpChannel(delta: Int, gestureName: String? = null) {
        if (!isLive || liveChannels.isEmpty() || liveIndex < 0) return
        val nextIdx = (liveIndex + delta + liveChannels.size) % liveChannels.size
        val target = liveChannels[nextIdx]
        liveIndex = nextIdx
        currentChannelId = target.id
        currentTitle = target.name
        currentCoverImage = target.streamIcon
        bannerChannelNumber = (target.num?.toString()?.takeIf { it.isNotBlank() }) ?: "${nextIdx + 1}"
        bannerCategoryName = target.groupName.ifBlank { "TRANSMISIÓN EN VIVO HD" }
        candidateIndex = 0
        swipeBannerDirection = if (delta > 0) "▲ Siguiente canal" else "▼ Canal anterior"
        showLiveChannelBanner = true
        showControls = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
                            if (!showControls) {
                                showControls = true
                                if (isLive) showLiveChannelBanner = true
                                true
                            } else false
                        }
                        Key.MediaPlayPause -> {
                            if (playerManager.mediaPlayer.isPlaying) playerManager.pause() else playerManager.resume()
                            showControls = true
                            true
                        }
                        Key.MediaPlay -> {
                            playerManager.resume()
                            showControls = true
                            true
                        }
                        Key.MediaPause -> {
                            playerManager.pause()
                            showControls = true
                            true
                        }
                        Key.DirectionLeft -> {
                            if (!showControls && !isLive) {
                                val target = (playerManager.mediaPlayer.time - 10000L).coerceAtLeast(0L)
                                playerManager.seekTo(target)
                                showControls = true
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (!showControls && !isLive) {
                                val target = (playerManager.mediaPlayer.time + 10000L).coerceAtMost(duration)
                                playerManager.seekTo(target)
                                showControls = true
                                true
                            } else false
                        }
                        Key.MediaFastForward -> {
                            val target = (playerManager.mediaPlayer.time + 10000L).coerceAtMost(duration)
                            playerManager.seekTo(target)
                            showControls = true
                            true
                        }
                        Key.MediaRewind, Key.MediaPrevious -> {
                            val target = (playerManager.mediaPlayer.time - 10000L).coerceAtLeast(0L)
                            playerManager.seekTo(target)
                            showControls = true
                            true
                        }
                        Key.MediaNext -> {
                            if (kind == "series") {
                                playNextEpisode()
                                true
                            } else false
                        }
                        Key.DirectionUp -> {
                            if (isLive && !showControls) {
                                jumpChannel(1, "up")
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (isLive && !showControls) {
                                jumpChannel(-1, "down")
                                true
                            } else false
                        }
                        Key.Back, Key.Escape -> {
                            if (showControls) {
                                showControls = false
                                true
                            } else {
                                onClose()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        if (isLive && showControls) {
                            showLiveChannelBanner = true
                        }
                    }
                )
            }
            .then(
                if (isLive) {
                    Modifier.pointerInput(liveChannels, liveIndex) {
                        var dragY = 0f
                        var dragX = 0f
                        detectDragGestures(
                            onDragStart = {
                                dragY = 0f
                                dragX = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragY += dragAmount.y
                                dragX += dragAmount.x
                            },
                            onDragEnd = {
                                val threshold = 55f
                                if (abs(dragY) > abs(dragX)) {
                                    if (dragY < -threshold) {
                                        jumpChannel(1, "up")
                                    } else if (dragY > threshold) {
                                        jumpChannel(-1, "down")
                                    }
                                } else {
                                    if (dragX < -threshold) {
                                        jumpChannel(1, "left")
                                    } else if (dragX > threshold) {
                                        jumpChannel(-1, "right")
                                    }
                                }
                            }
                        )
                    }
                } else Modifier
            )
            .testTag("player_screen")
    ) {
        // VLC Video View
        VlcPlayerView(
            playerManager = playerManager,
            enableSubtitles = true,
            modifier = Modifier.fillMaxSize()
        )

        // Loading overlay
        if (isLoading && !isNextLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NexusPrimary, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
            }
        }

        // Aspect Ratio / Fullscreen Mode Toast Indicator
        AnimatedVisibility(
            visible = resizeToastText != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NexusPrimary),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        when (currentResizeMode) {
                            ScreenResizeMode.ZOOM -> Icons.Default.Fullscreen
                            ScreenResizeMode.FILL -> Icons.Default.AspectRatio
                            ScreenResizeMode.FIT -> Icons.Default.FullscreenExit
                        },
                        contentDescription = null,
                        tint = NexusPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = resizeToastText ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Error message overlay
        if (errorMsg != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.widthIn(max = 480.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = errorMsg ?: "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary)
                    ) {
                        Text("Regresar")
                    }
                }
            }
        }

        // Live Channel Banner (Breaking News style)
        AnimatedVisibility(
            visible = isLive && showLiveChannelBanner,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            BreakingNewsTvBanner(
                channelNumber = bannerChannelNumber ?: "1",
                channelName = currentTitle,
                categoryName = bannerCategoryName ?: "TRANSMISIÓN EN VIVO HD",
                channelLogoUrl = currentCoverImage,
                directionLabel = swipeBannerDirection
            )
        }

        // Next Episode Loading Overlay
        if (isNextLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = NexusPrimary)
                    Text(
                        text = "Cargando siguiente episodio...",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (nextLoadingTitle.isNotBlank()) {
                        Text(
                            text = nextLoadingTitle,
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Player Controls HUD Overlay
        if (showControls) {
            if (isLive) {
                // Simplified Live TV HUD
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.testTag("player_close_btn")) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    // Cast Screen to TV button (Mobile only)
                    if (!isTv) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showScreenCastDialog = true },
                                modifier = Modifier.testTag("player_live_cast_btn")
                            ) {
                                Icon(Icons.Default.Cast, contentDescription = "Transmitir Pantalla a TV", tint = Color.White)
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvFullscreenPlayerOverlay(
                        isPlaying = isPlaying,
                        title = currentTitle,
                        thumbnailUrl = currentCoverImage ?: "",
                        currentPositionMs = currentPosition,
                        durationMs = duration,
                        onPlayPause = {
                            if (playerManager.mediaPlayer.isPlaying) {
                                playerManager.pause()
                            } else {
                                playerManager.resume()
                            }
                        },
                        onRewind = {
                            val target = (playerManager.mediaPlayer.time - 10000L).coerceAtLeast(0L)
                            playerManager.seekTo(target)
                        },
                        onForward = {
                            val target = (playerManager.mediaPlayer.time + 10000L).coerceAtMost(duration)
                            playerManager.seekTo(target)
                        },
                        onExit = onClose,
                        onSubtitles = { showAudioSubtitlesDialog = true },
                        onSkipNext = if (kind == "series" && (currentEpisodeIndex + 1 < seriesEpisodes.size || !nextUrl.isNullOrBlank())) {
                            { playNextEpisode() }
                        } else null,
                        onAspectRatio = { cycleResizeMode() }
                    )

                    // Cast Screen to TV button on phone for VOD / Movies / Series
                    if (!isTv) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(24.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showScreenCastDialog = true },
                                modifier = Modifier.testTag("player_vod_cast_btn")
                            ) {
                                Icon(Icons.Default.Cast, contentDescription = "Transmitir Pantalla a TV", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Screen Cast Dialog for Mobile
        if (showScreenCastDialog) {
            ScreenCastDialog(
                streamUrl = streamUrl,
                title = currentTitle,
                onDismiss = { showScreenCastDialog = false }
            )
        }

        // Audio & Subtitles Selection Dialog
        if (showAudioSubtitlesDialog) {
            AudioSubtitlesDialog(
                availableAudioTracks = availableAudioTracks,
                availableSubtitleTracks = availableSubtitleTracks,
                isSubtitlesDisabled = isSubtitlesDisabled,
                onSelectAudio = { track -> selectAudioTrack(track) },
                onSelectSubtitle = { track -> selectSubtitleTrack(track) },
                onDismiss = { showAudioSubtitlesDialog = false }
            )
        }
    }
}

@Composable
fun AudioSubtitlesDialog(
    availableAudioTracks: List<MediaTrackOption>,
    availableSubtitleTracks: List<MediaTrackOption>,
    isSubtitlesDisabled: Boolean,
    onSelectAudio: (MediaTrackOption) -> Unit,
    onSelectSubtitle: (MediaTrackOption?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14151F),
        title = {
            Text(
                "Idiomas y Subtítulos (VLC)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Audio",
                    color = TvFocusBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (availableAudioTracks.isEmpty()) {
                    Text("No hay pistas de audio alternativas disponibles.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    availableAudioTracks.forEach { track ->
                        val isSelected = track.isSelected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectAudio(track)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = TvFocusBlue, unselectedColor = Color.White)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(track.label, color = if (isSelected) Color.White else Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Subtítulos",
                    color = TvFocusBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (availableSubtitleTracks.isEmpty()) {
                    Text("No hay subtítulos disponibles.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSubtitle(null)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = isSubtitlesDisabled,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = TvFocusBlue, unselectedColor = Color.White)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Desactivar Subtítulos", color = if (isSubtitlesDisabled) Color.White else Color.Gray)
                    }
                    availableSubtitleTracks.forEach { track ->
                        val isSelected = track.isSelected && !isSubtitlesDisabled
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSubtitle(track)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = TvFocusBlue, unselectedColor = Color.White)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(track.label, color = if (isSelected) Color.White else Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Text("Listo", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}

fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
