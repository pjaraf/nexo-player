package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        if (res.second.isNotEmpty() && !res.second.containsKey(selectedSeason)) {
            selectedSeason = res.second.keys.sortedBy { it.toIntOrNull() ?: 99 }.firstOrNull() ?: "1"
        }
        isFav = viewModel.isFavorite("series", seriesId)
        loading = false
    }

    val currentEpisodes = remember(episodesMap, selectedSeason) {
        episodesMap[selectedSeason] ?: emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .testTag("series_detail_screen")
    ) {
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else {
            val title = info?.name ?: "Serie"
            val image = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                // Hero Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        AsyncImage(
                            model = image,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Transparent,
                                            NexusBackground
                                        )
                                    )
                                )
                        )
                    }
                }

                // Info
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                color = Color.White
                            )
                        )

                        // Meta Chips
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

                            info?.genre?.takeIf { it.isNotBlank() }?.let { g ->
                                Text(
                                    text = g.split(",").firstOrNull()?.trim() ?: g,
                                    color = NexusTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Synopsis
                        val plot = info?.plot ?: "Disfruta de todos los episodios completos de esta serie."
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = NexusTextSecondary,
                                lineHeight = 20.sp,
                                fontSize = 13.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Seasons Chips
                        if (episodesMap.size > 1) {
                            Text(
                                text = "Temporadas",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )

                            val sortedSeasons = episodesMap.keys.sortedBy { it.toIntOrNull() ?: 99 }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(sortedSeasons) { seasonKey ->
                                    val isSelected = seasonKey == selectedSeason
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (isSelected) Color.White else NexusSurfaceVariant,
                                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                                        modifier = Modifier
                                            .clickable { selectedSeason = seasonKey }
                                            .testTag("season_chip_$seasonKey")
                                    ) {
                                        Text(
                                            text = "Temporada $seasonKey",
                                            color = if (isSelected) Color.Black else NexusTextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Episodios (${currentEpisodes.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                // Episodes list
                items(currentEpisodes) { ep ->
                    val epIndex = currentEpisodes.indexOf(ep)
                    val nextEp = currentEpisodes.getOrNull(epIndex + 1)
                    val epImage = ep.info?.movieImage ?: image

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NexusSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .clickable {
                                val url = XtreamApi.getSeriesStreamUrl(ep.epId, ep.containerExtension ?: "mp4")
                                val nextUrl = nextEp?.let { XtreamApi.getSeriesStreamUrl(it.epId, it.containerExtension ?: "mp4") }
                                val nextTitle = nextEp?.displayTitle
                                val nextContentId = nextEp?.epId
                                val nextEpImg = nextEp?.info?.movieImage ?: image

                                onPlayEpisode(
                                    url,
                                    "${title} - T${selectedSeason}E${ep.epNumber}: ${ep.displayTitle}",
                                    "series",
                                    seriesId,
                                    epImage,
                                    0L,
                                    nextUrl,
                                    nextTitle,
                                    nextContentId,
                                    nextEpImg
                                )
                            }
                            .testTag("episode_item_${ep.epId}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(58.dp)
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
                                    modifier = Modifier
                                        .size(26.dp)
                                        .align(Alignment.Center)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "${ep.epNumber}. ${ep.displayTitle}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                ep.info?.duration?.takeIf { it.isNotBlank() }?.let { dur ->
                                    Text(
                                        text = dur,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 11.sp,
                                            color = NexusTextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top Bar
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("series_back_btn")) {
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
                            isFav = viewModel.toggleFavorite("series", seriesId, title, image)
                        },
                        modifier = Modifier.testTag("series_fav_toggle_btn")
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
