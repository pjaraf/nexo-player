package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CategoryChipsRow
import com.example.ui.components.MediaPosterCard
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateMovie: (movieId: String) -> Unit,
    onNavigateSeries: (seriesId: String) -> Unit,
    onPlayLive: (channelId: String, title: String) -> Unit
) {
    val favorites by viewModel.favoritesList.collectAsState()
    var selectedTab by remember { mutableStateOf("all") }

    val tabs = listOf(
        "all" to "Todos",
        "movies" to "Películas",
        "series" to "Series",
        "live" to "En Vivo"
    )

    val filteredList = remember(favorites, selectedTab) {
        if (selectedTab == "all") favorites else favorites.filter { it.kind == selectedTab }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mis Favoritos",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("favorites_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NexusBackground,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = NexusBackground,
        modifier = Modifier.testTag("favorites_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Tabs
            CategoryChipsRow(
                categories = tabs,
                selectedId = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = NexusTextMuted,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "Aún no tienes favoritos guardados.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Toca el ícono de corazón en cualquier película, serie o canal de TV para agregarlo a esta lista.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NexusTextSecondary,
                                fontSize = 13.sp
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { "${it.kind}_${it.id}" }) { item ->
                        val badge = when (item.kind) {
                            "movies" -> "PELÍCULA"
                            "series" -> "SERIE"
                            "live" -> "EN VIVO"
                            else -> null
                        }

                        MediaPosterCard(
                            title = item.title,
                            imageUrl = item.image,
                            badgeText = badge,
                            onClick = {
                                when (item.kind) {
                                    "movies" -> onNavigateMovie(item.id)
                                    "series" -> onNavigateSeries(item.id)
                                    "live" -> onPlayLive(item.id, item.title)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
