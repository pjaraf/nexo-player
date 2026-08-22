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
 * Estados del menú lateral para Android TV:
 * - CLOSED: Reproductor a pantalla completa, zapping directo con UP/DOWN y OSD banner Breaking News.
 * - CHANNELS: Ventana lateral con lista de canales del grupo seleccionado (1er nivel).
 * - CATEGORIES: Ventana lateral expandida con categorías y canales lado a lado (2do nivel / al presionar 2 veces).
 */
private enum class TvMenuLevel {
    CLOSED,
    CHANNELS,
    CATEGORIES
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
// INTERFAZ PARA TELEVISORES (SMART TV / ANDROID TV / TV BOX - D-PAD NATIVO)
// =========================================================================
@Composable
private fun TvLiveScreen(
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

    var activeTvTab by remember { mutableStateOf(LiveSubTab.CATEGORIA) }
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var menuLevel by remember { mutableStateOf(TvMenuLevel.CLOSED) }
    var showChannelBanner by remember { mutableStateOf(true) }
    var zapDirectionLabel by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val rootFocusRequester = remember { FocusRequester() }
    val channelListState = rememberLazyListState()
    val categoryListState = rememberLazyListState()

    val favoriteChannels = remember(favorites, channels) {
        val favIds = favorites.filter { it.kind == "live" }.map { it.id }.toSet()
        channels.filter { favIds.contains(it.id) }
    }
    val displayChannels = if (activeTvTab == LiveSubTab.FAVORITOS) favoriteChannels else channels

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

            delay(120)
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

    // Auto-hide channel banner after 4.5 seconds
    LaunchedEffect(showChannelBanner, selectedChannel) {
        if (showChannelBanner) {
            delay(4500)
            showChannelBanner = false
        }
    }

    // Auto-hide lateral menu after 12 seconds of inactivity
    LaunchedEffect(menuLevel, lastInteractionTime) {
        if (menuLevel != TvMenuLevel.CLOSED) {
            delay(12000)
            menuLevel = TvMenuLevel.CLOSED
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Throwable) {}
        }
    }

    // Auto-scroll to selected channel when entering channels menu
    LaunchedEffect(menuLevel) {
        if (menuLevel == TvMenuLevel.CHANNELS && selectedChannel != null) {
            val idx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
            if (idx >= 0) {
                try {
                    channelListState.scrollToItem((idx - 2).coerceAtLeast(0))
                } catch (_: Throwable) {}
            }
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
        showChannelBanner = true
        lastInteractionTime = System.currentTimeMillis()
    }

    // Back button handling
    BackHandler(enabled = true) {
        when (menuLevel) {
            TvMenuLevel.CATEGORIES -> {
                menuLevel = TvMenuLevel.CHANNELS
                lastInteractionTime = System.currentTimeMillis()
            }
            TvMenuLevel.CHANNELS -> {
                menuLevel = TvMenuLevel.CLOSED
                lastInteractionTime = System.currentTimeMillis()
                try {
                    rootFocusRequester.requestFocus()
                } catch (_: Throwable) {}
            }
            TvMenuLevel.CLOSED -> {
                onExitToMenu?.invoke()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    lastInteractionTime = System.currentTimeMillis()
                    val nativeKeyCode = keyEvent.nativeKeyEvent.keyCode
                    when {
                        // When lateral menu is CLOSED:
                        menuLevel == TvMenuLevel.CLOSED -> {
                            when (nativeKeyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                    zapChannel(-1)
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                    zapChannel(1)
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                AndroidKeyEvent.KEYCODE_ENTER,
                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                AndroidKeyEvent.KEYCODE_BUTTON_A,
                                AndroidKeyEvent.KEYCODE_MENU -> {
                                    menuLevel = TvMenuLevel.CHANNELS
                                    showChannelBanner = false
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    zapDirectionLabel = null
                                    showChannelBanner = true
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_BACK,
                                AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                    onExitToMenu?.invoke()
                                    true
                                }
                                else -> false
                            }
                        }
                        // When lateral menu is OPEN:
                        else -> {
                            when (nativeKeyCode) {
                                AndroidKeyEvent.KEYCODE_MENU -> {
                                    menuLevel = TvMenuLevel.CLOSED
                                    try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                } else false
            }
            .clickable {
                menuLevel = if (menuLevel == TvMenuLevel.CLOSED) TvMenuLevel.CHANNELS else TvMenuLevel.CLOSED
                lastInteractionTime = System.currentTimeMillis()
            }
            .testTag("tv_live_root")
    ) {
        // 1. Fullscreen VLC Video Player
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
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = JetOrange,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Cargando señal en vivo...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Error Banner (if stream fails after trying all candidates)
        if (hasError && !isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF1E1E1E),
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
                            text = "Presiona [ARRIBA] o [ABAJO] para cambiar de canal, o [IZQUIERDA] para la lista",
                            color = JetTextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 4. TV Broadcast Breaking News Channel Banner (Overlay inferior estilo emisión)
        AnimatedVisibility(
            visible = showChannelBanner && menuLevel == TvMenuLevel.CLOSED,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, bottom = 28.dp)
        ) {
            val currentIdx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
            val chNumber = if (currentIdx >= 0) "${currentIdx + 1}" else "1"
            val categoryLabel = if (activeTvTab == LiveSubTab.FAVORITOS) {
                "FAVORITOS"
            } else {
                categories.find { it.categoryId == selectedCat }?.categoryName?.uppercase() ?: "TRANSMISIÓN EN VIVO HD"
            }

            BreakingNewsTvBanner(
                channelName = selectedChannel?.name ?: "CANAL EN VIVO",
                channelNumber = chNumber,
                categoryName = categoryLabel,
                channelLogoUrl = selectedChannel?.streamIcon,
                directionLabel = zapDirectionLabel,
                totalChannels = displayChannels.size,
                currentIndex = if (currentIdx >= 0) currentIdx + 1 else 1
            )
        }

        // 5. Ventana lateral interactiva: Canales (1er nivel) y Categorías (2do nivel al presionar 2 veces [←])
        AnimatedVisibility(
            visible = menuLevel != TvMenuLevel.CLOSED,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterStart)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xF80B0B0E),
                                Color(0xF4121216),
                                Color(0xE8141418),
                                Color(0xB0101014),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(vertical = 18.dp, horizontal = 16.dp)
            ) {
                // COLUMNA 1: CATEGORÍAS (Se expande al presionar 2 veces o presionar IZQUIERDA en la lista)
                AnimatedVisibility(
                    visible = menuLevel == TvMenuLevel.CATEGORIES,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    Row {
                        Column(
                            modifier = Modifier
                                .width(240.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF16161C).copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                                .border(1.2.dp, Color(0xFF2C2C38), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CATEGORÍAS",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "[→ Canales]",
                                    color = JetOrangeBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Sub Tabs: Categorías / Favoritos
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        activeTvTab = LiveSubTab.CATEGORIA
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeTvTab == LiveSubTab.CATEGORIA) JetOrange else Color(0xFF24242C),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Text("Todas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        activeTvTab = LiveSubTab.FAVORITOS
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeTvTab == LiveSubTab.FAVORITOS) JetOrange else Color(0xFF24242C),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                                        Text("Favs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (activeTvTab == LiveSubTab.CATEGORIA) {
                                LazyColumn(
                                    state = categoryListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    item {
                                        val isSelected = selectedCat == "ALL"
                                        var isFocused by remember { mutableStateOf(false) }
                                        Surface(
                                            onClick = {
                                                viewModel.selectLiveCategory("ALL")
                                                menuLevel = TvMenuLevel.CHANNELS
                                                lastInteractionTime = System.currentTimeMillis()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) JetOrange else if (isFocused) Color(0xFF2E2E42) else Color.Transparent,
                                            border = if (isFocused && !isSelected) BorderStroke(1.5.dp, Color.White) else null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isFocused = it.isFocused }
                                                .onKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        lastInteractionTime = System.currentTimeMillis()
                                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                                                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                            AndroidKeyEvent.KEYCODE_ENTER -> {
                                                                viewModel.selectLiveCategory("ALL")
                                                                menuLevel = TvMenuLevel.CHANNELS
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    } else false
                                                }
                                                .focusable()
                                        ) {
                                            Text(
                                                text = "TODOS LOS CANALES",
                                                color = if (isSelected || isFocused) Color.White else JetTextMuted,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                            )
                                        }
                                    }
                                    itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                                        val isSelected = selectedCat == cat.categoryId
                                        var isFocused by remember { mutableStateOf(false) }
                                        Surface(
                                            onClick = {
                                                viewModel.selectLiveCategory(cat.categoryId)
                                                menuLevel = TvMenuLevel.CHANNELS
                                                lastInteractionTime = System.currentTimeMillis()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) JetOrange else if (isFocused) Color(0xFF2E2E42) else Color.Transparent,
                                            border = if (isFocused && !isSelected) BorderStroke(1.5.dp, Color.White) else null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isFocused = it.isFocused }
                                                .onKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        lastInteractionTime = System.currentTimeMillis()
                                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                                                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                            AndroidKeyEvent.KEYCODE_ENTER -> {
                                                                viewModel.selectLiveCategory(cat.categoryId)
                                                                menuLevel = TvMenuLevel.CHANNELS
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    } else false
                                                }
                                                .focusable()
                                        ) {
                                            Text(
                                                text = cat.categoryName.uppercase(),
                                                color = if (isSelected || isFocused) Color.White else JetTextMuted,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Canales marcados con estrella",
                                        color = JetTextMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                // COLUMNA 2: LISTA DE CANALES (Ventana Lateral Principal)
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF16161C).copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                        .border(1.2.dp, Color(0xFF2C2C38), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    val currentCatName = if (activeTvTab == LiveSubTab.FAVORITOS) {
                        "FAVORITOS"
                    } else {
                        categories.find { it.categoryId == selectedCat }?.categoryName?.uppercase() ?: "TODOS LOS CANALES"
                    }

                    // Encabezado con Botón/Pill de Categorías y cantidad de canales
                    Surface(
                        onClick = {
                            menuLevel = if (menuLevel == TvMenuLevel.CATEGORIES) TvMenuLevel.CHANNELS else TvMenuLevel.CATEGORIES
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF20202A),
                        border = BorderStroke(1.dp, if (menuLevel == TvMenuLevel.CATEGORIES) JetOrange else Color(0xFF333344)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (menuLevel == TvMenuLevel.CATEGORIES) JetOrange else Color(0xFF303040)
                                ) {
                                    Text(
                                        text = "◄ CATEGORÍAS",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = currentCatName,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${displayChannels.size} ch",
                                color = JetOrangeBright,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (loading && channels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = JetOrange, modifier = Modifier.size(36.dp))
                        }
                    } else if (displayChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No hay canales en esta categoría",
                                color = JetTextMuted,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(displayChannels, key = { index, ch -> "${ch.id}_$index" }) { index, channel ->
                                val isSelected = selectedChannel?.id == channel.id
                                val isFav = favorites.any { it.kind == "live" && it.id == channel.id }
                                ChannelListItemJetGo(
                                    channel = channel,
                                    index = index + 1,
                                    isSelected = isSelected,
                                    isFav = isFav,
                                    isTv = true,
                                    onClick = {
                                        selectedChannel = channel
                                        menuLevel = TvMenuLevel.CLOSED
                                        zapDirectionLabel = null
                                        showChannelBanner = true
                                        lastInteractionTime = System.currentTimeMillis()
                                        try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    },
                                    onDoubleClick = {
                                        selectedChannel = channel
                                        menuLevel = TvMenuLevel.CLOSED
                                        zapDirectionLabel = null
                                        showChannelBanner = true
                                        lastInteractionTime = System.currentTimeMillis()
                                        try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                    },
                                    onFavToggle = {
                                        viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon)
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    modifier = Modifier.onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            lastInteractionTime = System.currentTimeMillis()
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    // Al presionar izquierda en la lista de canales, abre Categorías
                                                    menuLevel = TvMenuLevel.CATEGORIES
                                                    true
                                                }
                                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                    // Al presionar derecha en canales, cierra la ventana y vuelve al reproductor
                                                    menuLevel = TvMenuLevel.CLOSED
                                                    try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    true
                                                }
                                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                AndroidKeyEvent.KEYCODE_ENTER,
                                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                    selectedChannel = channel
                                                    menuLevel = TvMenuLevel.CLOSED
                                                    zapDirectionLabel = null
                                                    showChannelBanner = true
                                                    try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
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
        color = if (isSelected) activePrimaryColor else if (isFocused) Color(0xFF2C2C3C) else JetCardBg,
        border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
        modifier = modifier
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
                color = if (isSelected || isFocused) Color.White else activeNumberColor,
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
