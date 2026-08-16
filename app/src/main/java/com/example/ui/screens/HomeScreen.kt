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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
    onNavigateProfile: () -> Unit
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
    val isWideScreen = configuration.screenWidthDp >= 600

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
                // --- 1. Dynamic Fullscreen Backdrop Layer (Changes as user navigates Movies / Series) ---
                val currentBackdrop = selectedMedia?.backdropUrl ?: POSTER_FALLBACK
                Crossfade(
                    targetState = currentBackdrop,
                    animationSpec = tween(durationMillis = 500),
                    label = "backdrop_crossfade"
                ) { imageUrl ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isWideScreen) 580.dp else 440.dp)
                    ) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top & Bottom gradient shadows (Exact Netflix TV style)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.85f),
                                            Color.Black.copy(alpha = 0.20f),
                                            Color.Black.copy(alpha = 0.70f),
                                            NexusBackground
                                        )
                                    )
                                )
                        )

                        // Left-to-Right vignette shadow for TV clarity
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.90f),
                                            Color.Black.copy(alpha = 0.45f),
                                            Color.Transparent
                                        ),
                                        startX = 0f,
                                        endX = 1200f
                                    )
                                )
                        )
                    }
                }

                // --- 2. Main Scrollable Content ---
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Top Header Bar
                    item {
                        BrandHeader(
                            isKids = isKids,
                            profileInitial = activeProfile?.name ?: "P",
                            avatarColorHex = activeProfile?.color ?: "#E50914",
                            onAvatarClick = onNavigateProfile,
                            modifier = Modifier.statusBarsPadding()
                        )
                    }

                    // --- Dynamic Hero Details for TV & Mobile (Shows current selected title) ---
                    item {
                        val media = selectedMedia
                        if (media != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = if (isWideScreen) 32.dp else 12.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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

                                // Large Headline Title
                                Text(
                                    text = media.title,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = if (isWideScreen) 38.sp else 26.sp,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp,
                                        lineHeight = if (isWideScreen) 44.sp else 32.sp
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
                                            fontSize = if (isWideScreen) 14.sp else 12.5.sp,
                                            lineHeight = 18.sp
                                        ),
                                        maxLines = if (isWideScreen) 3 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 680.dp)
                                    )
                                }
                            }
                        }
                    }

                    // --- Tendencias ahora (Películas) ---
                    if (moviesRow.isNotEmpty()) {
                        item {
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
                                    items(moviesRow, key = { it.id }) { movie ->
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
                                                onNavigateMovie(movie.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- Series Destacadas ---
                    if (seriesRow.isNotEmpty()) {
                        item {
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
                                            }
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
