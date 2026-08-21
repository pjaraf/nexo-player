package com.example.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.LiveCategory
import com.example.data.models.LiveChannel
import com.example.player.PlayerManager
import com.example.player.VlcPlayerView
import com.example.ui.components.BreakingNewsTvBanner
import com.example.ui.components.CHANNEL_FALLBACK
import com.example.ui.components.ScreenCastDialog
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Palette matching the JetGo reference image
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

@Composable
fun LiveScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit,
    onExitToMenu: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }

    if (isTv) {
        // --- TELEVISION LAYOUT: Fullscreen Live TV with Remote Control-activated Floating Windows ---
        TvLiveFullscreenScreen(
            viewModel = viewModel,
            onExitToMenu = onExitToMenu
        )
    } else {
        // --- MOBILE / PHONE LAYOUT: Untouched standard layout ---
        PhoneLiveScreen(
            viewModel = viewModel,
            onPlayChannel = onPlayChannel
        )
    }
}

/**
 * TELEVISION FULLSCREEN LIVE TV
 * - Starts in 100% full screen video
 * - Pressing DPAD_LEFT (hacia el lado con el control remoto) or OK opens the floating Categories & Channels windows
 * - Full D-Pad Remote Control compatibility (DPAD_LEFT, DPAD_RIGHT, DPAD_UP, DPAD_DOWN, OK, BACK, CH+/CH-)
 */
@Composable
private fun TvLiveFullscreenScreen(
    viewModel: MainViewModel,
    onExitToMenu: (() -> Unit)?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val categories by viewModel.liveCategories.collectAsState()
    val selectedCat by viewModel.selectedLiveCat.collectAsState()
    val channels by viewModel.liveChannels.collectAsState()
    val search by viewModel.liveSearch.collectAsState()
    val loading by viewModel.liveLoading.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()

    // Guide visibility mode: HIDDEN (0 presses), CHANNELS_ONLY (1 press), BOTH_CATEGORIES_AND_CHANNELS (2 presses)
    var guideMode by remember { mutableStateOf(0) } // 0 = oculto, 1 = solo canales, 2 = categorías y canales
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var isPlayerBuffering by remember { mutableStateOf(false) }
    var playerHasError by remember { mutableStateOf(false) }
    var isFavoritesCategorySelected by remember { mutableStateOf(false) }
    var showOsdBanner by remember { mutableStateOf(false) }
    var showRemoteHint by remember { mutableStateOf(true) }

    val rootFocusRequester = remember { FocusRequester() }
    val categoriesFocusRequester = remember { FocusRequester() }
    val channelsFocusRequester = remember { FocusRequester() }

    val categoriesListState = rememberLazyListState()
    val channelsListState = rememberLazyListState()

    // Embedded Fullscreen VLC Player Instance
    val playerManager = remember { PlayerManager(context) }

    DisposableEffect(playerManager) {
        playerManager.onBuffering = { buffering, _ ->
            isPlayerBuffering = buffering
        }
        playerManager.onPlayingChanged = { playing ->
            if (playing) {
                isPlayerBuffering = false
                playerHasError = false
            }
        }
        playerManager.onError = {
            playerHasError = true
            isPlayerBuffering = false
        }

        onDispose {
            playerManager.release()
        }
    }

    // Play selected channel inside full screen player with debounce for smooth fast remote zapping
    LaunchedEffect(selectedChannel) {
        val ch = selectedChannel
        if (ch != null) {
            showOsdBanner = true
            isPlayerBuffering = true
            playerHasError = false
            // Short debounce to permit fast remote channel browsing without flooding native LibVLC decoder
            delay(180)
            val candidates = XtreamApi.getLiveStreamCandidates(ch.id)
            val primaryUrl = ch.directStreamUrl?.takeIf { it.isNotBlank() }
                ?: candidates.firstOrNull()
                ?: XtreamApi.getLiveStreamUrl(ch.id)

            try {
                playerHasError = false
                isPlayerBuffering = true
                playerManager.play(primaryUrl, 0L)
            } catch (e: Exception) {
                // Fallback attempt with alternate stream candidate if available
                val altUrl = candidates.getOrNull(1)
                if (altUrl != null && altUrl != primaryUrl) {
                    try {
                        playerManager.play(altUrl, 0L)
                    } catch (_: Exception) {
                        playerHasError = true
                        isPlayerBuffering = false
                    }
                } else {
                    playerHasError = true
                    isPlayerBuffering = false
                }
            }
        }
    }

    // Auto-hide OSD banner after 4 seconds
    LaunchedEffect(showOsdBanner) {
        if (showOsdBanner) {
            delay(4000)
            showOsdBanner = false
        }
    }

    // Auto-hide initial remote control hint after 5 seconds
    LaunchedEffect(showRemoteHint) {
        if (showRemoteHint) {
            delay(5000)
            showRemoteHint = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLiveCategories()
        // Request initial focus on root container for remote control keys
        try {
            rootFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Filter channels according to category / favorites / search
    val displayedChannels = remember(channels, search, isFavoritesCategorySelected, favorites) {
        val baseList = if (isFavoritesCategorySelected) {
            val favIds = favorites.filter { it.kind == "live" }.map { it.id }.toSet()
            channels.filter { favIds.contains(it.id) }
        } else {
            channels
        }

        if (search.isBlank()) {
            baseList
        } else {
            val q = search.trim().lowercase()
            baseList.filter { it.name.lowercase().contains(q) || it.groupName.lowercase().contains(q) }
        }
    }

    // Select first channel automatically when data loads
    LaunchedEffect(displayedChannels) {
        if (selectedChannel == null && displayedChannels.isNotEmpty()) {
            selectedChannel = displayedChannels.first()
        }
    }

    // Scroll to current channel when guide opens
    LaunchedEffect(guideMode) {
        if (guideMode == 1) {
            val idx = displayedChannels.indexOfFirst { it.id == selectedChannel?.id }
            if (idx >= 0) {
                channelsListState.scrollToItem(idx)
            }
            try {
                channelsFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else if (guideMode == 2) {
            try {
                categoriesFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Remote Control Key Handler: Channel Zapping & Guide Activation
    fun zapChannel(direction: Int) {
        if (displayedChannels.isEmpty()) return
        val currentIndex = displayedChannels.indexOfFirst { it.id == selectedChannel?.id }
        val newIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex + direction + displayedChannels.size) % displayedChannels.size
        }
        selectedChannel = displayedChannels[newIndex]
        showOsdBanner = true
    }

    // Remote Control Back Handler
    BackHandler(enabled = true) {
        if (guideMode == 2) {
            guideMode = 1 // De categorías vuelve a canales
            coroutineScope.launch {
                try { channelsFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        } else if (guideMode == 1) {
            guideMode = 0 // De canales oculta la guía y vuelve a pantalla completa
        } else {
            onExitToMenu?.invoke()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val code = keyEvent.nativeKeyEvent.keyCode
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            when (guideMode) {
                                0 -> {
                                    // 1er toque: Abre SOLO Canales
                                    guideMode = 1
                                    true
                                }
                                1 -> {
                                    // 2do toque: Abre Categorías
                                    guideMode = 2
                                    true
                                }
                                else -> false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            when (guideMode) {
                                2 -> {
                                    // Desde Categorías, hacia la derecha va a Canales
                                    guideMode = 1
                                    coroutineScope.launch {
                                        try { channelsFocusRequester.requestFocus() } catch (_: Exception) {}
                                    }
                                    true
                                }
                                1 -> {
                                    // Desde Canales, hacia la derecha cierra la guía
                                    guideMode = 0
                                    true
                                }
                                else -> false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (guideMode == 0) {
                                guideMode = 1
                                true
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                            zapChannel(1)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            zapChannel(-1)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (guideMode == 0) {
                                zapChannel(1)
                                true
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (guideMode == 0) {
                                zapChannel(-1)
                                true
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_MENU -> {
                            guideMode = if (guideMode == 0) 1 else 0
                            true
                        }
                        AndroidKeyEvent.KEYCODE_BACK -> {
                            if (guideMode == 2) {
                                guideMode = 1
                                coroutineScope.launch {
                                    try { channelsFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                                true
                            } else if (guideMode == 1) {
                                guideMode = 0
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .testTag("tv_live_fullscreen_container")
    ) {
        // --- 1. FULLSCREEN VLC VIDEO BACKGROUND (100% Pantalla Completa) ---
        VlcPlayerView(
            playerManager = playerManager,
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    // Clicking screen toggles channels
                    guideMode = when (guideMode) {
                        0 -> 1
                        1 -> 2
                        else -> 0
                    }
                }
        )

        // Buffering Indicator
        if (isPlayerBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = NexusPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Cargando transmisión...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Error message if stream failed
        if (playerHasError && !isPlayerBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, NexusPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = NexusPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Transmisión no disponible temporalmente",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Presiona ◀ en el control remoto para ver otros canales",
                        color = Color(0xFFA0A0AB),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // --- 2. REMOTE CONTROL HELPER PILL (Appears on entry or when guide is closed) ---
        AnimatedVisibility(
            visible = showRemoteHint && guideMode == 0,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F0F16).copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NexusPrimary
                    ) {
                        Text(
                            text = "◀",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Presiona 1 vez (◀) para Canales | 2 veces (◀◀) para Categorías",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- 3. SEPARATED FLOATING OVERLAY WINDOWS: CATEGORÍAS (2 toques) & CANALES (1 toque) ---
        AnimatedVisibility(
            visible = guideMode > 0,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==========================================================
                // VENTANA FLOTANTE 1: CATEGORÍAS (Aparece con 2do toque / guideMode == 2)
                // ==========================================================
                AnimatedVisibility(
                    visible = guideMode == 2,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Surface(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                            .shadow(24.dp, RoundedCornerShape(24.dp))
                            .testTag("floating_tv_categories_window"),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF101018).copy(alpha = 0.96f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            // Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = null,
                                    tint = NexusPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "CATEGORÍAS",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                )
                            }

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            LazyColumn(
                                state = categoriesListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(categoriesFocusRequester),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // "TODOS LOS CANALES" item
                                item {
                                    val isSelected = selectedCat == "ALL" && !isFavoritesCategorySelected
                                    TvCategoryFloatingItem(
                                        title = "TODOS LOS CANALES",
                                        icon = Icons.Outlined.Tv,
                                        isSelected = isSelected,
                                        onClick = {
                                            isFavoritesCategorySelected = false
                                            viewModel.selectLiveCategory("ALL")
                                            coroutineScope.launch {
                                                try { channelsFocusRequester.requestFocus() } catch (_: Exception) {}
                                            }
                                        }
                                    )
                                }

                                // "FAVORITOS" item
                                item {
                                    val favCount = favorites.count { it.kind == "live" }
                                    TvCategoryFloatingItem(
                                        title = "FAVORITOS ($favCount)",
                                        icon = Icons.Default.Star,
                                        isSelected = isFavoritesCategorySelected,
                                        iconTint = Color(0xFFFFC107),
                                        onClick = {
                                            isFavoritesCategorySelected = true
                                            coroutineScope.launch {
                                                try { channelsFocusRequester.requestFocus() } catch (_: Exception) {}
                                            }
                                        }
                                    )
                                }

                                // Categories from Server
                                itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                                    val isSelected = selectedCat == cat.categoryId && !isFavoritesCategorySelected
                                    TvCategoryFloatingItem(
                                        title = cat.categoryName.uppercase(),
                                        icon = Icons.Default.LiveTv,
                                        isSelected = isSelected,
                                        onClick = {
                                            isFavoritesCategorySelected = false
                                            viewModel.selectLiveCategory(cat.categoryId)
                                            coroutineScope.launch {
                                                try { channelsFocusRequester.requestFocus() } catch (_: Exception) {}
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================================
                // VENTANA FLOTANTE 2: CANALES (Aparece con 1er toque / guideMode >= 1)
                // ==========================================================
                Surface(
                    modifier = Modifier
                        .width(380.dp)
                        .fillMaxHeight()
                        .shadow(24.dp, RoundedCornerShape(24.dp))
                        .testTag("floating_tv_channels_window"),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF141422).copy(alpha = 0.96f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        // Header info
                        val activeCategoryTitle = if (isFavoritesCategorySelected) {
                            "Favoritos"
                        } else if (selectedCat == "ALL") {
                            "Todos los canales"
                        } else {
                            categories.find { it.categoryId == selectedCat }?.categoryName ?: "Canales"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeCategoryTitle.uppercase(),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NexusPrimary.copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NexusPrimary)
                            ) {
                                Text(
                                    text = "${displayedChannels.size} canales",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Search Bar inside floating channels window
                        OutlinedTextField(
                            value = search,
                            onValueChange = { viewModel.setLiveSearch(it) },
                            placeholder = {
                                Text(
                                    text = "Buscar canal...",
                                    color = Color(0xFF888899),
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    tint = Color(0xFF888899),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (search.isNotBlank()) {
                                    IconButton(onClick = { viewModel.setLiveSearch("") }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Limpiar",
                                            tint = Color(0xFF888899),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NexusPrimary,
                                unfocusedBorderColor = Color(0xFF2A2A38),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0F0F17),
                                unfocusedContainerColor = Color(0xFF0F0F17)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .height(46.dp)
                        )

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // Channel List
                        if (loading && channels.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = NexusPrimary, modifier = Modifier.size(32.dp))
                            }
                        } else if (displayedChannels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (search.isNotBlank()) "Sin canales para \"$search\"" else "No hay canales en esta categoría",
                                    color = Color(0xFF888899),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = channelsListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(channelsFocusRequester),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(displayedChannels, key = { index, ch -> "${ch.id}_$index" }) { index, channel ->
                                    val isCurrentPlaying = selectedChannel?.id == channel.id
                                    val isFav = viewModel.isFavorite("live", channel.id)

                                    TvChannelFloatingItem(
                                        index = index + 1,
                                        channel = channel,
                                        isCurrentPlaying = isCurrentPlaying,
                                        isFav = isFav,
                                        onFavToggle = {
                                            viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon)
                                        },
                                        onClick = {
                                            selectedChannel = channel
                                            // Pressing a channel plays it and automatically hides guide to full screen video
                                            guideMode = 0
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Space for closing the guide
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { guideMode = 0 }
                )
            }
        }

        // --- 4. BOTTOM OSD BANNER (Appears when channel changes or when in full screen) ---
        AnimatedVisibility(
            visible = showOsdBanner && guideMode == 0,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(260)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = androidx.compose.animation.core.tween(300)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(220)) + slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = androidx.compose.animation.core.tween(250)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 28.dp)
                .testTag("tv_breaking_news_live_banner")
        ) {
            val currentChIndex = displayedChannels.indexOfFirst { it.id == selectedChannel?.id }
            val chNum = com.example.data.models.cleanId(selectedChannel?.num).takeIf { it.isNotBlank() } ?: if (currentChIndex >= 0) "${currentChIndex + 1}" else "1"
            val catTitle = selectedChannel?.groupName?.takeIf { it.isNotBlank() }
                ?: if (isFavoritesCategorySelected) "FAVORITOS"
                else (categories.find { it.categoryId == selectedCat }?.categoryName ?: "EN VIVO")

            BreakingNewsTvBanner(
                channelName = selectedChannel?.name ?: "CANAL EN VIVO",
                channelNumber = chNum,
                categoryName = catTitle,
                channelLogoUrl = selectedChannel?.streamIcon,
                directionLabel = null,
                totalChannels = displayedChannels.size,
                currentIndex = if (currentChIndex >= 0) currentChIndex + 1 else 1
            )
        }
    }
}

/**
 * Floating Category Item for TV Remote Navigation
 */
@Composable
private fun TvCategoryFloatingItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    iconTint: Color = Color.White,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = when {
        isFocused -> TvFocusBlue
        isSelected -> TvSelectedRed
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = if (isFocused) {
            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
        } else if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Floating Channel Item for TV Remote Navigation
 */
@Composable
private fun TvChannelFloatingItem(
    index: Int,
    channel: LiveChannel,
    isCurrentPlaying: Boolean,
    isFav: Boolean,
    onFavToggle: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val formattedIndex = String.format("%03d", index)

    val bg = when {
        isFocused -> TvFocusBlue
        isCurrentPlaying -> TvSelectedRed
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = if (isFocused) {
            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
        } else if (isCurrentPlaying) {
            androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Logo inside mini box
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B1B26))
                    .border(1.dp, if (isCurrentPlaying) NexusPrimary else Color(0xFF2C2C3C), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                )
            }

            // Number
            Text(
                text = formattedIndex,
                color = if (isCurrentPlaying) Color(0xFFFFC107) else Color(0xFF888899),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            // Name
            Text(
                text = channel.name.uppercase(),
                color = if (isCurrentPlaying) Color.White else Color(0xFFDDDDDD),
                fontSize = 13.sp,
                fontWeight = if (isCurrentPlaying || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Playing Pulse Badge if current
            if (isCurrentPlaying) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NexusPrimary
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

            // Fav Button
            IconButton(
                onClick = onFavToggle,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (isFav) Color(0xFFFFC107) else Color(0xFF666677),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * MOBILE / PHONE LIVE SCREEN (UNTOUCHED)
 */
@Composable
private fun PhoneLiveScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit
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
        playerManager.onError = {
            playerHasError = true
            isPlayerBuffering = false
        }

        onDispose {
            playerManager.release()
        }
    }

    // Play selected channel inside the embedded preview with debounce
    LaunchedEffect(selectedChannel) {
        val ch = selectedChannel
        if (ch != null) {
            isPlayerBuffering = true
            playerHasError = false
            delay(180)
            val candidates = XtreamApi.getLiveStreamCandidates(ch.id)
            val primaryUrl = ch.directStreamUrl?.takeIf { it.isNotBlank() }
                ?: candidates.firstOrNull()
                ?: XtreamApi.getLiveStreamUrl(ch.id)

            try {
                playerHasError = false
                isPlayerBuffering = true
                playerManager.play(primaryUrl, 0L)
            } catch (e: Exception) {
                val altUrl = candidates.getOrNull(1)
                if (altUrl != null && altUrl != primaryUrl) {
                    try {
                        playerManager.play(altUrl, 0L)
                    } catch (_: Exception) {
                        playerHasError = true
                        isPlayerBuffering = false
                    }
                } else {
                    playerHasError = true
                    isPlayerBuffering = false
                }
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
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.setLiveSearch(it) },
                placeholder = {
                    Text(
                        text = "Búsqueda por canal",
                        color = JetTextMuted,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = JetTextMuted,
                        modifier = Modifier.size(20.dp)
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
                    unfocusedBorderColor = Color(0xFF2A2A2A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = JetCardBg,
                    unfocusedContainerColor = JetCardBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp)
                    .height(48.dp)
                    .testTag("live_search_input")
            )

            // --- 2. Live Video Player Preview Box ---
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
                    .testTag("live_player_preview_box"),
                contentAlignment = Alignment.Center
            ) {
                // Video Surface
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

                // Control Buttons (Fullscreen and Cast to TV)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cast Screen to TV Button
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

                    // Expand Fullscreen button
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // CATEGORÍA Button
                Button(
                    onClick = { activeTab = LiveSubTab.CATEGORIA },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == LiveSubTab.CATEGORIA) JetOrange else Color(0xFF202020),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("tab_categoria")
                ) {
                    Text(
                        text = "CATEGORÍA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // FAVORITOS Button
                Button(
                    onClick = { activeTab = LiveSubTab.FAVORITOS },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == LiveSubTab.FAVORITOS) JetOrange else Color(0xFF202020),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("tab_favoritos")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                        Text(
                            text = "FAVORITOS (${favoriteChannels.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
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
                            .width(130.dp)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            // "TODOS LOS CANALES" Category item
                            item {
                                val isSelected = selectedCat == "ALL"
                                Surface(
                                    color = if (isSelected) JetOrange else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectLiveCategory("ALL") }
                                        .testTag("live_cat_all")
                                ) {
                                    Text(
                                        text = "TODOS",
                                        color = if (isSelected) Color.White else JetTextMuted,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // Dynamic categories from server
                            itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                                val isSelected = selectedCat == cat.categoryId
                                Surface(
                                    color = if (isSelected) JetOrange else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectLiveCategory(cat.categoryId) }
                                        .testTag("live_cat_${cat.categoryId}")
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
 * Mobile Channel Item
 */
@Composable
private fun ChannelListItemJetGo(
    index: Int,
    channel: LiveChannel,
    isSelected: Boolean,
    isFav: Boolean,
    onFavToggle: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    val formattedIndex = String.format("%03d", index)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) JetOrange else JetCardBg,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
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
                color = if (isSelected) Color.White else JetOrangeBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp)
            )

            // Channel Icon
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Channel Name
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Favorite Star
            IconButton(
                onClick = onFavToggle,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (isFav) Color(0xFFFFC107) else Color(0xFF666666),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
