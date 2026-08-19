import os

content = """package com.example.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.Episode
import com.example.data.models.SeriesDetailInfo
import com.example.ui.components.POSTER_FALLBACK
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp > 600

    if (isTv) {
        SeriesDetailTvScreen(seriesId, onBack, onPlayEpisode)
    } else {
        SeriesDetailPhoneScreen(seriesId, onBack, onPlayEpisode)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailPhoneScreen(
    seriesId: String,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }

    LaunchedEffect(seriesId) {
        loading = true
        val res = XtreamApi.getSeriesDetail(seriesId)
        info = res.first
        episodesMap = res.second
        if (res.second.isNotEmpty()) {
            selectedSeason = res.second.keys.sortedBy { it.toIntOrNull() ?: 99 }.firstOrNull() ?: "1"
        }
        loading = false
    }

    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()
    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK

    Box(modifier = Modifier.fillMaxSize().background(NexusBackground)) {
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Header (Poster + Info)
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        AsyncImage(
                            model = seriesCover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, NexusBackground),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                        )
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(top = 40.dp, start = 16.dp).align(Alignment.TopStart)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }
                    }
                }

                // Info Section
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = seriesTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = NexusText,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val rating = info?.rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }
                            if (rating != null) {
                                Box(
                                    modifier = Modifier.background(NexusPrimary, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(rating, color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            val year = info?.releaseDate?.take(4) ?: info?.releaseDateAlt?.take(4)
                            if (year != null) {
                                Text(year, color = NexusTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            info?.genre?.let {
                                Text(it, color = NexusTextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                            Text(plot, style = MaterialTheme.typography.bodyMedium, color = NexusTextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

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
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reproducir T${selectedSeason}:E${firstEp.epNumber}", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Season Selector
                        if (episodesMap.size > 1) {
                            Text("Temporadas", style = MaterialTheme.typography.titleMedium, color = NexusText, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                        Text("Episodios (${currentEpisodes.size})", style = MaterialTheme.typography.titleMedium, color = NexusText, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Episodes list
                itemsIndexed(currentEpisodes) { epIndex, ep ->
                    val nextEp = currentEpisodes.getOrNull(epIndex + 1)
                    val epImage = ep.info?.movieImage ?: seriesCover
                    val nextEpImg = nextEp?.info?.movieImage ?: seriesCover
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = epImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(120.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NexusSurface)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${ep.epNumber}. ${ep.displayTitle}",
                                color = NexusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            ep.info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(plot, color = NexusTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailTvScreen(
    seriesId: String,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }

    LaunchedEffect(seriesId) {
        loading = true
        val res = XtreamApi.getSeriesDetail(seriesId)
        info = res.first
        episodesMap = res.second
        if (res.second.isNotEmpty()) {
            selectedSeason = res.second.keys.sortedBy { it.toIntOrNull() ?: 99 }.firstOrNull() ?: "1"
        }
        loading = false
    }

    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()
    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK
    val plotText = info?.plot?.takeIf { it.isNotBlank() } ?: "Disfruta de todos los episodios."

    Box(modifier = Modifier.fillMaxSize().background(NexusBackground)) {
        // Background Image
        AsyncImage(
            model = seriesCover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().align(Alignment.TopEnd)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(NexusBackground, NexusBackground.copy(alpha = 0.9f), Color.Transparent),
                        startX = 0f,
                        endX = 1000f
                    )
                )
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusPrimary)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column - Info
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(40.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = seriesTitle,
                        style = MaterialTheme.typography.displayMedium,
                        color = NexusText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = plotText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NexusTextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(24.dp))

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
                            modifier = Modifier.height(50.dp).width(200.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reproducir", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.height(50.dp).width(200.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusSurface)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = NexusText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Volver", color = NexusText)
                    }
                }

                // Right Column - Seasons and Episodes
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(top = 40.dp, bottom = 40.dp, end = 40.dp)
                ) {
                    if (episodesMap.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(episodesMap.keys.toList().sortedBy { it.toIntOrNull() ?: 0 }) { sKey ->
                                val isSelected = sKey == selectedSeason
                                Surface(
                                    modifier = Modifier.clickable { selectedSeason = sKey },
                                    color = if (isSelected) NexusPrimary else NexusSurface,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Temporada $sKey",
                                        color = if (isSelected) Color.Black else NexusText,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(currentEpisodes) { epIndex, ep ->
                            val nextEp = currentEpisodes.getOrNull(epIndex + 1)
                            val epImage = ep.info?.movieImage ?: seriesCover
                            val nextEpImg = nextEp?.info?.movieImage ?: seriesCover

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                color = NexusSurface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = epImage,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NexusBackground)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${ep.epNumber}. ${ep.displayTitle}",
                                            color = NexusText,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            ep.info?.plot ?: "",
                                            color = NexusTextSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
"""

with open('app/src/main/java/com/example/ui/screens/SeriesDetailScreen.kt', 'w') as f:
    f.write(content)
