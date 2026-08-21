package com.example.ui.screens

import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.LiveCategory
import com.example.data.models.LiveChannel
import com.example.player.PlayerManager
import com.example.player.VlcPlayerView
import com.example.ui.components.CHANNEL_FALLBACK
import com.example.ui.components.ScreenCastDialog
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Color palette matching the JetGo layout
private val JetOrange = Color(0xFFDE5B17)
private val JetOrangeBright = Color(0xFFFF7A00)
private val JetSidebarBg = Color(0xFF141414)
private val JetBackground = Color(0xFF0D0D0D)
private val JetCardBg = Color(0xFF1A1A1A)
private val JetTextMuted = Color(0xFF888888)

enum class LiveSubTab {
    CATEGORIA,
    FAVORITOS
}

/**
 * PANTALLA UNIFICADA DE TV EN VIVO (Móvil y Smart TV / TV Box)
 * - Mismo diseño robusto, estable y sin cierres inesperados
 * - Soporte 100% interactivo para control remoto D-Pad (resaltado de foco, OK, Flechas)
 * - Mini-reproductor VLC en 16:9 con preview en tiempo real y transición fluida a pantalla completa
 */
@Composable
fun LiveScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit,
    onExitToMenu: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    val coroutineScope = rememberCoroutineScope()

    val categories by viewModel.liveCategories.collectAsState()
    val selectedCat by viewModel.selectedLiveCat.collectAsState()
    val channels by viewModel.liveChannels.collectAsState()
    val search by viewModel.liveSearch.collectAsState()
    val loading by viewModel.liveLoading.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()

    var activeTab by remember { mutableStateOf(LiveSubTab.CATEGORIA) }
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var isPlayerBuffering by remember { mutableStateOf(false) }
    var playerHasError by remember { mutableStateOf(false) }
    var showScreenCastDialog by remember { mutableStateOf(false) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var channelCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Embedded VLC Instance
    val playerManager = remember { PlayerManager(context) }

    DisposableEffect(playerManager) {
        playerManager.setAspectRatio("16:9")
        playerManager.setScale(0f)
        playerManager.onBuffering = { buffering, _ ->
            isPlayerBuffering = buffering
        }
        playerManager.onPlayingChanged = { playing ->
            if (playing) {
                isPlayerBuffering = false
                playerHasError = false
            }
        }
        playerManager.onError = { error ->
            Log.w("LiveScreen", "VLC Error en candidato $candidateIndex: $error")
            if (candidateIndex + 1 < channelCandidates.size) {
                candidateIndex++
                val nextCandidate = channelCandidates[candidateIndex]
                try {
                    isPlayerBuffering = true
                    playerManager.play(nextCandidate, 0L)
                } catch (_: Throwable) {
                    playerHasError = true
                    isPlayerBuffering = false
                }
            } else {
                playerHasError = true
                isPlayerBuffering = false
            }
        }

        onDispose {
            playerManager.release()
        }
    }

    // Play selected channel inside preview with debounce
    LaunchedEffect(selectedChannel, retryTrigger) {
        val ch = selectedChannel
        if (ch != null) {
            isPlayerBuffering = true
            playerHasError = false
            candidateIndex = 0

            val rawCandidates = XtreamApi.getLiveStreamCandidates(ch.id)
            val list = mutableListOf<String>()
            ch.directStreamUrl?.takeIf { it.isNotBlank() }?.let { list.add(it) }
            for (c in rawCandidates) {
                if (!list.contains(c)) list.add(c)
            }
            if (list.isEmpty()) {
                val single = XtreamApi.getLiveStreamUrl(ch.id)
                if (single.isNotBlank()) list.add(single)
            }
            channelCandidates = list

            delay(180)

            if (list.isNotEmpty()) {
                try {
                    playerHasError = false
                    isPlayerBuffering = true
                    playerManager.play(list[0], 0L)
                } catch (e: Throwable) {
                    if (list.size > 1) {
                        candidateIndex = 1
                        try {
                            playerManager.play(list[1], 0L)
                        } catch (_: Throwable) {
                            playerHasError = true
                            isPlayerBuffering = false
                        }
                    } else {
                        playerHasError = true
                        isPlayerBuffering = false
                    }
                }
            } else {
                playerHasError = true
                isPlayerBuffering = false
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLiveCategories()
    }

    // Filter channels according to search
    val filteredChannels = remember(channels, search) {
        if (search.isBlank()) {
            channels
        } else {
            val q = search.trim().lowercase()
            channels.filter { it.name.lowercase().contains(q) || it.groupName.lowercase().contains(q) }
        }
    }

    // Favorite channels list
    val favoriteChannels = remember(favorites, channels) {
        val favIds = favorites.filter { it.kind == "live" }.map { it.id }.toSet()
        channels.filter { favIds.contains(it.id) }
    }

    // Select first channel automatically when data loads
    LaunchedEffect(filteredChannels) {
        if (selectedChannel == null && filteredChannels.isNotEmpty()) {
            selectedChannel = filteredChannels.first()
        }
    }

    val channelListState = rememberLazyListState()

    // Manejador del botón Atrás
    BackHandler(enabled = true) {
        if (search.isNotBlank()) {
            viewModel.setLiveSearch("")
        } else if (activeTab == LiveSubTab.FAVORITOS) {
            activeTab = LiveSubTab.CATEGORIA
        } else {
            onExitToMenu?.invoke()
        }
    }

    Scaffold(
        containerColor = JetBackground,
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            // --- 1. Top Search Bar (Búsqueda por canal) ---
            var isSearchFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.setLiveSearch(it) },
                placeholder = {
                    Text(
                        text = "Búsqueda por canal",
                        color = JetTextMuted,
                        fontSize = if (isTv) 15.sp else 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = if (isSearchFocused) JetOrangeBright else JetTextMuted,
                        modifier = Modifier.size(if (isTv) 22.dp else 20.dp)
                    )
                },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { viewModel.setLiveSearch("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = JetTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JetOrange,
                    unfocusedBorderColor = if (isSearchFocused) Color.White else Color(0xFF2A2A2A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = JetCardBg,
                    unfocusedContainerColor = JetCardBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (isTv) 20.dp else 14.dp, end = if (isTv) 20.dp else 14.dp, top = 4.dp, bottom = 4.dp)
                    .height(if (isTv) 52.dp else 48.dp)
                    .onFocusChanged { isSearchFocused = it.isFocused }
                    .testTag("live_search_input")
            )

            // --- 2. Live Video Player Preview Box (Formato 16:9 ajustado) ---
            var isPlayerBoxFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTv) 20.dp else 14.dp, vertical = 2.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(
                        if (isPlayerBoxFocused) 2.dp else 1.dp,
                        if (isPlayerBoxFocused) Color.White else Color(0xFF262626),
                        RoundedCornerShape(14.dp)
                    )
                    .onFocusChanged { isPlayerBoxFocused = it.isFocused }
                    .focusable()
                    .clickable {
                        selectedChannel?.let { ch ->
                            onPlayChannel(ch.id, selectedCat, ch.name)
                        }
                    }
                    .testTag("live_player_preview_box"),
                contentAlignment = Alignment.Center
            ) {
                // Video Surface VLC
                VlcPlayerView(
                    playerManager = playerManager,
                    modifier = Modifier.fillMaxSize()
                )

                // Buffering indicator
                if (isPlayerBuffering) {
                    CircularProgressIndicator(
                        color = JetOrange,
                        strokeWidth = 3.dp,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }

                // Error text
                if (playerHasError && !isPlayerBuffering) {
                    Text(
                        text = "Canal no disponible",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                // Control Buttons (Pantalla completa y Transmitir)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Transmitir a TV
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape,
                    ) {
                        IconButton(
                            onClick = { showScreenCastDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("live_cast_screen_btn")
                        ) {
                            Icon(
                                Icons.Default.Cast,
                                contentDescription = "Transmitir a TV",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Botón Expandir a Pantalla Completa
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape,
                    ) {
                        IconButton(
                            onClick = {
                                selectedChannel?.let { ch ->
                                    onPlayChannel(ch.id, selectedCat, ch.name)
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("live_fullscreen_btn")
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Pantalla completa",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // --- 3. Sub Tabs: CATEGORÍA & FAVORITOS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTv) 20.dp else 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // CATEGORÍA Button
                var isCatTabFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { activeTab = LiveSubTab.CATEGORIA },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == LiveSubTab.CATEGORIA) JetOrange else Color(0xFF202020),
                        contentColor = Color.White
                    ),
                    border = if (isCatTabFocused) BorderStroke(2.dp, Color.White) else null,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isTv) 44.dp else 38.dp)
                        .onFocusChanged { isCatTabFocused = it.isFocused }
                        .testTag("tab_categoria")
                ) {
                    Text(
                        text = "CATEGORÍA",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isTv) 13.sp else 12.sp
                    )
                }

                // FAVORITOS Button
                var isFavTabFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { activeTab = LiveSubTab.FAVORITOS },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == LiveSubTab.FAVORITOS) JetOrange else Color(0xFF202020),
                        contentColor = Color.White
                    ),
                    border = if (isFavTabFocused) BorderStroke(2.dp, Color.White) else null,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isTv) 44.dp else 38.dp)
                        .onFocusChanged { isFavTabFocused = it.isFocused }
                        .testTag("tab_favoritos")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Text(
                            text = "FAVORITOS (${favoriteChannels.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isTv) 13.sp else 12.sp
                        )
                    }
                }
            }

            // --- 4. Main Content Area (Sidebar + Channels or Favorites) ---
            if (activeTab == LiveSubTab.CATEGORIA) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // --- 4A. Left Sidebar: Categories list ---
                    Surface(
                        color = JetSidebarBg,
                        modifier = Modifier
                            .width(if (isTv) 160.dp else 130.dp)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            // "TODOS LOS CANALES" Category item
                            item {
                                val isSelected = selectedCat == "ALL"
                                var isItemFocused by remember { mutableStateOf(false) }
                                Surface(
                                    color = if (isSelected) JetOrange else if (isItemFocused) Color(0xFF333344) else Color.Transparent,
                                    border = if (isItemFocused) BorderStroke(2.dp, Color.White) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isItemFocused = it.isFocused }
                                        .focusable()
                                        .clickable { viewModel.selectLiveCategory("ALL") }
                                        .testTag("live_cat_all")
                                ) {
                                    Text(
                                        text = "TODOS",
                                        color = if (isSelected || isItemFocused) Color.White else JetTextMuted,
                                        fontSize = if (isTv) 12.5.sp else 11.5.sp,
                                        fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = if (isTv) 12.dp else 10.dp)
                                    )
                                }
                            }

                            // Dynamic categories from server
                            itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                                val isSelected = selectedCat == cat.categoryId
                                var isItemFocused by remember { mutableStateOf(false) }
                                Surface(
                                    color = if (isSelected) JetOrange else if (isItemFocused) Color(0xFF333344) else Color.Transparent,
                                    border = if (isItemFocused) BorderStroke(2.dp, Color.White) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isItemFocused = it.isFocused }
                                        .focusable()
                                        .clickable { viewModel.selectLiveCategory(cat.categoryId) }
                                        .testTag("live_cat_${cat.categoryId}")
                                ) {
                                    Text(
                                        text = cat.categoryName.uppercase(),
                                        color = if (isSelected || isItemFocused) Color.White else JetTextMuted,
                                        fontSize = if (isTv) 12.5.sp else 11.5.sp,
                                        fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = if (isTv) 12.dp else 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // --- 4B. Right List: Channels ---
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(JetBackground)
                    ) {
                        if (loading && channels.isEmpty()) {
                            CircularProgressIndicator(
                                color = JetOrange,
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center)
                            )
                        } else if (filteredChannels.isEmpty()) {
                            Text(
                                text = if (search.isNotBlank()) "Sin canales para \"$search\"" else "No hay canales",
                                color = JetTextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                            )
                        } else {
                            LazyColumn(
                                state = channelListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(filteredChannels, key = { index, ch -> "${ch.id}_$index" }) { index, channel ->
                                    val isSelected = selectedChannel?.id == channel.id
                                    val isFav = viewModel.isFavorite("live", channel.id)

                                    ChannelListItemJetGo(
                                        index = index + 1,
                                        channel = channel,
                                        isSelected = isSelected,
                                        isFav = isFav,
                                        isTv = isTv,
                                        onFavToggle = {
                                            viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon)
                                        },
                                        onClick = {
                                            selectedChannel = channel
                                        },
                                        onDoubleClick = {
                                            onPlayChannel(channel.id, selectedCat, channel.name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // --- 4C. FAVORITOS TAB (Full Width Channels list) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(JetBackground)
                ) {
                    if (favoriteChannels.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.StarBorder, contentDescription = null, tint = JetTextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "No tienes canales favoritos", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Toca la estrella en un canal para agregarlo a favoritos", color = JetTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(favoriteChannels, key = { index, ch -> "${ch.id}_$index" }) { index, channel ->
                                val isSelected = selectedChannel?.id == channel.id
                                ChannelListItemJetGo(
                                    index = index + 1,
                                    channel = channel,
                                    isSelected = isSelected,
                                    isFav = true,
                                    isTv = isTv,
                                    onFavToggle = {
                                        viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon)
                                    },
                                    onClick = {
                                        selectedChannel = channel
                                    },
                                    onDoubleClick = {
                                        onPlayChannel(channel.id, selectedCat, channel.name)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showScreenCastDialog) {
            val castUrl = selectedChannel?.let { XtreamApi.getLiveStreamCandidates(it.id).firstOrNull() }
            ScreenCastDialog(
                streamUrl = castUrl,
                title = selectedChannel?.name ?: "Canal en Vivo",
                onDismiss = { showScreenCastDialog = false }
            )
        }
    }
}

/**
 * Channel Item Component (Móvil y TV D-Pad focus)
 */
@Composable
private fun ChannelListItemJetGo(
    index: Int,
    channel: LiveChannel,
    isSelected: Boolean,
    isFav: Boolean,
    isTv: Boolean,
    onFavToggle: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    val formattedIndex = String.format("%03d", index)
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) JetOrange else if (isFocused) Color(0xFF2C2C3C) else JetCardBg,
        border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 54.dp else 48.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .testTag("channel_item_${channel.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Channel Number
            Text(
                text = formattedIndex,
                color = if (isSelected || isFocused) Color.White else JetOrangeBright,
                fontSize = if (isTv) 13.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp)
            )

            // Channel Icon
            Box(
                modifier = Modifier
                    .size(if (isTv) 34.dp else 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(if (isTv) 28.dp else 24.dp)
                )
            }

            // Channel Name
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = if (isTv) 13.5.sp else 12.5.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Favorite Star
            IconButton(
                onClick = onFavToggle,
                modifier = Modifier.size(if (isTv) 32.dp else 28.dp)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (isFav) Color(0xFFFFC107) else Color(0xFF666666),
                    modifier = Modifier.size(if (isTv) 18.dp else 16.dp)
                )
            }
        }
    }
}
