package com.example.ui.screens

import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * PANTALLA PRINCIPAL DE TV EN VIVO:
 * - Detección automática de dispositivo:
 *   * SMART TV / TV BOX (isTv = true): Interfaz nativa de TV a pantalla completa, zapping D-Pad, banner OSD y menú lateral sin cierres.
 *   * TELÉFONOS (isTv = false): Interfaz JetGo con mini-reproductor ajustado a 16:9 y categorías/canales.
 */
@Composable
fun LiveScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit,
    onExitToMenu: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }

    if (isTv) {
        TvLiveFullscreenScreen(
            viewModel = viewModel,
            onPlayChannel = onPlayChannel,
            onExitToMenu = onExitToMenu
        )
    } else {
        PhoneLiveScreen(
            viewModel = viewModel,
            onPlayChannel = onPlayChannel,
            onExitToMenu = onExitToMenu
        )
    }
}

// =========================================================================
// INTERFAZ PARA TELEVISORES (SMART TV / ANDROID TV / TV BOX - D-PAD NATIVO)
// =========================================================================
@Composable
private fun TvLiveFullscreenScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit,
    onExitToMenu: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val categories by viewModel.liveCategories.collectAsState()
    val selectedCat by viewModel.selectedLiveCat.collectAsState()
    val channels by viewModel.liveChannels.collectAsState()
    val loading by viewModel.liveLoading.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showChannelBanner by remember { mutableStateOf(true) }
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeTvTab by remember { mutableStateOf(LiveSubTab.CATEGORIA) }

    val playerManager = remember { PlayerManager(context) }

    // Safe VLC instance
    DisposableEffect(playerManager) {
        playerManager.setAspectRatio("16:9")
        playerManager.setScale(0f)
        playerManager.onBuffering = { buffering, _ ->
            isBuffering = buffering
        }
        playerManager.onPlayingChanged = { playing ->
            if (playing) {
                isBuffering = false
                hasError = false
            }
        }
        playerManager.onError = { error ->
            Log.w("TvLiveScreen", "Error VLC candidato $candidateIndex: $error")
            if (candidateIndex + 1 < candidates.size) {
                candidateIndex++
                val nextCandidate = candidates[candidateIndex]
                try {
                    isBuffering = true
                    playerManager.play(nextCandidate, 0L)
                } catch (_: Throwable) {
                    hasError = true
                    isBuffering = false
                }
            } else {
                hasError = true
                isBuffering = false
            }
        }
        onDispose {
            playerManager.release()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLiveCategories()
    }

    // Auto select first channel if none selected
    LaunchedEffect(channels) {
        if (selectedChannel == null && channels.isNotEmpty()) {
            selectedChannel = channels.first()
        }
    }

    // Play channel stream with fallback candidates
    LaunchedEffect(selectedChannel) {
        val ch = selectedChannel
        if (ch != null) {
            isBuffering = true
            hasError = false
            candidateIndex = 0
            showChannelBanner = true

            val raw = XtreamApi.getLiveStreamCandidates(ch.id)
            val list = mutableListOf<String>()
            ch.directStreamUrl?.takeIf { it.isNotBlank() }?.let { list.add(it) }
            for (c in raw) {
                if (!list.contains(c)) list.add(c)
            }
            if (list.isEmpty()) {
                val single = XtreamApi.getLiveStreamUrl(ch.id)
                if (single.isNotBlank()) list.add(single)
            }
            candidates = list

            delay(150)

            if (list.isNotEmpty()) {
                try {
                    playerManager.play(list[0], 0L)
                } catch (e: Throwable) {
                    if (list.size > 1) {
                        candidateIndex = 1
                        try {
                            playerManager.play(list[1], 0L)
                        } catch (_: Throwable) {
                            hasError = true
                            isBuffering = false
                        }
                    } else {
                        hasError = true
                        isBuffering = false
                    }
                }
            } else {
                hasError = true
                isBuffering = false
            }
        }
    }

    // Auto-hide channel banner after 4 seconds
    LaunchedEffect(showChannelBanner, selectedChannel) {
        if (showChannelBanner) {
            delay(4000)
            showChannelBanner = false
        }
    }

    // Favorite channels list
    val favoriteChannels = remember(favorites, channels) {
        val favIds = favorites.filter { it.kind == "live" }.map { it.id }.toSet()
        channels.filter { favIds.contains(it.id) }
    }

    val displayChannels = if (activeTvTab == LiveSubTab.FAVORITOS) favoriteChannels else channels

    // Zapping function
    fun zapChannel(delta: Int) {
        if (displayChannels.isEmpty()) return
        val currentIdx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
        val newIdx = if (currentIdx == -1) {
            0
        } else {
            (currentIdx + delta).mod(displayChannels.size)
        }
        selectedChannel = displayChannels[newIdx]
        showChannelBanner = true
    }

    val channelListState = rememberLazyListState()

    // Manejador del botón ATRÁS
    BackHandler(enabled = true) {
        if (showMenu) {
            showMenu = false
        } else {
            onExitToMenu?.invoke()
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                            if (!showMenu) {
                                showMenu = true
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showMenu) {
                                zapChannel(-1)
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showMenu) {
                                zapChannel(1)
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!showMenu) {
                                showMenu = true
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_MENU -> {
                            showMenu = !showMenu
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .testTag("tv_live_fullscreen_root")
    ) {
        // 1. Fullscreen Video Player (VLC Surface)
        VlcPlayerView(
            playerManager = playerManager,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Buffering Indicator
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = JetOrange,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        // 3. Error Banner
        if (hasError && !isBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Canal no disponible temporalmente",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 4. Channel Info Banner (Bottom OSD)
        AnimatedVisibility(
            visible = showChannelBanner && !showMenu,
            enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            val currentIdx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
            val chNumber = if (currentIdx >= 0) String.format("%03d", currentIdx + 1) else "---"

            Surface(
                color = Color(0xE6141414),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Channel Number Badge
                    Surface(
                        color = JetOrange,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = chNumber,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // Channel Logo
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = selectedChannel?.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                            contentDescription = selectedChannel?.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // Channel Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedChannel?.name ?: "Cargando canal...",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (activeTvTab == LiveSubTab.FAVORITOS) "FAVORITOS" else (categories.find { it.categoryId == selectedCat }?.categoryName?.uppercase() ?: "TODOS"),
                            color = JetTextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Live Badge
                    Surface(
                        color = Color(0xFFCC0000),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Text(
                                text = "EN VIVO",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // 5. TV Overlay Menu (Categorías & Canales con navegación D-Pad)
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(tween(200)) + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut(tween(200)) + slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.95f),
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 1400f
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(620.dp)
                        .padding(start = 28.dp, top = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 5A. Categories Drawer
                    Surface(
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Header
                            item {
                                Text(
                                    text = "CATEGORÍAS",
                                    color = JetOrangeBright,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }

                            // FAVORITOS Tab
                            item {
                                var isFavFocused by remember { mutableStateOf(false) }
                                val isSelected = activeTvTab == LiveSubTab.FAVORITOS
                                Surface(
                                    onClick = {
                                        activeTvTab = LiveSubTab.FAVORITOS
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) JetOrange else if (isFavFocused) Color(0xFF333344) else Color.Transparent,
                                    border = if (isFavFocused) BorderStroke(2.dp, Color.White) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isFavFocused = it.isFocused }
                                        .focusable()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "FAVORITOS (${favoriteChannels.size})",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isFavFocused) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // TODOS Item
                            item {
                                var isAllFocused by remember { mutableStateOf(false) }
                                val isSelected = activeTvTab == LiveSubTab.CATEGORIA && selectedCat == "ALL"
                                Surface(
                                    onClick = {
                                        activeTvTab = LiveSubTab.CATEGORIA
                                        viewModel.selectLiveCategory("ALL")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) JetOrange else if (isAllFocused) Color(0xFF333344) else Color.Transparent,
                                    border = if (isAllFocused) BorderStroke(2.dp, Color.White) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isAllFocused = it.isFocused }
                                        .focusable()
                                ) {
                                    Text(
                                        text = "TODOS LOS CANALES",
                                        color = if (isSelected || isAllFocused) Color.White else JetTextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected || isAllFocused) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                    )
                                }
                            }

                            // Categories from Server
                            itemsIndexed(categories, key = { idx, cat -> "tv_${cat.categoryId}_$idx" }) { _, cat ->
                                var isCatFocused by remember { mutableStateOf(false) }
                                val isSelected = activeTvTab == LiveSubTab.CATEGORIA && selectedCat == cat.categoryId
                                Surface(
                                    onClick = {
                                        activeTvTab = LiveSubTab.CATEGORIA
                                        viewModel.selectLiveCategory(cat.categoryId)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) JetOrange else if (isCatFocused) Color(0xFF333344) else Color.Transparent,
                                    border = if (isCatFocused) BorderStroke(2.dp, Color.White) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isCatFocused = it.isFocused }
                                        .focusable()
                                ) {
                                    Text(
                                        text = cat.categoryName.uppercase(),
                                        color = if (isSelected || isCatFocused) Color.White else JetTextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected || isCatFocused) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 5B. Channels List Drawer
                    Surface(
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                    ) {
                        if (loading && displayChannels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = JetOrange, modifier = Modifier.size(32.dp))
                            }
                        } else if (displayChannels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No hay canales en esta sección", color = JetTextMuted, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                state = channelListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(displayChannels, key = { idx, ch -> "tv_ch_${ch.id}_$idx" }) { index, channel ->
                                    val isCurrent = selectedChannel?.id == channel.id
                                    val isFav = viewModel.isFavorite("live", channel.id)
                                    var isItemFocused by remember { mutableStateOf(false) }

                                    Surface(
                                        onClick = {
                                            selectedChannel = channel
                                            showMenu = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCurrent) JetOrange else if (isItemFocused) Color(0xFF2C2C3C) else JetCardBg,
                                        border = if (isItemFocused) BorderStroke(2.dp, Color.White) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .onFocusChanged { isItemFocused = it.isFocused }
                                            .focusable()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = String.format("%03d", index + 1),
                                                color = if (isCurrent || isItemFocused) Color.White else JetOrangeBright,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(34.dp)
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(alpha = 0.3f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = channel.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                                                    contentDescription = channel.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }

                                            Text(
                                                text = channel.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isCurrent || isItemFocused) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            if (isFav) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "Favorito",
                                                    tint = Color(0xFFFFC107),
                                                    modifier = Modifier.size(18.dp)
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
    }
}

// =========================================================================
// INTERFAZ PARA TELÉFONOS (MÓVIL / MINI REPRODUCTOR 16:9 AJUSTADO)
// =========================================================================
@Composable
private fun PhoneLiveScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit,
    onExitToMenu: (() -> Unit)? = null
) {
    val context = LocalContext.current
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

    // Embedded VLC Instance for phone preview
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
            Log.w("PhoneLiveScreen", "VLC Error candidato $candidateIndex: $error")
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

    val filteredChannels = remember(channels, search) {
        if (search.isBlank()) {
            channels
        } else {
            val q = search.trim().lowercase()
            channels.filter { it.name.lowercase().contains(q) || it.groupName.lowercase().contains(q) }
        }
    }

    val favoriteChannels = remember(favorites, channels) {
        val favIds = favorites.filter { it.kind == "live" }.map { it.id }.toSet()
        channels.filter { favIds.contains(it.id) }
    }

    LaunchedEffect(filteredChannels) {
        if (selectedChannel == null && filteredChannels.isNotEmpty()) {
            selectedChannel = filteredChannels.first()
        }
    }

    val channelListState = rememberLazyListState()

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
            .testTag("phone_live_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            // 1. Top Search Bar
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.setLiveSearch(it) },
                placeholder = { Text(text = "Búsqueda por canal", color = JetTextMuted, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = JetTextMuted, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { viewModel.setLiveSearch("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = JetTextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JetOrange,
                    unfocusedBorderColor = Color(0xFF2A2A2A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = JetCardBg,
                    unfocusedContainerColor = JetCardBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .height(48.dp)
                    .testTag("phone_live_search")
            )

            // 2. Mini Player Box 16:9
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF262626), RoundedCornerShape(14.dp))
                    .clickable {
                        selectedChannel?.let { ch ->
                            onPlayChannel(ch.id, selectedCat, ch.name)
                        }
                    }
                    .testTag("phone_live_player_preview_box"),
                contentAlignment = Alignment.Center
            ) {
                VlcPlayerView(
                    playerManager = playerManager,
                    modifier = Modifier.fillMaxSize()
                )

                if (isPlayerBuffering) {
                    CircularProgressIndicator(color = JetOrange, strokeWidth = 3.dp, modifier = Modifier.size(36.dp).align(Alignment.Center))
                }

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

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = CircleShape) {
                        IconButton(onClick = { showScreenCastDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Cast, contentDescription = "Transmitir a TV", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = CircleShape) {
                        IconButton(
                            onClick = {
                                selectedChannel?.let { ch ->
                                    onPlayChannel(ch.id, selectedCat, ch.name)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla completa", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // 3. Sub Tabs: CATEGORÍA & FAVORITOS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { activeTab = LiveSubTab.CATEGORIA },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == LiveSubTab.CATEGORIA) JetOrange else Color(0xFF202020),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text(text = "CATEGORÍA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { activeTab = LiveSubTab.FAVORITOS },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == LiveSubTab.FAVORITOS) JetOrange else Color(0xFF202020),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Text(text = "FAVORITOS (${favoriteChannels.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // 4. Content Area
            if (activeTab == LiveSubTab.CATEGORIA) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Categories
                    Surface(
                        color = JetSidebarBg,
                        modifier = Modifier
                            .width(130.dp)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            item {
                                val isSelected = selectedCat == "ALL"
                                Surface(
                                    color = if (isSelected) JetOrange else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectLiveCategory("ALL") }
                                ) {
                                    Text(
                                        text = "TODOS",
                                        color = if (isSelected) Color.White else JetTextMuted,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                                val isSelected = selectedCat == cat.categoryId
                                Surface(
                                    color = if (isSelected) JetOrange else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectLiveCategory(cat.categoryId) }
                                ) {
                                    Text(
                                        text = cat.categoryName.uppercase(),
                                        color = if (isSelected) Color.White else JetTextMuted,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Channels
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(JetBackground)
                    ) {
                        if (loading && channels.isEmpty()) {
                            CircularProgressIndicator(color = JetOrange, modifier = Modifier.size(32.dp).align(Alignment.Center))
                        } else if (filteredChannels.isEmpty()) {
                            Text(
                                text = if (search.isNotBlank()) "Sin canales para \"$search\"" else "No hay canales",
                                color = JetTextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center).padding(16.dp)
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
                                        isTv = false,
                                        onFavToggle = { viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon) },
                                        onClick = { selectedChannel = channel },
                                        onDoubleClick = { onPlayChannel(channel.id, selectedCat, channel.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Favorites
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(JetBackground)
                ) {
                    if (favoriteChannels.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
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
                                    isTv = false,
                                    onFavToggle = { viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon) },
                                    onClick = { selectedChannel = channel },
                                    onDoubleClick = { onPlayChannel(channel.id, selectedCat, channel.name) }
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
            Text(
                text = formattedIndex,
                color = if (isSelected || isFocused) Color.White else JetOrangeBright,
                fontSize = if (isTv) 13.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp)
            )

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

            Text(
                text = channel.name,
                color = Color.White,
                fontSize = if (isTv) 13.5.sp else 12.5.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

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
