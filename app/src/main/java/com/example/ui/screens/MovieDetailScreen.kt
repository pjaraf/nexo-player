package com.example.ui.screens

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



@Composable
private fun MovieDetailTvScreen(
    movieId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateMovie: (movieId: String) -> Unit,
    onPlay: (url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit
) {
    var detail by remember { mutableStateOf<VodDetailResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(viewModel.isFavorite("movies", movieId)) }

    LaunchedEffect(movieId) {
        loading = true
        detail = XtreamApi.getVodDetail(movieId)
        isFav = viewModel.isFavorite("movies", movieId)
        loading = false
    }

    val progressList by viewModel.progressList.collectAsState()
    val savedProgress = remember(progressList, movieId) {
        progressList.find { it.kind == "movie" && it.id == movieId }
    }

    BackHandler(onBack = onBack)

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
    val directorText = info?.director ?: ""
    val genreText = info?.genre ?: ""
    val durationText = info?.duration ?: ""
    val ext = movieData?.containerExtension ?: "mp4"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .testTag("movie_detail_tv_screen")
    ) {
        // 1. Ambient Glow Layer
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.25f)
        )

        // 2. Full Uncropped Crisp Poster Art on Right
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .fillMaxWidth(0.48f)
                    .padding(end = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .shadow(24.dp, RoundedCornerShape(16.dp))
            )
        }

        // 3. Vignette Gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NexusBackground,
                            NexusBackground.copy(alpha = 0.98f),
                            NexusBackground.copy(alpha = 0.85f),
                            NexusBackground.copy(alpha = 0.40f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1300f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.70f),
                            Color.Transparent,
                            NexusBackground.copy(alpha = 0.85f),
                            NexusBackground
                        )
                    )
                )
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TvFocusBlue)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar: Back & Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = if (isBackFocused) TvFocusBlue else Color.Black.copy(alpha = 0.6f),
                        border = if (isBackFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null,
                        modifier = Modifier
                            .size(44.dp)
                            .focusable()
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .testTag("tv_movie_back_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }
                    }

                    var isFavFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = {
                            isFav = viewModel.toggleFavorite("movies", movieId, title, cover)
                        },
                        shape = CircleShape,
                        color = if (isFavFocused) TvFocusBlue else Color.Black.copy(alpha = 0.6f),
                        border = if (isFavFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null,
                        modifier = Modifier
                            .size(44.dp)
                            .focusable()
                            .onFocusChanged { isFavFocused = it.isFocused }
                            .testTag("tv_movie_fav_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (isFav) NexusPrimary else Color.White
                            )
                        }
                    }
                }

                // Middle Info Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Movie Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Badges row: Year, Rating, Duration, Genre
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (releaseYear.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NexusSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder)
                            ) {
                                Text(
                                    text = releaseYear,
                                    color = NexusTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (cleanRatingNumber.isNotBlank() && cleanRatingNumber != "0") {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF332000),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = cleanRatingNumber,
                                        color = Color(0xFFFFC107),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (durationText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NexusSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder)
                            ) {
                                Text(
                                    text = durationText,
                                    color = NexusTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (genreText.isNotBlank()) {
                            Text(
                                text = genreText.split(",").firstOrNull()?.trim() ?: genreText,
                                color = NexusTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Synopsis
                    Text(
                        text = plotText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        ),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Cast
                    if (castText.isNotBlank() && castText != "No disponible") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Reparto: ",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = castText,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = directorText,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons (Reproducir / Reanudar)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isPlayBtnFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = {
                                val streamUrl = XtreamApi.getVodStreamUrl(movieId, ext)
                                val resumeMs = savedProgress?.positionMs ?: 0L
                                if (streamUrl.isNotBlank()) {
                                    onPlay(streamUrl, title, "movie", movieId, cover, resumeMs)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPlayBtnFocused) TvFocusBlue else Color.White,
                            border = if (isPlayBtnFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null,
                            modifier = Modifier
                                .height(48.dp)
                                .focusable()
                                .onFocusChanged { isPlayBtnFocused = it.isFocused }
                                .testTag("btn_tv_movie_play")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isPlayBtnFocused) Color.White else Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = if (savedProgress != null && savedProgress.positionMs > 5000) "Reanudar Reproducción" else "Reproducir Película",
                                    color = if (isPlayBtnFocused) Color.White else Color.Black,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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

