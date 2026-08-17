package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import android.view.KeyEvent as AndroidKeyEvent
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
    val loading by viewModel.seriesLoading.collectAsState()

    var selectedSeries by remember { mutableStateOf<SeriesItem?>(null) }
    var isCategoriesOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadSeriesCategories()
    }

    val chipList = remember(categories) {
        val l = mutableListOf("ALL" to "TODAS")
        l.addAll(categories.map { it.categoryId to it.categoryName })
        l
    }

    val selectedCategoryLabel = remember(chipList, selectedCat) {
        chipList.firstOrNull { it.first == selectedCat }?.second ?: "TODAS"
    }

    // Default select first series when list updates
    LaunchedEffect(list) {
        if (selectedSeries == null || list.none { it.id == selectedSeries?.id }) {
            selectedSeries = list.firstOrNull()
        }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_ESCAPE -> {
                            if (isCategoriesOpen) {
                                isCategoriesOpen = false
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .testTag("series_screen")
    ) {
        // Dynamic Backdrop Wallpaper
        val currentCover = selectedSeries?.cover ?: POSTER_FALLBACK
        Crossfade(
            targetState = currentCover,
            animationSpec = tween(durationMillis = 400),
            label = "series_bg_crossfade"
        ) { imageUrl ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isWideScreen) 540.dp else 400.dp)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Vertical Dark Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.35f),
                                    NexusBackground.copy(alpha = 0.95f),
                                    NexusBackground
                                )
                            )
                        )
                )

                // Vignette from Left
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

        // Main Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Hero Header with Title, Category selector button and Details action
            selectedSeries?.let { active ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF007AFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        Text(
                            text = "SERIE DE TV",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Floating Categories Button
                        var isCatBtnFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = { isCategoriesOpen = !isCategoriesOpen },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isCategoriesOpen) NexusPrimary else if (isCatBtnFocused) TvFocusBlue else Color.White.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isCatBtnFocused) 2.dp else 1.dp,
                                if (isCatBtnFocused) Color(0xFFFFC107) else if (isCategoriesOpen) NexusPrimary else Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .onFocusChanged { isCatBtnFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                            AndroidKeyEvent.KEYCODE_ENTER,
                                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                                isCategoriesOpen = !isCategoriesOpen
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .testTag("btn_toggle_series_categories")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = "Categorías",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "📂 $selectedCategoryLabel",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (isCategoriesOpen) Icons.Default.ArrowBack else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = active.displayName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = if (isWideScreen) 28.sp else 22.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isPlayBtnFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { onNavigateSeries(active.id) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayBtnFocused) TvFocusBlue else Color.White,
                                contentColor = if (isPlayBtnFocused) Color.White else Color.Black
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .onFocusChanged { isPlayBtnFocused = it.isFocused }
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isPlayBtnFocused) Color.White else Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Ver Temporadas",
                                color = if (isPlayBtnFocused) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        var isDetailBtnFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { onNavigateSeries(active.id) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDetailBtnFocused) TvFocusBlue else Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .onFocusChanged { isDetailBtnFocused = it.isFocused }
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detalles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Series Grid
            if (loading && list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NexusPrimary)
                }
            } else if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay series disponibles en esta categoría.",
                        color = NexusTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 115.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.id }) { series ->
                        val isSelected = selectedSeries?.id == series.id
                        MediaPosterCard(
                            title = series.displayName,
                            imageUrl = series.cover,
                            rating = series.formattedRating,
                            badgeText = "SERIE",
                            isSelected = isSelected,
                            onFocused = {
                                selectedSeries = series
                            },
                            onClick = {
                                onNavigateSeries(series.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Scrim background when Categories drawer is open
        if (isCategoriesOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { isCategoriesOpen = false }
            )
        }

        // Floating Vertical Categories Drawer on the Left
        AnimatedVisibility(
            visible = isCategoriesOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier
                    .padding(start = 20.dp, top = 20.dp, bottom = 20.dp)
                    .width(260.dp)
                    .fillMaxHeight()
                    .shadow(28.dp, RoundedCornerShape(24.dp))
                    .testTag("floating_series_categories_panel"),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF101018).copy(alpha = 0.97f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 18.dp)
                ) {
                    // Header of categories
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = NexusPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "CATEGORÍAS",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        IconButton(
                            onClick = { isCategoriesOpen = false },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = NexusTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Divider(
                        color = Color.White.copy(alpha = 0.1f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Vertical list of Categories
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(chipList, key = { it.first }) { (id, label) ->
                            val isCatSelected = id == selectedCat
                            var isCatFocused by remember { mutableStateOf(false) }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .onFocusChanged { state ->
                                        isCatFocused = state.isFocused
                                        if (state.isFocused) {
                                            viewModel.selectSeriesCategory(id)
                                        }
                                    }
                                    .focusable()
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                AndroidKeyEvent.KEYCODE_ENTER,
                                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                                    viewModel.selectSeriesCategory(id)
                                                    isCategoriesOpen = false
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .clickable {
                                        viewModel.selectSeriesCategory(id)
                                        isCategoriesOpen = false
                                    }
                                    .testTag("series_category_item_$id"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCatFocused) NexusPrimary
                                else if (isCatSelected) Color.White.copy(alpha = 0.15f)
                                else Color.Transparent,
                                border = if (isCatFocused) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White)
                                else if (isCatSelected) androidx.compose.foundation.BorderStroke(1.dp, NexusPrimary)
                                else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (id == "ALL") Icons.Default.AutoAwesome else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isCatFocused || isCatSelected) Color.White else NexusTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = label,
                                        color = if (isCatFocused || isCatSelected) Color.White else NexusTextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCatFocused || isCatSelected) FontWeight.Bold else FontWeight.Medium,
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
    }
}
