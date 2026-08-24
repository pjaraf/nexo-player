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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.focus.focusProperties
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
import com.example.ui.components.BreakingNewsTvBanner
import com.example.ui.components.CHANNEL_FALLBACK
import com.example.ui.components.ScreenCastDialog
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val JetOrange = Color(0xFFDE5B17)
private val JetOrangeBright = Color(0xFFFF7A00)
private val PhoneRed = Color(0xFFE50914)
private val PhoneRedBright = Color(0xFFFF3333)
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
 *   * SMART TV / TV BOX (isTv = true): Interfaz nativa de TV a pantalla completa, zapping D-Pad, banner OSD y guía flotante moderna.
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
        TvLiveScreen(
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
// INTERFAZ MODERNA PARA TELEVISORES (SMART TV / GOOGLE TV / TV BOX - D-PAD)
// =========================================================================
@Composable
private fun TvLiveScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit,
    onExitToMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categories by viewModel.liveCategories.collectAsState()
    val selectedCat by viewModel.selectedLiveCat.collectAsState()
    val channels by viewModel.liveChannels.collectAsState()
    val loading by viewModel.liveLoading.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()

    var activeTvTab by remember { mutableStateOf(LiveSubTab.CATEGORIA) }
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var showQuickGuide by remember { mutableStateOf(false) }
    var showOsdBanner by remember { mutableStateOf(true) }
    var zapDirectionLabel by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val rootFocusRequester = remember { FocusRequester() }
    val activeChannelFocusRequester = remember { FocusRequester() }
    val activeCategoryFocusRequester = remember { FocusRequester() }
    val channelListState = rememberLazyListState()
    val categoryListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val favoriteChannels = remember(favorites, channels) {
        val favIds = favorites.filter { it.kind == "live" }.map { it.id }.toSet()
        channels.filter { favIds.contains(it.id) }
    }
    val displayChannels = if (activeTvTab == LiveSubTab.FAVORITOS) favoriteChannels else channels

    val targetChannelIndex = remember(displayChannels, selectedChannel) {
        if (displayChannels.isEmpty()) 0
        else {
            val idx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
            if (idx >= 0) idx else 0
        }
    }

    // Dedicated VLC player for TV Live
    val playerManager = remember { PlayerManager(context) }

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
        delay(300)
        try {
            rootFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Auto-select first channel when channels load
    LaunchedEffect(displayChannels) {
        if (selectedChannel == null && displayChannels.isNotEmpty()) {
            selectedChannel = displayChannels.first()
        }
    }

    // Play channel stream with fallback candidates
    LaunchedEffect(selectedChannel) {
        val ch = selectedChannel
        if (ch != null) {
            isBuffering = true
            hasError = false
            candidateIndex = 0
            showOsdBanner = true

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

    // Watchdog for Live TV: only switch candidates if connection is unresponsive after 7.5s
    LaunchedEffect(selectedChannel, candidateIndex, candidates) {
        if (candidates.size > 1 && candidateIndex + 1 < candidates.size) {
            delay(7500)
            if (isBuffering && !playerManager.mediaPlayer.isPlaying) {
                Log.w("TvLiveScreen", "Live channel timeout triggered for candidate $candidateIndex, trying next...")
                candidateIndex++
                try {
                    isBuffering = true
                    playerManager.play(candidates[candidateIndex], 0L)
                } catch (_: Throwable) {}
            }
        }
    }

    // Auto-hide channel OSD banner after 4 seconds
    LaunchedEffect(showOsdBanner, selectedChannel) {
        if (showOsdBanner) {
            delay(4000)
            showOsdBanner = false
        }
    }

    // Auto-hide quick guide after 12 seconds of inactivity
    LaunchedEffect(showQuickGuide, lastInteractionTime) {
        if (showQuickGuide) {
            delay(12000)
            showQuickGuide = false
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Throwable) {}
        }
    }

    // Reset scroll to top when category changes
    LaunchedEffect(selectedCat, activeTvTab) {
        try {
            channelListState.scrollToItem(0)
        } catch (_: Throwable) {}
    }

    // Scroll to currently playing channel when opening quick guide and transfer focus
    LaunchedEffect(showQuickGuide) {
        if (showQuickGuide) {
            val currentIdx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }.coerceAtLeast(0)
            if (displayChannels.isNotEmpty()) {
                try {
                    channelListState.scrollToItem((currentIdx - 2).coerceAtLeast(0))
                } catch (_: Throwable) {}
            }
            delay(120)
            try {
                activeChannelFocusRequester.requestFocus()
            } catch (_: Throwable) {
                try { activeCategoryFocusRequester.requestFocus() } catch (_: Throwable) {}
            }
        } else {
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Throwable) {}
        }
    }

    // Function to zap next / previous channel
    fun zapChannel(delta: Int) {
        if (displayChannels.isEmpty()) return
        val currentIdx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
        val newIdx = if (currentIdx == -1) {
            0
        } else {
            (currentIdx + delta + displayChannels.size) % displayChannels.size
        }
        selectedChannel = displayChannels[newIdx]
        zapDirectionLabel = if (delta > 0) "▲ Siguiente canal" else "▼ Canal anterior"
        showOsdBanner = true
        lastInteractionTime = System.currentTimeMillis()
    }

    // Back button handling
    BackHandler(enabled = true) {
        if (showQuickGuide) {
            showQuickGuide = false
            lastInteractionTime = System.currentTimeMillis()
            try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
        } else {
            onExitToMenu?.invoke()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable(!showQuickGuide)
            .onKeyEvent { keyEvent ->
                if (!showQuickGuide && keyEvent.type == KeyEventType.KeyDown) {
                    lastInteractionTime = System.currentTimeMillis()
                    val nativeKeyCode = keyEvent.nativeKeyEvent.keyCode
                    when (nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            zapChannel(-1)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            zapChannel(1)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BUTTON_A,
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        AndroidKeyEvent.KEYCODE_MENU -> {
                            showQuickGuide = true
                            showOsdBanner = false
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            zapDirectionLabel = null
                            showOsdBanner = true
                            true
                        }
                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE -> {
                            onExitToMenu?.invoke()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable {
                showQuickGuide = !showQuickGuide
                lastInteractionTime = System.currentTimeMillis()
            }
            .testTag("tv_live_root")
    ) {
        // 1. Fullscreen Video Player
        VlcPlayerView(
            playerManager = playerManager,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Error Overlay (only shown if stream completely fails to load)
        if (hasError && !isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF1A1A22),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, PhoneRed),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = PhoneRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Canal no disponible temporalmente",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Presiona [ARRIBA] o [ABAJO] para cambiar de canal, o [OK] para abrir la guía",
                            color = JetTextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 3. Modern Broadcast TV Channel OSD Banner (Pill flotante inferior)
        AnimatedVisibility(
            visible = showOsdBanner && !showQuickGuide,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, bottom = 28.dp)
        ) {
            val currentIdx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
            val chNumber = if (currentIdx >= 0) String.format("%03d", currentIdx + 1) else "001"
            val categoryLabel = if (activeTvTab == LiveSubTab.FAVORITOS) {
                "FAVORITOS"
            } else {
                categories.find { it.categoryId == selectedCat }?.categoryName?.uppercase() ?: "TRANSMISIÓN EN VIVO HD"
            }

            ModernTvOsdBanner(
                channelNumber = chNumber,
                channelName = selectedChannel?.name ?: "CANAL EN VIVO",
                categoryName = categoryLabel,
                channelLogoUrl = selectedChannel?.streamIcon,
                directionLabel = zapDirectionLabel,
                isBuffering = isBuffering
            )
        }

        // 5. MODERNA GUÍA RÁPIDA DE CANALES FLOTANTE (ESTILO OLED SMART TV)
        AnimatedVisibility(
            visible = showQuickGuide,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp, top = 20.dp, bottom = 20.dp)
                .width(400.dp)
                .fillMaxHeight()
        ) {
            Surface(
                color = Color(0xF40E1019),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, Color(0xFF282C40)),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    // Header de la Guía
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(JetOrange, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "GUÍA DE CANALES",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E2130)
                        ) {
                            Text(
                                text = "${displayChannels.size} Canales",
                                color = JetOrangeBright,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Selector Horizontal de Categorías (Modern Chips)
                    LazyRow(
                        state = categoryListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        // Chip Favoritos
                        item {
                            val isSelected = activeTvTab == LiveSubTab.FAVORITOS
                            var isFocused by remember { mutableStateOf(false) }
                            val isRequesterTarget = isSelected
                            Surface(
                                onClick = {
                                    activeTvTab = LiveSubTab.FAVORITOS
                                    lastInteractionTime = System.currentTimeMillis()
                                    coroutineScope.launch {
                                        delay(100)
                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFocused) Color(0xFF2C324A) else if (isSelected) JetOrange else Color(0xFF1A1C28),
                                border = if (isFocused) {
                                    BorderStroke(2.5.dp, Color(0xFFFF9800))
                                } else if (isSelected) {
                                    BorderStroke(1.5.dp, JetOrangeBright)
                                } else {
                                    BorderStroke(1.dp, Color(0xFF2B3045))
                                },
                                modifier = (if (isRequesterTarget) Modifier.focusRequester(activeCategoryFocusRequester) else Modifier)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            lastInteractionTime = System.currentTimeMillis()
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                AndroidKeyEvent.KEYCODE_ENTER,
                                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                    activeTvTab = LiveSubTab.FAVORITOS
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    }
                                                    true
                                                }
                                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    coroutineScope.launch {
                                                        delay(50)
                                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    }
                                                    true
                                                }
                                                AndroidKeyEvent.KEYCODE_BACK,
                                                AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                                    showQuickGuide = false
                                                    try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Favoritos",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Chip Todos
                        item {
                            val isSelected = activeTvTab == LiveSubTab.CATEGORIA && selectedCat == "ALL"
                            var isFocused by remember { mutableStateOf(false) }
                            val isRequesterTarget = isSelected
                            Surface(
                                onClick = {
                                    activeTvTab = LiveSubTab.CATEGORIA
                                    viewModel.selectLiveCategory("ALL")
                                    lastInteractionTime = System.currentTimeMillis()
                                    coroutineScope.launch {
                                        delay(100)
                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFocused) Color(0xFF2C324A) else if (isSelected) JetOrange else Color(0xFF1A1C28),
                                border = if (isFocused) {
                                    BorderStroke(2.5.dp, Color(0xFFFF9800))
                                } else if (isSelected) {
                                    BorderStroke(1.5.dp, JetOrangeBright)
                                } else {
                                    BorderStroke(1.dp, Color(0xFF2B3045))
                                },
                                modifier = (if (isRequesterTarget) Modifier.focusRequester(activeCategoryFocusRequester) else Modifier)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            lastInteractionTime = System.currentTimeMillis()
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                AndroidKeyEvent.KEYCODE_ENTER,
                                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                    activeTvTab = LiveSubTab.CATEGORIA
                                                    viewModel.selectLiveCategory("ALL")
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    }
                                                    true
                                                }
                                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    coroutineScope.launch {
                                                        delay(50)
                                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    }
                                                    true
                                                }
                                                AndroidKeyEvent.KEYCODE_BACK,
                                                AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                                    showQuickGuide = false
                                                    try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            ) {
                                Text(
                                    text = "Todos",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }

                        // Categorías del Servidor
                        itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                            val isSelected = activeTvTab == LiveSubTab.CATEGORIA && selectedCat == cat.categoryId
                            var isFocused by remember { mutableStateOf(false) }
                            val isRequesterTarget = isSelected
                            Surface(
                                onClick = {
                                    activeTvTab = LiveSubTab.CATEGORIA
                                    viewModel.selectLiveCategory(cat.categoryId)
                                    lastInteractionTime = System.currentTimeMillis()
                                    coroutineScope.launch {
                                        delay(100)
                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFocused) Color(0xFF2C324A) else if (isSelected) JetOrange else Color(0xFF1A1C28),
                                border = if (isFocused) {
                                    BorderStroke(2.5.dp, Color(0xFFFF9800))
                                } else if (isSelected) {
                                    BorderStroke(1.5.dp, JetOrangeBright)
                                } else {
                                    BorderStroke(1.dp, Color(0xFF2B3045))
                                },
                                modifier = (if (isRequesterTarget) Modifier.focusRequester(activeCategoryFocusRequester) else Modifier)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            lastInteractionTime = System.currentTimeMillis()
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                AndroidKeyEvent.KEYCODE_ENTER,
                                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                    activeTvTab = LiveSubTab.CATEGORIA
                                                    viewModel.selectLiveCategory(cat.categoryId)
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    }
                                                    true
                                                }
                                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    coroutineScope.launch {
                                                        delay(50)
                                                        try { activeChannelFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    }
                                                    true
                                                }
                                                AndroidKeyEvent.KEYCODE_BACK,
                                                AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                                    showQuickGuide = false
                                                    try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            ) {
                                Text(
                                    text = cat.categoryName,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Lista Vertical Moderna de Canales
                    if (loading && channels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = JetOrange, modifier = Modifier.size(36.dp))
                        }
                    } else if (displayChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (activeTvTab == LiveSubTab.FAVORITOS) "Sin canales favoritos guardados" else "No hay canales en esta categoría",
                                color = JetTextMuted,
                                fontSize = 13.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(displayChannels, key = { index, ch -> "${ch.id}_$index" }) { index, channel ->
                                val isCurrentPlaying = selectedChannel?.id == channel.id
                                val isFav = favorites.any { it.kind == "live" && it.id == channel.id }
                                val isRequesterTarget = (index == targetChannelIndex)

                                ModernTvChannelItem(
                                    channel = channel,
                                    index = index + 1,
                                    isPlaying = isCurrentPlaying,
                                    isFav = isFav,
                                    modifier = if (isRequesterTarget) Modifier.focusRequester(activeChannelFocusRequester) else Modifier,
                                    onSelect = {
                                        selectedChannel = channel
                                        showQuickGuide = false
                                        zapDirectionLabel = null
                                        showOsdBanner = true
                                        lastInteractionTime = System.currentTimeMillis()
                                        try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    },
                                    onFavToggle = {
                                        viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon)
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onNavigateUpToCategory = {
                                        try { activeCategoryFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    },
                                    onDismissGuide = {
                                        showQuickGuide = false
                                        try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item de canal moderno optimizado para control remoto en TV (D-Pad).
 */
@Composable
private fun ModernTvChannelItem(
    channel: LiveChannel,
    index: Int,
    isPlaying: Boolean,
    isFav: Boolean,
    onSelect: () -> Unit,
    onFavToggle: () -> Unit,
    onNavigateUpToCategory: () -> Unit,
    onDismissGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedIndex = String.format("%03d", index)
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(10.dp),
        color = if (isFocused) Color(0xFF1E3A8A) else if (isPlaying) Color(0xFF1A1D2E) else Color(0xFF131520),
        border = if (isFocused) {
            BorderStroke(2.5.dp, Color(0xFFFFD700))
        } else if (isPlaying) {
            BorderStroke(1.5.dp, JetOrange)
        } else {
            BorderStroke(1.dp, Color(0xFF222638))
        },
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (index == 1) {
                                onNavigateUpToCategory()
                                true
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            false // Let Compose naturally navigate to the next channel
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                            onSelect()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            onNavigateUpToCategory()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onDismissGuide()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE -> {
                            onDismissGuide()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_PROG_YELLOW,
                        AndroidKeyEvent.KEYCODE_BUTTON_Y -> {
                            onFavToggle()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .testTag("tv_channel_${channel.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Número de canal
            Text(
                text = formattedIndex,
                color = if (isFocused) Color(0xFFFFD700) else if (isPlaying) JetOrangeBright else Color(0xFF8E95A5),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(32.dp)
            )

            // Logo del canal
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF090A10)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(30.dp)
                )
            }

            // Nombre del canal
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isPlaying || isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isPlaying) {
                    Text(
                        text = "● Reproduciendo ahora",
                        color = Color(0xFF4CAF50),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Indicador / Botón Favorito (no focusable para evitar atrapar el D-Pad)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onFavToggle() }
                    .focusProperties { canFocus = false },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (isFav) Color(0xFFFFC107) else Color(0xFF6B7280),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Modern OSD Channel Banner para TV con diseño Glassmorphic.
 */
@Composable
private fun ModernTvOsdBanner(
    channelNumber: String,
    channelName: String,
    categoryName: String,
    channelLogoUrl: String?,
    directionLabel: String?,
    isBuffering: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentTime = remember {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date())
    }

    Surface(
        color = Color(0xF20F121C),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2C324A)),
        shadowElevation = 18.dp,
        modifier = modifier
            .widthIn(min = 340.dp, max = 560.dp)
            .height(68.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Badge Número Canal
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFFDE5B17))),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channelNumber,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Logo Canal
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (!channelLogoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channelLogoUrl,
                        contentDescription = channelName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = JetOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Info Canal y Categoría
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channelName,
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$currentTime • $categoryName",
                        color = Color(0xFFA0A5B5),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!directionLabel.isNullOrBlank()) {
                        Text(
                            text = directionLabel,
                            color = JetOrangeBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Badge LIVE / Conectando
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isBuffering) Color(0xFF422E10) else Color(0xFF1B5E20)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isBuffering) JetOrange else Color(0xFF4CAF50), CircleShape)
                    )
                    Text(
                        text = if (isBuffering) "CONECTANDO" else "EN VIVO",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
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

    // Watchdog for mobile player
    LaunchedEffect(selectedChannel, candidateIndex, channelCandidates) {
        if (channelCandidates.size > 1 && candidateIndex + 1 < channelCandidates.size) {
            delay(7500)
            if (isPlayerBuffering && !playerManager.mediaPlayer.isPlaying) {
                candidateIndex++
                try {
                    isPlayerBuffering = true
                    playerManager.play(channelCandidates[candidateIndex], 0L)
                } catch (_: Throwable) {}
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
                    focusedBorderColor = PhoneRed,
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
                    CircularProgressIndicator(color = PhoneRed, strokeWidth = 3.dp, modifier = Modifier.size(36.dp).align(Alignment.Center))
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
                        containerColor = if (activeTab == LiveSubTab.CATEGORIA) PhoneRed else Color(0xFF202020),
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
                        containerColor = if (activeTab == LiveSubTab.FAVORITOS) PhoneRed else Color(0xFF202020),
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
                                    color = if (isSelected) PhoneRed else Color.Transparent,
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
                                    color = if (isSelected) PhoneRed else Color.Transparent,
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
                            CircularProgressIndicator(color = PhoneRed, modifier = Modifier.size(32.dp).align(Alignment.Center))
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
                                        onDoubleClick = { onPlayChannel(channel.id, selectedCat ?: "ALL", channel.name) }
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
                                    onDoubleClick = { onPlayChannel(channel.id, selectedCat ?: "ALL", channel.name) }
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
    onDoubleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedIndex = String.format("%03d", index)
    var isFocused by remember { mutableStateOf(false) }

    val activePrimaryColor = if (isTv) JetOrange else PhoneRed
    val activeNumberColor = if (isTv) JetOrangeBright else PhoneRedBright

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) Color(0xFF2C2C3E) else if (isSelected) activePrimaryColor else JetCardBg,
        border = if (isFocused) BorderStroke(2.5.dp, Color(0xFFFF9800)) else if (isSelected && isTv) BorderStroke(1.5.dp, JetOrangeBright) else null,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTv) 56.dp else 48.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .testTag("channel_item_${channel.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formattedIndex,
                color = if (isFocused) JetOrangeBright else if (isSelected) Color.White else activeNumberColor,
                fontSize = if (isTv) 13.5.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp)
            )

            Box(
                modifier = Modifier
                    .size(if (isTv) 36.dp else 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(if (isTv) 30.dp else 24.dp)
                )
            }

            Text(
                text = channel.name,
                color = Color.White,
                fontSize = if (isTv) 14.sp else 12.5.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isTv && isSelected) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isFocused) JetOrange else Color(0xFF1B5E20)
                ) {
                    Text(
                        text = "EN VIVO",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

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
