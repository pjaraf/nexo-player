package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.data.models.Episode
import com.example.data.models.SeriesDetailInfo
import com.example.ui.components.POSTER_FALLBACK
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (
        url: String,
        title: String,
        kind: String,
        contentId: String,
        image: String,
        resumeMs: Long,
        nextUrl: String?,
        nextTitle: String?,
        nextContentId: String?,
        nextEpImage: String?
    ) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTvOrLandscape = configuration.screenWidthDp >= 600

    if (isTvOrLandscape) {
        SeriesDetailTvScreen(
            seriesId = seriesId,
            viewModel = viewModel,
            onBack = onBack,
            onPlayEpisode = onPlayEpisode
        )
    } else {
        SeriesDetailPhoneScreen(
            seriesId = seriesId,
            viewModel = viewModel,
            onBack = onBack,
            onPlayEpisode = onPlayEpisode
        )
    }
}

/**
 * PANTALLA ORIGINAL DE TELÉFONOS MÓVILES (Vertical / Touch)
 */
@Composable
private fun SeriesDetailPhoneScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (
        url: String,
        title: String,
        kind: String,
        contentId: String,
        image: String,
        resumeMs: Long,
        nextUrl: String?,
        nextTitle: String?,
        nextContentId: String?,
        nextEpImage: String?
    ) -> Unit
) {
    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(viewModel.isFavorite("series", seriesId)) }

    LaunchedEffect(seriesId) {
        loading = true
        val res = XtreamApi.getSeriesDetail(seriesId)
        info = res.first
        episodesMap = res.second
        if (res.second.isNotEmpty()) {
            selectedSeason = res.second.keys.firstOrNull() ?: "1"
        }
        isFav = viewModel.isFavorite("series", seriesId)
        loading = false
    }

    BackHandler(onBack = onBack)

    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()
    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .testTag("series_detail_phone_screen")
    ) {
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Backdrop
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        AsyncImage(
                            model = seriesCover,
                            contentDescription = seriesTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), NexusBackground)
                                    )
                                )
                        )

                        // Top bar buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    isFav = viewModel.toggleFavorite("series", seriesId, seriesTitle, seriesCover)
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
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

                // Info Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = seriesTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = NexusText
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Metadata row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rating = info?.rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }
                            if (rating != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = NexusPrimary.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = NexusPrimary, modifier = Modifier.size(14.dp))
                                        Text(rating.take(3), color = NexusPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            val year = info?.releaseDate?.take(4) ?: info?.releaseDateAlt?.take(4)
                            if (!year.isNullOrBlank()) {
                                Text(year, color = NexusTextSecondary, fontSize = 13.sp)
                            }

                            info?.genre?.let {
                                Text(it, color = NexusTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        // Plot
                        info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = plot,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexusTextSecondary,
                                lineHeight = 20.sp
                            )
                        }

                        // Cast
                        info?.cast?.takeIf { it.isNotBlank() }?.let { cast ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reparto: $cast",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Play first episode button
                        if (currentEpisodes.isNotEmpty()) {
                            val firstEp = currentEpisodes.first()
                            val nextEp = currentEpisodes.getOrNull(1)
                            Button(
                                onClick = {
                                    val url = XtreamApi.getSeriesStreamUrl(firstEp.epId, firstEp.containerExtension ?: "mp4")
                                    val nextUrl = nextEp?.let { XtreamApi.getSeriesStreamUrl(it.epId, it.containerExtension ?: "mp4") }
                                    val epImg = firstEp.info?.movieImage ?: seriesCover
                                    val nextEpImg = nextEp?.info?.movieImage ?: seriesCover
                                    onPlayEpisode(
                                        url,
                                        "$seriesTitle - T${selectedSeason}E${firstEp.epNumber}: ${firstEp.displayTitle}",
                                        "series",
                                        seriesId,
                                        epImg,
                                        0L,
                                        nextUrl,
                                        nextEp?.displayTitle,
                                        nextEp?.epId,
                                        nextEpImg
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reproducir T${selectedSeason}:E${firstEp.epNumber}", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Season Selector Tabs
                        if (episodesMap.size > 1) {
                            Text("Temporadas", style = MaterialTheme.typography.titleMedium, color = NexusText, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(episodesMap.keys.toList().sortedBy { it.toIntOrNull() ?: 0 }) { sKey ->
                                    val isSelected = sKey == selectedSeason
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedSeason = sKey },
                                        label = { Text("Temporada $sKey") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NexusPrimary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = NexusSurface,
                                            labelColor = NexusTextSecondary
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Episodes Header
                        Text("Episodios (${currentEpisodes.size})", style = MaterialTheme.typography.titleMedium, color = NexusText, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Episode list
                items(currentEpisodes) { ep ->
                    val epIndex = currentEpisodes.indexOf(ep)
                    val nextEp = currentEpisodes.getOrNull(epIndex + 1)
                    val epImage = ep.info?.movieImage ?: seriesCover
                    val nextEpImg = nextEp?.info?.movieImage ?: seriesCover

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val url = XtreamApi.getSeriesStreamUrl(ep.epId, ep.containerExtension ?: "mp4")
                                val nextUrl = nextEp?.let { XtreamApi.getSeriesStreamUrl(it.epId, it.containerExtension ?: "mp4") }
                                onPlayEpisode(
                                    url,
                                    "$seriesTitle - T${selectedSeason}E${ep.epNumber}: ${ep.displayTitle}",
                                    "series",
                                    seriesId,
                                    epImage,
                                    0L,
                                    nextUrl,
                                    nextEp?.displayTitle,
                                    nextEp?.epId,
                                    nextEpImg
                                )
                            },
                        color = NexusSurface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp, 50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = epImage,
                                    contentDescription = ep.displayTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${ep.epNumber}. ${ep.displayTitle}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = NexusText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                ep.info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                                    Text(
                                        text = plot,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NexusTextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ep.info?.duration?.takeIf { it.isNotBlank() }?.let { duration ->
                                    Text(
                                        text = duration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusTextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * PANTALLA EXCLUSIVA PARA TELEVISORES (Android TV / Pantallas Grandes)
 * Basada fielmente en el video con Mini-Reproductor y Carrusel de Números.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SeriesDetailTvScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (
        url: String,
        title: String,
        kind: String,
        contentId: String,
        image: String,
        resumeMs: Long,
        nextUrl: String?,
        nextTitle: String?,
        nextContentId: String?,
        nextEpImage: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(viewModel.isFavorite("series", seriesId)) }

    // Dialogs & Expanded states
    var showSeasonPicker by remember { mutableStateOf(false) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    // Mini ExoPlayer for embedded preview
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var isPreviewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }

    // Audio & Subtitle tracks for the embedded preview
    var availableAudioTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var availableSubtitleTracks by remember { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var isSubtitlesDisabled by remember { mutableStateOf(false) }

    // Stop playback when activity leaves foreground / dispose
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                    exoPlayer.playWhenReady = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.playWhenReady = true
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

    // ExoPlayer event listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isPreviewLoading = true
                    Player.STATE_READY -> {
                        isPreviewLoading = false
                        previewError = false
                    }
                    Player.STATE_ENDED -> isPreviewLoading = false
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("SeriesDetailTv", "Preview player error: ${error.message}")
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
        isFav = viewModel.isFavorite("series", seriesId)
        loading = false
    }

    val currentEpisodes = remember(episodesMap, selectedSeason) {
        episodesMap[selectedSeason] ?: emptyList()
    }

    // When current season episodes update, ensure selectedEpisode is valid
    LaunchedEffect(currentEpisodes) {
        if (selectedEpisode == null || currentEpisodes.none { it.epId == selectedEpisode?.epId }) {
            selectedEpisode = currentEpisodes.firstOrNull()
        }
    }

    // Update mini preview player when selected episode changes
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
                }
            } catch (e: Exception) {
                Log.e("SeriesDetailTv", "Error playing preview for ${ep.displayTitle}", e)
                isPreviewLoading = false
                previewError = true
            }
        }
    }

    // Function to launch full screen playback for the currently selected episode
    fun launchFullScreen() {
        val ep = selectedEpisode ?: currentEpisodes.firstOrNull() ?: return
        val epIndex = currentEpisodes.indexOf(ep)
        val nextEp = currentEpisodes.getOrNull(epIndex + 1)
        val image = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK
        val epImage = ep.info?.movieImage ?: image

        val url = XtreamApi.getSeriesStreamUrl(ep.epId, ep.containerExtension ?: "mp4")
        val nextUrl = nextEp?.let { XtreamApi.getSeriesStreamUrl(it.epId, it.containerExtension ?: "mp4") }
        val nextTitle = nextEp?.displayTitle
        val nextContentId = nextEp?.epId
        val nextEpImg = nextEp?.info?.movieImage ?: image

        val seriesTitle = info?.name ?: "Serie"
        val resumeMs = exoPlayer.currentPosition.coerceAtLeast(0L)

        // Pause preview before navigating to full screen
        try {
            exoPlayer.pause()
        } catch (_: Exception) {}

        onPlayEpisode(
            url,
            "$seriesTitle - T${selectedSeason}E${ep.epNumber}: ${ep.displayTitle}",
            "series",
            seriesId,
            epImage,
            resumeMs,
            nextUrl,
            nextTitle,
            nextContentId,
            nextEpImg
        )
    }

    BackHandler(onBack = onBack)

    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK
    val releaseYear = info?.releaseDate?.takeIf { it.isNotBlank() } ?: info?.releaseDateAlt?.takeIf { it.isNotBlank() } ?: ""
    val rawRating = info?.rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }
    val displayRating = rawRating?.take(3) ?: "7.0"
    val cleanRatingNumber = displayRating.replace(".0", "").replace(",", ".").toDoubleOrNull()?.toInt()?.toString() ?: "7"
    val castText = info?.cast?.takeIf { it.isNotBlank() } ?: "Sebastián Martínez, Juliette Pardau, Hanny Vizcaíno, Carlos Camacho, Laura de León"
    val plotText = info?.plot?.takeIf { it.isNotBlank() } ?: "Disfruta de todos los episodios completos en alta definición y con la mejor calidad de audio."

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .testTag("series_detail_tv_screen")
    ) {
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
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                // Top Navigation Bar (Back and Favorite button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.testTag("series_back_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Volver", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        onClick = {
                            isFav = viewModel.toggleFavorite("series", seriesId, seriesTitle, seriesCover)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.testTag("series_fav_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (isFav) NexusPrimary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(if (isFav) "Guardado" else "Añadir a Favoritos", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Main Info & Video Row (Top Half Layout matching reference video)
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
                            // Primary Orange Button "Pantalla completa"
                            var isFullBtnFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { launchFullScreen() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800),
                                    contentColor = Color.White
                                ),
                                border = if (isFullBtnFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null,
                                modifier = Modifier
                                    .height(42.dp)
                                    .focusable()
                                    .onFocusChanged { isFullBtnFocused = it.isFocused }
                                    .testTag("btn_series_fullscreen")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pantalla completa",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Secondary Translucent Pill Button "Idioma y subtítulos"
                            var isTracksBtnFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { showTracksDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.18f),
                                border = if (isTracksBtnFocused) {
                                    androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                } else {
                                    androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(210.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .clickable { launchFullScreen() }
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
                                CircularProgressIndicator(color = Color(0xFFFF9800), modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
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
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Season and Range pill selectors
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Season Picker Button
                        var isSeasonBtnFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = {
                                if (episodesMap.size > 1) {
                                    showSeasonPicker = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E202B),
                            border = if (isSeasonBtnFocused) {
                                androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                            } else {
                                androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
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
                                color = Color.White.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
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

                    // Horizontal Row of Numbered Episode Blocks (like [ ▶ 1 ], [ 2 ], [ 3 ], etc.)
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
                                            if (state.isFocused) {
                                                selectedEpisode = ep
                                            }
                                        }
                                        .clickable {
                                            if (isSelected) {
                                                launchFullScreen()
                                            } else {
                                                selectedEpisode = ep
                                            }
                                        }
                                        .testTag("episode_block_${ep.epNumber}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) {
                                        Color(0xFF007AFF) // Vibrant Blue active block
                                    } else if (isFocused) {
                                        Color.White.copy(alpha = 0.28f)
                                    } else {
                                        Color.White.copy(alpha = 0.12f)
                                    },
                                    border = if (isFocused) {
                                        androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                    } else if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF64B5F6))
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
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
                                color = if (isRowFocused) Color(0xFFFF9800) else if (isCurrent) Color(0xFF007AFF) else Color.White.copy(alpha = 0.1f),
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
                        Text("Cerrar", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
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
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
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
                                    color = if (track.isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.1f)
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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
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
                            color = if (isSubtitlesDisabled) Color(0xFF007AFF) else Color.White.copy(alpha = 0.1f)
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
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
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
                                color = if (track.isSelected && !isSubtitlesDisabled) Color(0xFF007AFF) else Color.White.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(track.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    if (track.isSelected && !isSubtitlesDisabled) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTracksDialog = false }) {
                        Text("Aceptar", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
