package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.models.VodStream
import com.example.ui.components.CategoryChipsRow
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.POSTER_FALLBACK
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@Composable
fun MoviesScreen(
    viewModel: MainViewModel,
    onNavigateMovie: (movieId: String) -> Unit,
    onPlayDirect: ((url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit)? = null
) {
    val categories by viewModel.vodCategories.collectAsState()
    val selectedCat by viewModel.selectedVodCat.collectAsState()
    val streams by viewModel.vodStreams.collectAsState()
    val search by viewModel.vodSearch.collectAsState()
    val loading by viewModel.vodLoading.collectAsState()

    var selectedMovie by remember { mutableStateOf<VodStream?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadVodCategories()
    }

    val chipList = remember(categories) {
        val list = mutableListOf("ALL" to "TODAS")
        list.addAll(categories.map { it.categoryId to it.categoryName })
        list
    }

    val filteredStreams = remember(streams, search) {
        if (search.isBlank()) {
            streams
        } else {
            val q = search.trim().lowercase()
            streams.filter { it.displayName.lowercase().contains(q) }
        }
    }

    // Default select first movie
    LaunchedEffect(filteredStreams) {
        if (selectedMovie == null || filteredStreams.none { it.id == selectedMovie?.id }) {
            selectedMovie = filteredStreams.firstOrNull()
        }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(NexusBackground.copy(alpha = 0.92f))
                    .statusBarsPadding()
                    .padding(top = 4.dp, bottom = 6.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Películas",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                    )
                    Text(
                        text = "${filteredStreams.size} películas",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = NexusTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = search,
                    onValueChange = { viewModel.setVodSearch(it) },
                    placeholder = { Text("Buscar películas por título...", color = NexusTextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NexusTextSecondary)
                    },
                    trailingIcon = {
                        if (search.isNotBlank()) {
                            IconButton(onClick = { viewModel.setVodSearch("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = NexusTextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexusPrimary,
                        unfocusedBorderColor = NexusBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = NexusSurfaceVariant.copy(alpha = 0.85f),
                        unfocusedContainerColor = NexusSurfaceVariant.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("movies_search_input")
                )

                // Category Chips
                CategoryChipsRow(
                    categories = chipList,
                    selectedId = selectedCat,
                    onSelect = { viewModel.selectVodCategory(it) },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        containerColor = NexusBackground,
        modifier = Modifier.testTag("movies_screen")
    ) { padding ->
        if (loading && streams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else if (filteredStreams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (search.isNotBlank()) "No se encontraron películas para \"$search\"" else "No hay títulos disponibles en esta categoría.",
                    color = NexusTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Dynamic Background Layer that changes with selected movie
                val currentPoster = selectedMovie?.streamIcon ?: POSTER_FALLBACK
                Crossfade(
                    targetState = currentPoster,
                    animationSpec = tween(durationMillis = 400),
                    label = "movie_bg_crossfade"
                ) { imageUrl ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isWideScreen) 500.dp else 360.dp)
                    ) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top & Bottom gradient shadows
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            NexusBackground.copy(alpha = 0.95f),
                                            Color.Black.copy(alpha = 0.35f),
                                            NexusBackground
                                        )
                                    )
                                )
                        )

                        // Vignette
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.85f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }

                // Foreground Content with Selected Info Header & Grid
                Column(modifier = Modifier.fillMaxSize()) {
                    // Quick Active Banner
                    selectedMovie?.let { active ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "N",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFE50914),
                                        fontFamily = FontFamily.SansSerif
                                    )
                                )
                                Text(
                                    text = "PELÍCULA",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                )
                            }

                            Text(
                                text = active.displayName,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontSize = if (isWideScreen) 26.sp else 20.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (onPlayDirect != null) {
                                            val url = XtreamApi.getVodStreamUrl(active.id, active.containerExtension ?: "mp4")
                                            onPlayDirect(url, active.displayName, "movie", active.id, active.streamIcon ?: POSTER_FALLBACK, 0L)
                                        } else {
                                            onNavigateMovie(active.id)
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reproducir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { onNavigateMovie(active.id) },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Detalles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Grid of Posters
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 105.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredStreams, key = { it.id }) { movie ->
                            val isSelected = selectedMovie?.id == movie.id
                            MediaPosterCard(
                                title = movie.displayName,
                                imageUrl = movie.streamIcon,
                                rating = movie.formattedRating,
                                badgeText = "PELÍCULA",
                                isSelected = isSelected,
                                onClick = {
                                    selectedMovie = movie
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
