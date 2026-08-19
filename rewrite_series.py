import os

content = """package com.example.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.Episode
import com.example.data.models.SeriesDetailInfo
import com.example.ui.components.CinematicBackground
import com.example.ui.components.POSTER_FALLBACK
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp > 600

    if (isTv) {
        SeriesDetailTvScreen(seriesId, viewModel, onBack, onPlayEpisode)
    } else {
        SeriesDetailPhoneScreen(seriesId, viewModel, onBack, onPlayEpisode)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailTvScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }

    LaunchedEffect(seriesId) {
        loading = true
        val res = XtreamApi.getSeriesDetail(seriesId)
        info = res.first
        episodesMap = res.second
        if (res.second.isNotEmpty()) {
            selectedSeason = res.second.keys.sortedBy { it.toIntOrNull() ?: 99 }.firstOrNull() ?: "1"
            selectedEpisode = res.second[selectedSeason]?.firstOrNull()
        }
        loading = false
    }

    LaunchedEffect(selectedSeason) {
        if (!loading && episodesMap.isNotEmpty()) {
            selectedEpisode = episodesMap[selectedSeason]?.firstOrNull()
        }
    }

    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()
    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK
    val plotText = info?.plot?.takeIf { it.isNotBlank() } ?: "Disfruta de todos los episodios completos en alta definición y con la mejor calidad de audio."
    val castText = info?.cast?.takeIf { it.isNotBlank() } ?: "No disponible"
    val releaseDate = info?.releaseDate?.takeIf { it.isNotBlank() } ?: info?.releaseDateAlt?.takeIf { it.isNotBlank() } ?: ""
    val rating = info?.rating?.toString()?.takeIf { it.isNotBlank() && it != "0" }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Shared Cinematic Background from Login
        CinematicBackground()
        
        // Gradient overly to make text readable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.6f), Color.Transparent),
                        startX = 0f,
                        endX = 1200f
                    )
                )
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tvFocusBlue)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 50.dp, end = 50.dp, top = 50.dp, bottom = 20.dp)
            ) {
                // TOP SECTION: Info & Preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    // Left: Details
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Title & Rating
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = seriesTitle,
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (rating != null) {
                                Box(
                                    modifier = Modifier
                                        .background(tvFocusBlue, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(rating, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Date & Original Name
                        Text(
                            text = "$releaseDate | $seriesTitle",
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Season and Episode Label
                        Text(
                            text = "T$selectedSeason - E${selectedEpisode?.epNumber ?: "1"}",
                            color = tvFocusBlue,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Cast
                        Text(
                            text = "Actores: $castText",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Plot
                        Text(
                            text = "Sinopsis: $plotText",
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(30.dp))
                        
                        // Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = {
                                    selectedEpisode?.let { ep ->
                                        val epIndex = currentEpisodes.indexOf(ep)
                                        val nextEp = currentEpisodes.getOrNull(epIndex + 1)
                                        
                                        val url = XtreamApi.getSeriesStreamUrl(ep.epId, ep.containerExtension ?: "mp4")
                                        val nextUrl = nextEp?.let { XtreamApi.getSeriesStreamUrl(it.epId, it.containerExtension ?: "mp4") }
                                        val epImg = ep.info?.movieImage ?: seriesCover
                                        val nextEpImg = nextEp?.info?.movieImage ?: seriesCover
                                        
                                        onPlayEpisode(
                                            url,
                                            "$seriesTitle - T${selectedSeason}E${ep.epNumber}: ${ep.displayTitle}",
                                            "series",
                                            seriesId,
                                            epImg,
                                            0L,
                                            nextUrl,
                                            nextEp?.displayTitle,
                                            nextEp?.epId,
                                            nextEpImg
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pantalla completa", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Volver", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                    
                    // Right: Posters / Preview
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Series Poster
                        AsyncImage(
                            model = seriesCover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(180.dp)
                                .height(270.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        )
                        
                        Spacer(modifier = Modifier.width(20.dp))
                        
                        // Episode Preview Image
                        val epImage = selectedEpisode?.info?.movieImage ?: selectedEpisode?.info?.cover ?: seriesCover
                        Box(
                            modifier = Modifier
                                .width(380.dp)
                                .height(250.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = epImage,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // REC Indicator overlay
                            Row(
                                modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(10.dp).background(Color.Red, RoundedCornerShape(50)))
                                Text("REC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
                
                // BOTTOM SECTION: Seasons & Episodes (Horizontal)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    // Seasons
                    if (episodesMap.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(episodesMap.keys.toList().sortedBy { it.toIntOrNull() ?: 0 }) { sKey ->
                                val isSelected = sKey == selectedSeason
                                Surface(
                                    modifier = Modifier.clickable { selectedSeason = sKey },
                                    color = if (isSelected) Color.White else Color(0xFF333333),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Temporada $sKey",
                                        color = if (isSelected) Color.Black else Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    // Episodes (Numbers only like in the image)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(currentEpisodes) { ep ->
                            val isSelected = ep.epId == selectedEpisode?.epId
                            Surface(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clickable { selectedEpisode = ep },
                                color = if (isSelected) Color.White else Color(0xFF333333),
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = ep.epNumber.toString(),
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailPhoneScreen(
    seriesId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, String, String, String, Long, String?, String?, String?, String?) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
    var episodesMap by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf("1") }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }

    LaunchedEffect(seriesId) {
        loading = true
        val res = XtreamApi.getSeriesDetail(seriesId)
        info = res.first
        episodesMap = res.second
        if (res.second.isNotEmpty()) {
            selectedSeason = res.second.keys.sortedBy { it.toIntOrNull() ?: 99 }.firstOrNull() ?: "1"
            selectedEpisode = res.second[selectedSeason]?.firstOrNull()
        }
        loading = false
    }

    LaunchedEffect(selectedSeason) {
        if (!loading && episodesMap.isNotEmpty()) {
            selectedEpisode = episodesMap[selectedSeason]?.firstOrNull()
        }
    }

    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()
    val seriesTitle = info?.name ?: "Serie"
    val seriesCover = info?.cover ?: info?.backdropPath?.firstOrNull() ?: POSTER_FALLBACK

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Shared Cinematic Background
        CinematicBackground()
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f))
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tvFocusBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        AsyncImage(
                            model = selectedEpisode?.info?.movieImage ?: seriesCover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
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
                        
                        // Play Button overlaying the image
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            selectedEpisode?.let { ep ->
                                IconButton(
                                    onClick = {
                                        val epIndex = currentEpisodes.indexOf(ep)
                                        val nextEp = currentEpisodes.getOrNull(epIndex + 1)
                                        val url = XtreamApi.getSeriesStreamUrl(ep.epId, ep.containerExtension ?: "mp4")
                                        val nextUrl = nextEp?.let { XtreamApi.getSeriesStreamUrl(it.epId, it.containerExtension ?: "mp4") }
                                        
                                        onPlayEpisode(
                                            url,
                                            "$seriesTitle - T${selectedSeason}E${ep.epNumber}: ${ep.displayTitle}",
                                            "series",
                                            seriesId,
                                            ep.info?.movieImage ?: seriesCover,
                                            0L,
                                            nextUrl,
                                            nextEp?.displayTitle,
                                            nextEp?.epId,
                                            nextEp?.info?.movieImage ?: seriesCover
                                        )
                                    },
                                    modifier = Modifier.size(64.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = seriesTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "T$selectedSeason - E${selectedEpisode?.epNumber ?: ""}",
                            color = tvFocusBlue,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                            Text(plot, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (episodesMap.size > 1) {
                            Text("Temporadas", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(episodesMap.keys.toList().sortedBy { it.toIntOrNull() ?: 0 }) { sKey ->
                                    val isSelected = sKey == selectedSeason
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedSeason = sKey },
                                        label = { Text("Temporada $sKey") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color(0xFF333333),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text("Episodios", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                itemsIndexed(currentEpisodes) { epIndex, ep ->
                    val isSelected = ep.epId == selectedEpisode?.epId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { selectedEpisode = ep }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ep.epNumber.toString(),
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.width(40.dp)
                        )
                        
                        val epImage = ep.info?.movieImage ?: seriesCover
                        AsyncImage(
                            model = epImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(100.dp)
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ep.displayTitle ?: "Episodio ${ep.epNumber}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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

