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
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

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
 * - SMART TV / TV BOX (isTv = true): Reproducción a pantalla completa con guía rápida emergente (OSD), cambio de canales con D-Pad y soporte completo de control remoto.
 * - TELÉFONOS (isTv = false): Interfaz para móviles con mini reproductor 16:9 y lista de canales.
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
// INTERFAZ PARA SMART TV / TV BOX (REPRODUCCIÓN COMPLETA + GUÍA RÁPIDA OSD)
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

    var activeTab by remember { mutableStateOf(LiveSubTab.CATEGORIA) }
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var showQuickGuide by remember { mutableStateOf(false) }
    var showOsdBanner by remember { mutableStateOf(false) }
    var directionLabel by remember { mutableStateOf<String?>(null) }

    var candidateIndex by remember { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastZapTime by remember { mutableLongStateOf(0L) }

    val playerManager = remember { PlayerManager(context) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val rootFocusRequester = remember { FocusRequester() }
    val channelListState = rememberLazyListState()

    val displayChannels = remember(channels, activeTab, favorites) {
        if (activeTab == LiveSubTab.FAVORITOS) {
            val favIds = favorites.filter { it.kind == "live" }.map { it.id }.toSet()
            channels.filter { favIds.contains(it.id) }
        } else {
            channels
        }
    }

    // Inicializar reproductor VLC
    DisposableEffect(lifecycleOwner, playerManager) {
        playerManager.setAspectRatio(null) // Ajuste nativo de pantalla completa
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
            Log.w("TvLiveScreen", "VLC Error candidato $candidateIndex: $error")
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

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    try { playerManager.pause() } catch (_: Throwable) {}
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try { playerManager.resume() } catch (_: Throwable) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerManager.release()
        }
    }

    // Cargar categorías si no están cargadas
    LaunchedEffect(Unit) {
        viewModel.loadLiveCategories()
    }

    // Seleccionar automáticamente el primer canal si no hay ninguno seleccionado
    LaunchedEffect(displayChannels) {
        if (selectedChannel == null && displayChannels.isNotEmpty()) {
            selectedChannel = displayChannels.first()
        }
    }

    // Cargar y reproducir stream cuando cambia selectedChannel
    LaunchedEffect(selectedChannel) {
        val ch = selectedChannel
        if (ch != null) {
            isBuffering = true
            hasError = false
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
            candidates = list

            if (list.isNotEmpty()) {
                try {
                    hasError = false
                    isBuffering = true
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

    // Auto-ocultar banner OSD después de 4 segundos
    LaunchedEffect(showOsdBanner, selectedChannel) {
        if (showOsdBanner) {
            delay(4000)
            showOsdBanner = false
            directionLabel = null
        }
    }

    // Foco automático en el root cuando no está abierta la guía
    LaunchedEffect(showQuickGuide) {
        if (!showQuickGuide) {
            delay(150)
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Función de zapping con control remoto
    fun zapChannel(delta: Int, label: String) {
        if (displayChannels.isEmpty()) return
        val currentIndex = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
        val nextIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex + delta + displayChannels.size) % displayChannels.size
        }
        selectedChannel = displayChannels[nextIndex]
        directionLabel = label
        showOsdBanner = true
        lastZapTime = System.currentTimeMillis()
    }

    // Interceptar tecla ATRÁS en TV
    BackHandler(enabled = true) {
        if (showQuickGuide) {
            showQuickGuide = false
        } else {
            onExitToMenu?.invoke()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_PAGE_UP -> {
                            if (!showQuickGuide) {
                                zapChannel(-1, "▲ Canal Anterior")
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_PAGE_DOWN -> {
                            if (!showQuickGuide) {
                                zapChannel(1, "▼ Canal Siguiente")
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                            if (!showQuickGuide) {
                                showQuickGuide = true
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!showQuickGuide) {
                                showQuickGuide = true
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE -> {
                            if (showQuickGuide) {
                                showQuickGuide = false
                                true
                            } else {
                                onExitToMenu?.invoke()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .testTag("tv_live_root")
    ) {
        // 1. Reproductor de video de fondo a pantalla completa
        VlcPlayerView(
            playerManager = playerManager,
            modifier = Modifier.fillMaxSize()
        )

        // Indicador de buffering o error en pantalla completa
        if (isBuffering && !showQuickGuide && !showOsdBanner) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = JetOrange, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Text(text = "Cargando señal en vivo...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 2. Banner OSD Inferior (aparece al cambiar de canal o zappear)
        AnimatedVisibility(
            visible = showOsdBanner && !showQuickGuide,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            val currentIndex = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
            val formattedIndex = if (currentIndex >= 0) String.format("%03d", currentIndex + 1) else "---"
            val categoryName = categories.find { it.categoryId == selectedCat }?.categoryName ?: "TODOS"

            ModernTvOsdBanner(
                channelNumber = formattedIndex,
                channelName = selectedChannel?.name ?: "Canal en Vivo",
                categoryName = categoryName,
                channelLogoUrl = selectedChannel?.streamIcon,
                directionLabel = directionLabel,
                isBuffering = isBuffering
            )
        }

        // 3. Guía Rápida Lateral / Overlay con D-Pad (se abre con [OK] o [Izquierda])
        AnimatedVisibility(
            visible = showQuickGuide,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it / 3 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 3 }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.94f),
                                Color.Black.copy(alpha = 0.82f),
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
                        .padding(start = 24.dp, top = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Columna 1: Categorías
                    Surface(
                        color = Color(0xFF141414).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E2E3E)),
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            Text(
                                text = "CATEGORÍAS",
                                color = JetOrangeBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )

                            // Tabs: CATEGORÍAS & FAVORITOS
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    onClick = { activeTab = LiveSubTab.CATEGORIA },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (activeTab == LiveSubTab.CATEGORIA) JetOrange else Color(0xFF242424),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("CANALES", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Surface(
                                    onClick = { activeTab = LiveSubTab.FAVORITOS },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (activeTab == LiveSubTab.FAVORITOS) JetOrange else Color(0xFF242424),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("FAV (${favorites.count { it.kind == "live" }})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (activeTab == LiveSubTab.CATEGORIA) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    item {
                                        var isFocused by remember { mutableStateOf(false) }
                                        val isSelected = selectedCat == "ALL"

                                        Surface(
                                            onClick = { viewModel.selectLiveCategory("ALL") },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isFocused) Color(0xFF2C2C3E) else if (isSelected) JetOrange else Color.Transparent,
                                            border = if (isFocused) BorderStroke(2.dp, Color(0xFFFF9800)) else null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isFocused = it.isFocused }
                                        ) {
                                            Text(
                                                text = "TODOS",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            )
                                        }
                                    }

                                    itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                                        var isFocused by remember { mutableStateOf(false) }
                                        val isSelected = selectedCat == cat.categoryId

                                        Surface(
                                            onClick = { viewModel.selectLiveCategory(cat.categoryId) },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isFocused) Color(0xFF2C2C3E) else if (isSelected) JetOrange else Color.Transparent,
                                            border = if (isFocused) BorderStroke(2.dp, Color(0xFFFF9800)) else null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isFocused = it.isFocused }
                                        ) {
                                            Text(
                                                text = cat.categoryName.uppercase(),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Mostrando lista de favoritos a la derecha",
                                        color = JetTextMuted,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Columna 2: Lista de Canales
                    Surface(
                        color = Color(0xFF141414).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E2E3E)),
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CANALES (${displayChannels.size})",
                                    color = JetOrangeBright,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "[ATRÁS] para cerrar guía",
                                    color = JetTextMuted,
                                    fontSize = 10.5.sp
                                )
                            }

                            if (loading && channels.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = JetOrange, modifier = Modifier.size(32.dp))
                                }
                            } else if (displayChannels.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (activeTab == LiveSubTab.FAVORITOS) "No hay canales favoritos" else "No hay canales disponibles",
                                        color = JetTextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = channelListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    itemsIndexed(displayChannels, key = { index, ch -> "${ch.id}_$index" }) { index, channel ->
                                        val isPlaying = selectedChannel?.id == channel.id
                                        val isFav = viewModel.isFavorite("live", channel.id)

                                        ModernTvChannelItem(
                                            channel = channel,
                                            index = index + 1,
                                            isPlaying = isPlaying,
                                            isFav = isFav,
                                            onSelect = {
                                                selectedChannel = channel
                                                showQuickGuide = false
                                                showOsdBanner = true
                                            },
                                            onFavToggle = {
                                                viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon)
                                            },
                                            onNavigateUpToCategory = {
                                                // opcional navegación
                                            },
                                            onDismissGuide = {
                                                showQuickGuide = false
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
    }
}

// =========================================================================
// COMPONENTES MODERNOS PARA TV
// =========================================================================
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
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> Color(0xFF2C2C3E)
            isPlaying -> JetOrange
            else -> Color(0xFF1E1E1E)
        },
        border = when {
            isFocused -> BorderStroke(2.5.dp, Color(0xFFFF9800))
            isPlaying -> BorderStroke(1.5.dp, JetOrangeBright)
            else -> null
        },
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                            onSelect()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .testTag(channel.id)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = formattedIndex,
                color = if (isFocused || isPlaying) Color.White else JetOrangeBright,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp)
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = if (isPlaying || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isPlaying) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1B5E20)
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
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (isFav) Color(0xFFFFC107) else Color(0xFF666666),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ModernTvOsdBanner(
    channelNumber: String,
    channelName: String,
    categoryName: String,
    channelLogoUrl: String?,
    directionLabel: String?,
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    val currentTime = remember {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date())
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101014).copy(alpha = 0.92f),
        border = BorderStroke(1.5.dp, Color(0xFF2E2E3E)),
        modifier = modifier
            .widthIn(min = 460.dp, max = 680.dp)
            .height(84.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = JetOrange,
                modifier = Modifier.height(44.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channelNumber,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channelLogoUrl?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                    contentDescription = channelName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channelName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF242424)
                    ) {
                        Text(
                            text = categoryName.uppercase(),
                            color = JetOrangeBright,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (directionLabel != null) {
                        Text(
                            text = directionLabel,
                            color = JetTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (isBuffering) {
                CircularProgressIndicator(
                    color = JetOrange,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
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
    var candidateIndex by remember { mutableIntStateOf(0) }
    var channelCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Embedded VLC Instance for phone preview
    val playerManager = remember { PlayerManager(context) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, playerManager) {
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

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    try { playerManager.pause() } catch (_: Throwable) {}
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try { playerManager.resume() } catch (_: Throwable) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
    }
}

@Composable
internal fun ChannelListItemJetGo(
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
