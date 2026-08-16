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
import com.example.data.models.SeriesItem
import com.example.ui.components.CategoryChipsRow
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.POSTER_FALLBACK
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@Composable
fun SeriesScreen(
    viewModel: MainViewModel,
    onNavigateSeries: (seriesId: String) -> Unit
) {
    val categories by viewModel.seriesCategories.collectAsState()
    val selectedCat by viewModel.selectedSeriesCat.collectAsState()
    val list by viewModel.seriesList.collectAsState()
    val search by viewModel.seriesSearch.collectAsState()
    val loading by viewModel.seriesLoading.collectAsState()

    var selectedSeries by remember { mutableStateOf<SeriesItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadSeriesCategories()
    }

    val chipList = remember(categories) {
        val l = mutableListOf("ALL" to "TODAS")
        l.addAll(categories.map { it.categoryId to it.categoryName })
        l
    }

    val filteredList = remember(list, search) {
        if (search.isBlank()) {
            list
        } else {
            val q = search.trim().lowercase()
            list.filter { it.displayName.lowercase().contains(q) }
        }
    }

    // Default select first series
    LaunchedEffect(filteredList) {
        if (selectedSeries == null || filteredList.none { it.id == selectedSeries?.id }) {
            selectedSeries = filteredList.firstOrNull()
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
                        text = "Series de TV",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                    )
                    Text(
                        text = "${filteredList.size} series",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = NexusTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = search,
                    onValueChange = { viewModel.setSeriesSearch(it) },
                    placeholder = { Text("Buscar series por nombre...", color = NexusTextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NexusTextSecondary)
                    },
                    trailingIcon = {
                        if (search.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSeriesSearch("") }) {
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
                        .testTag("series_search_input")
                )

                // Category Chips
                CategoryChipsRow(
                    categories = chipList,
                    selectedId = selectedCat,
                    onSelect = { viewModel.selectSeriesCategory(it) },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        containerColor = NexusBackground,
        modifier = Modifier.testTag("series_screen")
    ) { padding ->
        if (loading && list.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (search.isNotBlank()) "No se encontraron series para \"$search\"" else "No hay series disponibles en esta categoría.",
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
                // Dynamic Background Layer that updates with selected series
                val currentCover = selectedSeries?.cover ?: POSTER_FALLBACK
                Crossfade(
                    targetState = currentCover,
                    animationSpec = tween(durationMillis = 400),
                    label = "series_bg_crossfade"
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
                    selectedSeries?.let { active ->
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
                                    text = "SERIE",
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
                                    onClick = { onNavigateSeries(active.id) },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ver Episodios", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { onNavigateSeries(active.id) },
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
                        items(filteredList, key = { it.id }) { series ->
                            val isSelected = selectedSeries?.id == series.id
                            MediaPosterCard(
                                title = series.displayName,
                                imageUrl = series.cover,
                                rating = series.formattedRating,
                                badgeText = "SERIE",
                                isSelected = isSelected,
                                onClick = {
                                    selectedSeries = series
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
