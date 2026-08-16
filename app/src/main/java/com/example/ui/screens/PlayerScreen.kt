package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.Episode
import com.example.data.models.LiveChannel
import com.example.data.models.ProgressItem
import com.example.player.PlayerManager
import com.example.ui.components.AppLogo
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Data structures for tracks and screen aspect ratio mode
data class MediaTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val trackGroup: Tracks.Group
)

enum class ScreenResizeMode(val mode: Int, val title: String, val shortLabel: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Ajustar (Original)", "Original"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Pantalla Completa (Zoom)", "Zoom"),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Estirar Pantalla (16:9)", "Estirar")
}

fun getFriendlyTrackName(type: Int, rawLabel: String?, language: String?, index: Int): String {
    val langCode = language?.lowercase()?.trim()
    val baseLang = when (langCode) {
        "es", "spa", "spanish" -> "Español"
        "es-419", "es-la", "lat", "latam" -> "Español Latino"
        "es-es" -> "Español (España)"
        "en", "eng", "english" -> "Inglés"
        "pt", "por", "portuguese" -> "Portugués"
        "fr", "fra", "french" -> "Francés"
        "it", "ita", "italian" -> "Italiano"
        "de", "deu", "ger", "german" -> "Alemán"
        "ja", "jpn", "japanese" -> "Japonés"
        "ko", "kor", "korean" -> "Coreano"
        "zh", "zho", "chi", "chinese" -> "Chino"
        "ru", "rus", "russian" -> "Ruso"
        else -> null
    }

    if (!rawLabel.isNullOrBlank() && rawLabel != language) {
        return if (baseLang != null && !rawLabel.contains(baseLang, ignoreCase = true)) {
            "$baseLang ($rawLabel)"
        } else {
            rawLabel
        }
    }
    val typeName = if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtítulo"
    return baseLang ?: "$typeName ${index + 1}${if (!language.isNullOrBlank()) " ($language)" else ""}"
}

@OptIn(UnstableApi::class)
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
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onClose)

    var isLandscape by remember { mutableStateOf(true) }

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

    val coroutineScope = rememberCoroutineScope()

    // Series episode navigation & auto-play state
    var seriesEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var seriesTitleName by remember { mutableStateOf("") }
    var currentEpisodeIndex by remember { mutableIntStateOf(-1) }
    var isNextLoading by remember { mutableStateOf(false) }
    var nextLoadingTitle by remember { mutableStateOf("") }

    // Fetch series episodes if kind == "series"
    LaunchedEffect(kind, contentId) {
        if (kind == "series" && !contentId.isNullOrBlank()) {
            try {
                val res = XtreamApi.getSeriesDetail(contentId)
                seriesTitleName = res.first?.name ?: ""
                if (currentCoverImage.isNullOrBlank() && !res.first?.cover.isNullOrBlank()) {
                    currentCoverImage = res.first?.cover
                }
                val episodesMap = res.second
                val sortedSeasons = episodesMap.keys.sortedBy { it.toIntOrNull() ?: 99 }
                val flattened = mutableListOf<Episode>()
                for (s in sortedSeasons) {
                    val eps = episodesMap[s] ?: emptyList()
                    flattened.addAll(eps)
                }
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

    // Live TV uses dedicated PlayerManager; Movies & Series use standard ExoPlayer
    val playerManager = remember(isLive) {
        if (isLive) PlayerManager(context) else null
    }

    val exoPlayer = remember(playerManager, isLive) {
        if (isLive && playerManager != null) {
            playerManager.exoPlayer
        } else {
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
            }
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
                    exoPlayer.pause()
                } catch (_: Exception) {}
                delay(1800) // Show "Cargando siguiente episodio..." message
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
                    exoPlayer.pause()
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
            // No more episodes in this series: return to the series carátula screen
            onClose()
        }
    }

    // VOD position tracking
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    // Audio, Subtitles, and Aspect Ratio / Fullscreen states
    var availableAudioTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var availableSubtitleTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var isSubtitlesDisabled by remember { mutableStateOf(false) }
    var showAudioSubtitlesDialog by remember { mutableStateOf(false) }
    var currentResizeMode by remember(isLive) {
        mutableStateOf(if (isLive) ScreenResizeMode.FILL else ScreenResizeMode.FIT)
    }
    var resizeToastText by remember { mutableStateOf<String?>(null) }

    fun cycleResizeMode() {
        val nextMode = when (currentResizeMode) {
            ScreenResizeMode.FIT -> ScreenResizeMode.ZOOM
            ScreenResizeMode.ZOOM -> ScreenResizeMode.FILL
            ScreenResizeMode.FILL -> ScreenResizeMode.FIT
        }
        currentResizeMode = nextMode
        resizeToastText = nextMode.title
    }

    fun selectAudioTrack(option: MediaTrackOption) {
        try {
            val override = TrackSelectionOverride(option.trackGroup.mediaTrackGroup, listOf(option.trackIndex))
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .setPreferredAudioLanguage(option.language)
                .build()
            availableAudioTracks = availableAudioTracks.map {
                it.copy(isSelected = (it.groupIndex == option.groupIndex && it.trackIndex == option.trackIndex))
            }
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error setting audio track", e)
        }
    }

    fun selectSubtitleTrack(option: MediaTrackOption?) {
        try {
            if (option == null) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                isSubtitlesDisabled = true
                availableSubtitleTracks = availableSubtitleTracks.map { it.copy(isSelected = false) }
            } else {
                val override = TrackSelectionOverride(option.trackGroup.mediaTrackGroup, listOf(option.trackIndex))
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(override)
                    .setPreferredTextLanguage(option.language)
                    .build()
                isSubtitlesDisabled = false
                availableSubtitleTracks = availableSubtitleTracks.map {
                    it.copy(isSelected = (it.groupIndex == option.groupIndex && it.trackIndex == option.trackIndex))
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error setting subtitle track", e)
        }
    }

    var liveChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var liveIndex by remember { mutableStateOf(-1) }

    // Stop playback immediately when app goes to background / onPause / onStop, release on dispose
    DisposableEffect(lifecycleOwner, exoPlayer, playerManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    playerManager?.pause() ?: run {
                        exoPlayer.pause()
                        exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    playerManager?.release() ?: run {
                        exoPlayer.stop()
                        exoPlayer.release()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerManager?.release() ?: run {
                exoPlayer.stop()
                exoPlayer.release()
            }
        }
    }

    // Load category channels for Live switching
    LaunchedEffect(isLive, categoryId, currentChannelId) {
        if (isLive) {
            val list = XtreamApi.getLiveChannels(if (categoryId == "ALL" || categoryId == "__FAVS__") null else categoryId)
            liveChannels = list
            val idx = list.indexOfFirst { it.id == currentChannelId }
            liveIndex = idx
            if (idx >= 0) {
                val target = list[idx]
                bannerChannelNumber = (target.num?.toString()?.takeIf { it.isNotBlank() }) ?: "${idx + 1}"
                bannerCategoryName = target.groupName.ifBlank { "TRANSMISIÓN EN VIVO HD" }
                if (currentCoverImage.isNullOrBlank() && !target.streamIcon.isNullOrBlank()) {
                    currentCoverImage = target.streamIcon
                }
            }
        }
    }

    // Prepare & play stream URL
    LaunchedEffect(streamUrl) {
        if (streamUrl.isNotBlank()) {
            isLoading = true
            errorMsg = null
            try {
                Log.d("PlayerScreen", "Preparing stream (candidate $candidateIndex/${candidates.size}): $streamUrl")
                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                exoPlayer.setMediaItem(mediaItem)
                if (resumeMs > 5000 && !isLive && currentEpisodeIndex <= 0) {
                    exoPlayer.seekTo(resumeMs)
                }
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (e: Exception) {
                Log.e("PlayerScreen", "Prepare failed for $streamUrl", e)
                if (isLive && candidateIndex + 1 < candidates.size) {
                    candidateIndex++
                    streamUrl = candidates[candidateIndex]
                } else {
                    errorMsg = if (isLive) "Canal en mantenimiento, cambie canal" else "Próximamente"
                    isLoading = false
                }
            }
        }
    }

    // Watchdog fallback for live streams stuck in buffering for > 6 seconds
    LaunchedEffect(streamUrl, isLive) {
        if (isLive && candidates.size > 1) {
            delay(6000)
            if (isLoading && !exoPlayer.isPlaying) {
                Log.w("PlayerScreen", "Stream loading watchdog triggered for $streamUrl, trying next format...")
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

    // ExoPlayer listener for playback state, candidate fallback, tracks and auto-play next episode
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (!exoPlayer.isPlaying) {
                            isLoading = true
                        }
                    }
                    Player.STATE_READY -> {
                        isLoading = false
                        errorMsg = null
                        duration = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isLoading = false
                        if (kind == "movie") {
                            // Automatically return to movie cover/detail screen when movie finishes
                            onClose()
                        } else if (kind == "series") {
                            // Automatically play next episode with loading notification
                            playNextEpisode()
                        }
                    }
                    Player.STATE_IDLE -> {}
                }
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

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isLoading = false
                    errorMsg = null
                }
            }

            override fun onRenderedFirstFrame() {
                isLoading = false
                errorMsg = null
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("PlayerScreen", "Player error on $streamUrl (candidate $candidateIndex of ${candidates.size}): ${error.message}")
                if (isLive && candidateIndex + 1 < candidates.size) {
                    candidateIndex++
                    streamUrl = candidates[candidateIndex]
                } else {
                    isLoading = false
                    errorMsg = if (isLive) "Canal en mantenimiento, cambie canal" else "Próximamente"
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Periodic Progress & Position Tracking
    LaunchedEffect(exoPlayer, isLive) {
        while (true) {
            delay(1000)
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            if (!isLive && contentId != null && kind != null && exoPlayer.isPlaying) {
                val currentPos = exoPlayer.currentPosition
                val dur = exoPlayer.duration
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
            // Tap gesture detector to toggle HUD controls
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
            // Drag gesture detector to switch Live TV channels with swipe
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
                                        // Swiped up -> Next channel
                                        jumpChannel(1, "up")
                                    } else if (dragY > threshold) {
                                        // Swiped down -> Previous channel
                                        jumpChannel(-1, "down")
                                    }
                                } else {
                                    if (dragX < -threshold) {
                                        // Swiped left -> Next channel
                                        jumpChannel(1, "left")
                                    } else if (dragX > threshold) {
                                        // Swiped right -> Previous channel
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
        // Video View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    resizeMode = if (isLive) AspectRatioFrameLayout.RESIZE_MODE_FILL else currentResizeMode.mode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = if (isLive) AspectRatioFrameLayout.RESIZE_MODE_FILL else currentResizeMode.mode
            },
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // "Cargando siguiente episodio" Animated Overlay
        AnimatedVisibility(
            visible = isNextLoading,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NexusPrimary),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 440.dp)
                    .testTag("player_loading_next_episode_banner")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = NexusPrimary,
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 4.dp
                    )

                    Text(
                        text = "Cargando siguiente episodio...",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (nextLoadingTitle.isNotBlank()) {
                        Text(
                            text = nextLoadingTitle,
                            color = NexusTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Broadcast TV Breaking News style banner for Live TV channel changes (First banner from reference)
        AnimatedVisibility(
            visible = isLive && (showLiveChannelBanner || showControls),
            enter = fadeIn(animationSpec = tween(260)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = if (isLandscape) 32.dp else 14.dp,
                    end = if (isLandscape) 32.dp else 14.dp,
                    bottom = if (isLandscape) 28.dp else 20.dp
                )
                .testTag("player_breaking_news_live_banner")
        ) {
            BreakingNewsTvBanner(
                channelName = currentTitle,
                channelNumber = bannerChannelNumber ?: if (liveIndex >= 0) "${liveIndex + 1}" else "1",
                categoryName = bannerCategoryName ?: "TRANSMISIÓN EN VIVO HD",
                channelLogoUrl = currentCoverImage,
                directionLabel = swipeBannerDirection,
                totalChannels = liveChannels.size,
                currentIndex = if (liveIndex >= 0) liveIndex + 1 else 1
            )
        }

        // Error overlay with App Logo & Custom Notice
        if (errorMsg != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.widthIn(max = 440.dp)
                ) {
                    // Application Logo above the message
                    AppLogo(
                        size = 72.dp,
                        showText = true,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (isLive) {
                        // Live TV Maintenance Message
                        Text(
                            text = "Canal en mantenimiento\ncambie canal",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 24.sp
                        )

                        Text(
                            text = "Este canal está en ajustes técnicos temporales.",
                            color = NexusTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Quick channel switcher buttons for Live TV
                        if (liveChannels.size > 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        errorMsg = null
                                        isLoading = true
                                        jumpChannel(-1)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusSurfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                                    modifier = Modifier.testTag("player_error_prev_btn")
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Canal Anterior", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        errorMsg = null
                                        isLoading = true
                                        jumpChannel(1)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary),
                                    modifier = Modifier.testTag("player_error_next_btn")
                                ) {
                                    Text("Siguiente Canal", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onClose,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                            modifier = Modifier.testTag("player_error_back_btn")
                        ) {
                            Text("Volver", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    } else {
                        // Movies & Series "Próximamente" Message
                        Text(
                            text = "Próximamente",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Text(
                            text = "Este contenido estará disponible muy pronto en la plataforma.",
                            color = NexusTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = onClose,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary),
                            modifier = Modifier.testTag("player_error_back_btn")
                        ) {
                            Text("Volver", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Controls HUD Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLive) {
                // Live TV HUD: ONLY the Close (X) button at top-left
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
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
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    // Top Close button & Title & Orientation Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onClose, modifier = Modifier.testTag("player_close_btn")) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                            }
                        }

                        // Title
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Top Right Action Buttons: Rotate Screen Orientation Toggle Button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { isLandscape = !isLandscape },
                                modifier = Modifier.testTag("player_orientation_toggle_btn")
                            ) {
                                Icon(
                                    if (isLandscape) Icons.Default.ScreenRotation else Icons.Default.CropLandscape,
                                    contentDescription = "Rotar Pantalla",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // VOD Center Controls: Rewind 10s, Play/Pause, Forward 10s
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(NexusPrimary)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(duration)
                                exoPlayer.seekTo(target)
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        if (kind == "series" && (currentEpisodeIndex + 1 < seriesEpisodes.size || !nextUrl.isNullOrBlank())) {
                            IconButton(
                                onClick = { playNextEpisode() },
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .testTag("player_skip_next_episode_btn")
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Siguiente Episodio",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    // Bottom Controls Area: Carátula at the start, Progress Bar, and Action Buttons below Progress Bar
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // Movies and Series VOD Controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. CARÁTULA AL PRINCIPIO DE LA BARRA DE PROGRESO
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF202020),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .testTag("player_cover_poster")
                            ) {
                                if (!currentCoverImage.isNullOrBlank()) {
                                    AsyncImage(
                                        model = currentCoverImage,
                                        contentDescription = "Carátula",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFF252525)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (kind == "series") Icons.Default.Tv else Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = NexusTextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            // 2. BARRA DE PROGRESO Y CONTROLES DEBAJO
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val activePos = if (isSeeking) (seekProgress * duration).toLong() else currentPosition
                                // Timeline Scrubber Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = formatTimeMs(activePos),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Slider(
                                        value = if (isSeeking) seekProgress else (currentPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
                                        onValueChange = {
                                            isSeeking = true
                                            seekProgress = it
                                        },
                                        onValueChangeFinished = {
                                            exoPlayer.seekTo((seekProgress * duration).toLong())
                                            isSeeking = false
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = NexusPrimary,
                                            activeTrackColor = NexusPrimary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatTimeMs(duration),
                                        color = NexusTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 3. BOTONES Y DETALLES DEBAJO DE LA BARRA DE PROGRESO
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Título / Info
                                    Text(
                                        text = currentTitle,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )

                                    // Botones de acción debajo de la barra
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Botón Idiomas y Subtítulos
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (!isSubtitlesDisabled && availableSubtitleTracks.isNotEmpty()) NexusPrimary else Color.White.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (!isSubtitlesDisabled && availableSubtitleTracks.isNotEmpty()) NexusPrimary else Color.White.copy(alpha = 0.25f)
                                            ),
                                            modifier = Modifier
                                                .clickable { showAudioSubtitlesDialog = true }
                                                .testTag("player_audio_subtitles_btn")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Subtitles,
                                                    contentDescription = "Idiomas y Subtítulos",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "Audio / Subtítulos",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Botón Expandir Pantalla Completa (Aspect Ratio)
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (currentResizeMode != ScreenResizeMode.FIT) NexusPrimary else Color.White.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (currentResizeMode != ScreenResizeMode.FIT) NexusPrimary else Color.White.copy(alpha = 0.25f)
                                            ),
                                            modifier = Modifier
                                                .clickable { cycleResizeMode() }
                                                .testTag("player_expand_fullscreen_btn")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    when (currentResizeMode) {
                                                        ScreenResizeMode.ZOOM -> Icons.Default.Fullscreen
                                                        ScreenResizeMode.FILL -> Icons.Default.AspectRatio
                                                        ScreenResizeMode.FIT -> Icons.Default.FullscreenExit
                                                    },
                                                    contentDescription = "Expandir Pantalla Completa",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = currentResizeMode.shortLabel,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Botón Siguiente Episodio (si es serie)
                                        if (kind == "series" && (currentEpisodeIndex + 1 < seriesEpisodes.size || !nextUrl.isNullOrBlank())) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color.White.copy(alpha = 0.15f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                                modifier = Modifier
                                                    .clickable { playNextEpisode() }
                                                    .testTag("player_skip_next_episode_bottom_btn")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.SkipNext,
                                                        contentDescription = "Siguiente Episodio",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = "Siguiente",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Audio, 1 = Subtítulos

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 480.dp)
            .testTag("audio_subtitles_dialog"),
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = NexusPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Audio y Subtítulos",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = NexusTextSecondary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab Selection (Audio / Subtítulos)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF222222),
                    contentColor = NexusPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Audio (${if (availableAudioTracks.isEmpty()) 1 else availableAudioTracks.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Subtítulos (${availableSubtitleTracks.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    if (selectedTab == 0) {
                        // Audio Tracks List
                        if (availableAudioTracks.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = NexusPrimary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NexusPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = NexusPrimary, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text("Audio Predeterminado (Original)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Pista de audio integrada en la transmisión", color = NexusTextSecondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(availableAudioTracks.size) { idx ->
                                    val track = availableAudioTracks[idx]
                                    val isSelected = track.isSelected
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) NexusPrimary.copy(alpha = 0.18f) else Color(0xFF222222),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) NexusPrimary else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectAudio(track) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = track.label,
                                                color = if (isSelected) Color.White else NexusTextSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = NexusPrimary, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Subtitles List
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // "Desactivados" option
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSubtitlesDisabled) NexusPrimary.copy(alpha = 0.18f) else Color(0xFF222222),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSubtitlesDisabled) NexusPrimary else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectSubtitle(null) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Desactivados",
                                            color = if (isSubtitlesDisabled) Color.White else NexusTextSecondary,
                                            fontWeight = if (isSubtitlesDisabled) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        if (isSubtitlesDisabled) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = NexusPrimary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }

                            if (availableSubtitleTracks.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No se detectaron subtítulos adicionales en este contenido",
                                            color = NexusTextSecondary,
                                            fontSize = 12.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                items(availableSubtitleTracks.size) { idx ->
                                    val track = availableSubtitleTracks[idx]
                                    val isSelected = !isSubtitlesDisabled && track.isSelected
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) NexusPrimary.copy(alpha = 0.18f) else Color(0xFF222222),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) NexusPrimary else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectSubtitle(track) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = track.label,
                                                color = if (isSelected) Color.White else NexusTextSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = NexusPrimary, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.padding(bottom = 6.dp, end = 6.dp)
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

/**
 * Broadcast TV Lower-Third "Breaking News" Style Channel Banner
 * Exact design replica of the first banner in the reference image:
 * - Slanted Red/Coral "TV LOGO" trapezoid badge on the left
 * - Slanted "LIVE ● [TIME]" pill with blinking indicator and red underline accent
 * - Dark glossy main bar with top channel title & subtitle category info
 * - Slanted right cut with vibrant red angled accent blade
 */
@Composable
fun BreakingNewsTvBanner(
    channelName: String,
    channelNumber: String,
    categoryName: String,
    channelLogoUrl: String?,
    directionLabel: String? = null,
    totalChannels: Int = 0,
    currentIndex: Int = 1,
    modifier: Modifier = Modifier
) {
    // Current live time clock formatted HH:mm
    val currentTime = remember {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date())
    }

    // Blinking pulsing animation for LIVE white dot
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Column(
        modifier = modifier
            .widthIn(min = 320.dp, max = 560.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(8.dp))
    ) {
        // Main Upper Row: Left Red TV Badge + Main Dark Bar
        Row(
            modifier = Modifier.height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. LEFT RED TV TRAPEZOID BADGE (Angled right edge)
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val skew = 14.dp.toPx()
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width - skew, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        // Red gradient background
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF2B4E),
                                    Color(0xFFE50914),
                                    Color(0xFFC70613)
                                )
                            )
                        )
                        // Top light bevel highlight
                        val topHighlight = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width - 2f, 2.5f)
                            lineTo(0f, 2.5f)
                            close()
                        }
                        drawPath(topHighlight, color = Color.White.copy(alpha = 0.35f))
                    }
                    .padding(start = 12.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!channelLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channelLogoUrl,
                            contentDescription = "Canal Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(46.dp)
                                .height(26.dp)
                        )
                        Text(
                            text = "CH $channelNumber",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    } else {
                        Text(
                            text = "TV",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CANAL $channelNumber",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // 2. MAIN DARK HORIZONTAL LOWER-THIRD BAR (With right slanted cut & red blade)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .offset(x = (-8).dp) // Seamlessly join with left badge
                    .drawBehind {
                        val skew = 18.dp.toPx()
                        val bladeWidth = 14.dp.toPx()
                        val mainWidth = size.width - bladeWidth

                        // Dark slate glossy background
                        val darkPath = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(mainWidth, 0f)
                            lineTo(mainWidth - skew, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            path = darkPath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2E303A),
                                    Color(0xFF202128),
                                    Color(0xFF141518)
                                )
                            )
                        )

                        // Top bevel highlight on dark bar
                        val topHighlight = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(mainWidth, 0f)
                            lineTo(mainWidth - 2f, 2f)
                            lineTo(0f, 2f)
                            close()
                        }
                        drawPath(topHighlight, color = Color(0xFF6B7082))

                        // Gloss reflection overlay across the lower half
                        val glossPath = Path().apply {
                            moveTo(0f, size.height * 0.48f)
                            lineTo(mainWidth - (skew * 0.48f), size.height * 0.48f)
                            lineTo(mainWidth - skew, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            path = glossPath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.05f),
                                    Color.White.copy(alpha = 0.01f)
                                )
                            )
                        )

                        // Slanted Red Accent Blade on the far right
                        val bladePath = Path().apply {
                            moveTo(mainWidth + 4.dp.toPx(), 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width - skew, size.height)
                            lineTo(mainWidth - skew + 4.dp.toPx(), size.height)
                            close()
                        }
                        drawPath(
                            path = bladePath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF2B4E),
                                    Color(0xFFE50914)
                                )
                            )
                        )
                    }
                    .padding(start = 14.dp, end = 32.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Upper Big Bold Title (Channel Name in Breaking News style)
                    Text(
                        text = channelName.uppercase(),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Lower Subtitle Line (Category / Stream details)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = categoryName.uppercase(),
                            color = Color(0xFFD4D7E2),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (totalChannels > 0) {
                            Text(
                                text = "•  $currentIndex/$totalChannels",
                                color = Color(0xFFA6ABB8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!directionLabel.isNullOrBlank()) {
                            Text(
                                text = "•  $directionLabel",
                                color = Color(0xFFFF4B6E),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Sub Row Below: Slanted LIVE & Time Pill + Underline Accent
        Row(
            modifier = Modifier
                .padding(start = 6.dp, top = 2.dp)
                .height(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LIVE pill + Time box with slant
            Row(
                modifier = Modifier
                    .drawBehind {
                        val skew = 8.dp.toPx()
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width - skew, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, color = Color.Transparent)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Red "LIVE ●" pill
                Surface(
                    shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp),
                    color = Color(0xFFFF2B4E),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = dotAlpha))
                        )
                    }
                }

                // Right White Time Box
                Surface(
                    shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp),
                    color = Color(0xFFF2F4F8),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentTime,
                            color = Color(0xFF15161A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Red Underline accent stripe running forward
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(2.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF2B4E),
                                Color(0xFFFF2B4E).copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}


