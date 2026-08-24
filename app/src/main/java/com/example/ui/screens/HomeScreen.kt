package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.LiveChannel
import com.example.data.models.SeriesItem
import com.example.data.models.VodStream
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils

// Data class to unify selected active highlight across Movies & Series
data class FocusedMedia(
    val id: String,
    val title: String,
    val description: String?,
    val backdropUrl: String?,
    val rating: String?,
    val kind: String, // "movie", "series", "live"
    val year: String? = null,
    val genre: String? = null,
    val containerExtension: String? = "mp4"
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateLive: (channelId: String) -> Unit,
    onNavigateMovie: (movieId: String) -> Unit,
    onNavigateSeries: (seriesId: String) -> Unit,
    onPlayDirect: (url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit,
    onNavigateProfile: () -> Unit,
    isSidebarOpen: Boolean = false,
    onFirstItemFocused: (Boolean) -> Unit = {}
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val isKids by viewModel.isKidsMode.collectAsState()
    val heroItem by viewModel.homeHeroItem.collectAsState()
    val liveRow by viewModel.homeLiveRow.collectAsState()
    val moviesRow by viewModel.homeMoviesRow.collectAsState()
    val seriesRow by viewModel.homeSeriesRow.collectAsState()
    val progressList by viewModel.progressList.collectAsState()
    val loading by viewModel.homeLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHomeContent()
    }

    // Dynamic highlighted media state that changes as user hovers/taps any Movie or Series
    var selectedMedia by remember { mutableStateOf<FocusedMedia?>(null) }

    // Synchronize default hero when data arrives
    LaunchedEffect(heroItem, moviesRow, seriesRow) {
        if (selectedMedia == null) {
            val hero = heroItem ?: moviesRow.firstOrNull()
            if (hero != null) {
                selectedMedia = FocusedMedia(
                    id = hero.id,
                    title = hero.displayName,
                    description = "Disfruta de este título exclusivo en máxima calidad.",
                    backdropUrl = hero.streamIcon ?: POSTER_FALLBACK,
                    rating = hero.formattedRating,
                    kind = "movie",
                    containerExtension = hero.containerExtension ?: "mp4"
                )
            } else if (seriesRow.isNotEmpty()) {
                val firstSeries = seriesRow.first()
                selectedMedia = FocusedMedia(
                    id = firstSeries.id,
                    title = firstSeries.displayName,
                    description = firstSeries.plot ?: "Serie completa disponible con todos sus episodios y temporadas.",
                    backdropUrl = firstSeries.cover ?: POSTER_FALLBACK,
                    rating = firstSeries.formattedRating,
                    kind = "series"
                )
            }
        }
    }

    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    val isWideScreen = configuration.screenWidthDp >= 600 || isTv
    var pendingResumeItem by remember { mutableStateOf<Triple<String, String, com.example.data.models.ProgressItem>?>(null) }

    val firstCardFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(moviesRow, isTv, isSidebarOpen) {
        if (isTv && !isSidebarOpen && moviesRow.isNotEmpty() && !hasRequestedInitialFocus) {
            delay(200)
            try {
                firstCardFocusRequester.requestFocus()
                hasRequestedInitialFocus = true
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Scaffold(
        containerColor = NexusBackground,
        modifier = Modifier.testTag("home_screen")
    ) { padding ->
        if (loading && moviesRow.isEmpty() && liveRow.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // --- 1. Background Layer: Use CinematicBackground (same as Login screen) ---
                CinematicBackground()

                // --- 2. Main Scrollable Content ---
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Header Bar
                    BrandHeader(
                        isKids = isKids,
                        profileInitial = activeProfile?.name ?: "P",
                        avatarColorHex = activeProfile?.color ?: "#E50914",
                        onAvatarClick = onNavigateProfile,
                        modifier = Modifier.statusBarsPadding()
                    )

                    // --- Dynamic Hero Details for TV & Mobile (Shows current selected title or default greeting) ---
                    val media = selectedMedia
                    if (media != null) {
                        if (isWideScreen || isTv) {
                            // TV side-by-side Hero: Info on Left, Complete Full Poster on Right
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 36.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Badge (N SERIES / N PELICULA)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "N",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFFE50914),
                                                fontFamily = FontFamily.SansSerif,
                                                fontSize = 22.sp
                                            )
                                        )
                                        Text(
                                            text = if (media.kind == "series") "SERIE" else "PELÍCULA",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 3.sp,
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 12.sp
                                            )
                                        )

                                        if (!media.rating.isNullOrBlank() && media.rating != "0") {
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                                modifier = Modifier.padding(start = 6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                                                    Text(text = media.rating.take(3), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // Large Headline Title
                                    Text(
                                        text = media.title,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 32.sp,
                                            color = Color.White,
                                            letterSpacing = 0.5.sp,
                                            lineHeight = 38.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Plot Description Synopsis
                                    if (!media.description.isNullOrBlank()) {
                                        Text(
                                            text = media.description,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White.copy(alpha = 0.88f),
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp
                                            ),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 680.dp)
                                        )
                                    }
                                }

                                // Right side: Full complete vertical cover of the focused movie/series
                                val posterUrl = media.backdropUrl?.takeIf { it.isNotBlank() } ?: POSTER_FALLBACK
                                Crossfade(
                                    targetState = posterUrl,
                                    animationSpec = tween(durationMillis = 300),
                                    label = "tv_hero_poster_crossfade"
                                ) { targetPosterUrl ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF14141E),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f)),
                                        shadowElevation = 10.dp,
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(235.dp)
                                            .padding(end = 16.dp)
                                    ) {
                                        AsyncImage(
                                            model = targetPosterUrl,
                                            contentDescription = media.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        } else {
                            // Mobile layout: Single column
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 12.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "N",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFE50914),
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 20.sp
                                        )
                                    )
                                    Text(
                                        text = if (media.kind == "series") "SERIE" else "PELÍCULA",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 3.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 11.sp
                                        )
                                    )

                                    if (!media.rating.isNullOrBlank() && media.rating != "0") {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                            modifier = Modifier.padding(start = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                                                Text(text = media.rating.take(3), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = media.title,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 26.sp,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp,
                                        lineHeight = 32.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!media.description.isNullOrBlank()) {
                                    Text(
                                        text = media.description,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White.copy(alpha = 0.88f),
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        // Empty/Welcome Hero when there is no content loaded
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = if (isWideScreen) 32.dp else 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "N",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFE50914),
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 24.sp
                                    )
                                )
                                Text(
                                    text = "NEXO PLAYER",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 3.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                            Text(
                                text = "Entretenimiento Ilimitado",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = if (isWideScreen) 36.sp else 24.sp,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Utiliza el menú lateral para acceder a la guía de Canales de TV en Vivo, Películas, Series o la configuración de tu Perfil.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = if (isWideScreen) 15.sp else 13.sp,
                                    lineHeight = 20.sp
                                ),
                                modifier = Modifier.widthIn(max = 640.dp)
                            )
                        }
                    }

                    // --- Tendencias ahora (Películas) ---
                    if (moviesRow.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isKids) "Películas Familiares" else "Tendencias ahora • Películas",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 17.sp
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(moviesRow, key = { _, it -> it.id }) { index, movie ->
                                    val isSelected = selectedMedia?.id == movie.id
                                    MediaPosterCard(
                                        title = movie.displayName,
                                        imageUrl = movie.streamIcon,
                                        rating = movie.formattedRating,
                                        badgeText = null,
                                        isSelected = isSelected,
                                        onFocused = {
                                            selectedMedia = FocusedMedia(
                                                id = movie.id,
                                                title = movie.displayName,
                                                description = "Película disponible en Nexo.",
                                                backdropUrl = movie.streamIcon ?: POSTER_FALLBACK,
                                                rating = movie.formattedRating,
                                                kind = "movie",
                                                containerExtension = movie.containerExtension ?: "mp4"
                                            )
                                        },
                                        onClick = {
                                            selectedMedia = FocusedMedia(
                                                id = movie.id,
                                                title = movie.displayName,
                                                description = "Película disponible en Nexo.",
                                                backdropUrl = movie.streamIcon ?: POSTER_FALLBACK,
                                                rating = movie.formattedRating,
                                                kind = "movie",
                                                containerExtension = movie.containerExtension ?: "mp4"
                                            )
                                            val savedProgress = progressList.find { it.key == "movie:${movie.id}" || (it.kind == "movie" && it.id == movie.id) }
                                            if (savedProgress != null && savedProgress.positionMs > 10_000L && (savedProgress.durationMs <= 0L || savedProgress.positionMs < (savedProgress.durationMs - 30_000L))) {
                                                pendingResumeItem = Triple(movie.displayName, movie.streamIcon ?: "", savedProgress)
                                            } else {
                                                val streamUrl = XtreamApi.getVodStreamUrl(movie.id, movie.containerExtension ?: "mp4")
                                                onPlayDirect(streamUrl, movie.displayName, "movie", movie.id, movie.streamIcon ?: "", 0L)
                                            }
                                        },
                                        modifier = Modifier
                                            .width(if (isWideScreen) 130.dp else 115.dp)
                                            .then(if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Resume Dialog for Home
    pendingResumeItem?.let { (title, cover, progress) ->
        val streamUrl = if (progress.kind == "movie") {
            XtreamApi.getVodStreamUrl(progress.id, "mp4")
        } else {
            XtreamApi.getSeriesStreamUrl(progress.id, "mp4")
        }
        ResumePlaybackDialog(
            title = title,
            coverUrl = cover,
            positionMs = progress.positionMs,
            durationMs = progress.durationMs,
            onResume = {
                val pos = progress.positionMs
                val pKind = progress.kind
                val pId = progress.id
                pendingResumeItem = null
                onPlayDirect(streamUrl, title, pKind, pId, cover, pos)
            },
            onStartFromBeginning = {
                val pKind = progress.kind
                val pId = progress.id
                pendingResumeItem = null
                onPlayDirect(streamUrl, title, pKind, pId, cover, 0L)
            },
            onDismiss = {
                pendingResumeItem = null
            }
        )
    }

    // --- Series Destacadas (Solo en Teléfonos) ---
    if (seriesRow.isNotEmpty() && !isTv) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isKids) "Series Infantiles" else "Series Destacadas",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 17.sp
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(seriesRow, key = { it.id }) { series ->
                    val isSelected = selectedMedia?.id == series.id
                    MediaPosterCard(
                        title = series.displayName,
                        imageUrl = series.cover,
                        rating = series.formattedRating,
                        badgeText = null,
                        isSelected = isSelected,
                        onFocused = {
                            selectedMedia = FocusedMedia(
                                id = series.id,
                                title = series.displayName,
                                description = series.plot ?: "Serie completa en alta definición.",
                                backdropUrl = series.cover ?: POSTER_FALLBACK,
                                rating = series.formattedRating,
                                kind = "series"
                            )
                        },
                        onClick = {
                            selectedMedia = FocusedMedia(
                                id = series.id,
                                title = series.displayName,
                                description = series.plot ?: "Serie completa en alta definición.",
                                backdropUrl = series.cover ?: POSTER_FALLBACK,
                                rating = series.formattedRating,
                                kind = "series"
                            )
                            onNavigateSeries(series.id)
                        },
                        modifier = Modifier.width(if (isWideScreen) 130.dp else 115.dp)
                    )
                }
            }
        }
    }
}
