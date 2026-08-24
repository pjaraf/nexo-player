package com.example.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import com.example.data.models.LiveChannel
import com.example.player.PlayerManager
import com.example.player.VlcPlayerView
import com.example.ui.components.CHANNEL_FALLBACK
import com.example.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay

private val TvLiveOrange = Color(0xFFDE5B17)
private val TvLiveOrangeBright = Color(0xFFFF7A00)
private val TvLiveRed = Color(0xFFE50914)
private val TvLiveSidebarBg = Color(0xFF141414)
private val TvLiveBackground = Color(0xFF0D0D0D)
private val TvLiveCardBg = Color(0xFF1A1A1A)
private val TvLiveTextMuted = Color(0xFF9E9E9E)

/**
 * Interfaz de TV en Vivo para Televisores:
 * Utiliza exactamente la misma interfaz y reproductor 16:9 que el teléfono,
 * adaptada a la vista de televisor con soporte completo para navegación con control remoto (D-Pad).
 */
@Composable
fun TvLiveScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit,
    onExitToMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
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

    // Mismo PlayerManager e instancia de VLC dedicada que la versión móvil
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
            Log.w("TvLiveScreen", "VLC Error candidato $candidateIndex: $error")
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

    LaunchedEffect(selectedChannel) {
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

    // Watchdog para el reproductor en vivo
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
    val categoryListState = rememberLazyListState()

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
        containerColor = TvLiveBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("tv_live_screen")
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // COLUMNA IZQUIERDA: MINI REPRODUCTOR 16:9 + INFO CANAL + PANTALLA COMPLETA
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Mini Reproductor 16:9 idéntico al teléfono con soporte de foco D-Pad
                var isPlayerFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black,
                    border = BorderStroke(
                        if (isPlayerFocused) 3.dp else 1.dp,
                        if (isPlayerFocused) Color(0xFFFF9800) else Color(0xFF2E2E2E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .onFocusChanged { isPlayerFocused = it.isFocused }
                        .focusable()
                        .clickable {
                            selectedChannel?.let { ch ->
                                onPlayChannel(ch.id, selectedCat ?: "ALL", ch.name)
                            }
                        }
                        .testTag("tv_live_player_preview_box")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        VlcPlayerView(
                            playerManager = playerManager,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isPlayerBuffering) {
                            CircularProgressIndicator(
                                color = TvLiveRed,
                                strokeWidth = 3.5.dp,
                                modifier = Modifier.size(42.dp).align(Alignment.Center)
                            )
                        }

                        if (playerHasError && !isPlayerBuffering) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Text(
                                    text = "Canal no disponible temporalmente",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Badge EN VIVO / Buffer en la esquina superior izquierda
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isPlayerBuffering) Color(0xFF422E10) else Color(0xFF1B5E20),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(if (isPlayerBuffering) TvLiveOrange else Color(0xFF4CAF50), CircleShape)
                                )
                                Text(
                                    text = if (isPlayerBuffering) "CONECTANDO" else "EN VIVO",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        // Botón flotante de pantalla completa
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    selectedChannel?.let { ch ->
                                        onPlayChannel(ch.id, selectedCat ?: "ALL", ch.name)
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = "Pantalla Completa",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Tarjeta Informativa del Canal Seleccionado
                selectedChannel?.let { ch ->
                    var isCardFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCardFocused) Color(0xFF22222E) else TvLiveCardBg,
                        border = BorderStroke(
                            if (isCardFocused) 2.dp else 1.dp,
                            if (isCardFocused) Color(0xFFFF9800) else Color(0xFF2C2C2C)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isCardFocused = it.isFocused }
                            .focusable()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ch.streamIcon?.takeIf { it.isNotBlank() } ?: CHANNEL_FALLBACK,
                                    contentDescription = ch.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ch.groupName.ifBlank { "Canal en Vivo" },
                                    color = TvLiveTextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val isFav = viewModel.isFavorite("live", ch.id)
                            IconButton(
                                onClick = { viewModel.toggleFavorite("live", ch.id, ch.name, ch.streamIcon) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorito",
                                    tint = if (isFav) Color(0xFFFFC107) else Color(0xFF888888),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Botón de Pantalla Completa destacado para Control Remoto
                var isFsBtnFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        selectedChannel?.let { ch ->
                            onPlayChannel(ch.id, selectedCat ?: "ALL", ch.name)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFsBtnFocused) TvLiveOrangeBright else TvLiveRed,
                        contentColor = Color.White
                    ),
                    border = if (isFsBtnFocused) BorderStroke(2.dp, Color.White) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .onFocusChanged { isFsBtnFocused = it.isFocused }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(text = "PANTALLA COMPLETA [OK]", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 4. Botón Volver al Menú
                if (onExitToMenu != null) {
                    var isExitFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = onExitToMenu,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isExitFocused) Color(0xFF2E2E3A) else Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(
                            if (isExitFocused) 2.dp else 1.dp,
                            if (isExitFocused) Color(0xFFFF9800) else Color(0xFF444444)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .onFocusChanged { isExitFocused = it.isFocused }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(text = "Volver al Menú", fontSize = 12.5.sp)
                        }
                    }
                }
            }

            // =========================================================================
            // COLUMNA DERECHA: PESTAÑAS + LISTA DE CATEGORÍAS + LISTA DE CANALES
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(0.52f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sub-Tabs: CATEGORÍA y FAVORITOS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    var isCatTabFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { activeTab = LiveSubTab.CATEGORIA },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == LiveSubTab.CATEGORIA) (if (isCatTabFocused) TvLiveOrangeBright else TvLiveRed) else (if (isCatTabFocused) Color(0xFF333344) else Color(0xFF202020)),
                            contentColor = Color.White
                        ),
                        border = if (isCatTabFocused) BorderStroke(2.dp, Color(0xFFFF9800)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .onFocusChanged { isCatTabFocused = it.isFocused }
                    ) {
                        Text(text = "CATEGORÍA", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    var isFavTabFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { activeTab = LiveSubTab.FAVORITOS },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == LiveSubTab.FAVORITOS) (if (isFavTabFocused) TvLiveOrangeBright else TvLiveRed) else (if (isFavTabFocused) Color(0xFF333344) else Color(0xFF202020)),
                            contentColor = Color.White
                        ),
                        border = if (isFavTabFocused) BorderStroke(2.dp, Color(0xFFFF9800)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .onFocusChanged { isFavTabFocused = it.isFocused }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                            Text(text = "FAVORITOS (${favoriteChannels.size})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }

                // Contenido según pestaña
                if (activeTab == LiveSubTab.CATEGORIA) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Lista de Categorías
                        Surface(
                            color = TvLiveSidebarBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .width(160.dp)
                                .fillMaxHeight()
                        ) {
                            LazyColumn(
                                state = categoryListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                item {
                                    val isSelected = selectedCat == "ALL"
                                    var isCatItemFocused by remember { mutableStateOf(false) }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCatItemFocused) Color(0xFF2E2E40) else if (isSelected) TvLiveRed else Color.Transparent,
                                        border = if (isCatItemFocused) BorderStroke(2.dp, Color(0xFFFF9800)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { isCatItemFocused = it.isFocused }
                                            .focusable()
                                            .clickable { viewModel.selectLiveCategory("ALL") }
                                    ) {
                                        Text(
                                            text = "TODOS",
                                            color = if (isSelected || isCatItemFocused) Color.White else TvLiveTextMuted,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isCatItemFocused) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                        )
                                    }
                                }

                                itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                                    val isSelected = selectedCat == cat.categoryId
                                    var isCatItemFocused by remember { mutableStateOf(false) }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCatItemFocused) Color(0xFF2E2E40) else if (isSelected) TvLiveRed else Color.Transparent,
                                        border = if (isCatItemFocused) BorderStroke(2.dp, Color(0xFFFF9800)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { isCatItemFocused = it.isFocused }
                                            .focusable()
                                            .clickable { viewModel.selectLiveCategory(cat.categoryId) }
                                    ) {
                                        Text(
                                            text = cat.categoryName.uppercase(),
                                            color = if (isSelected || isCatItemFocused) Color.White else TvLiveTextMuted,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isCatItemFocused) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Lista de Canales
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TvLiveBackground)
                        ) {
                            if (loading && channels.isEmpty()) {
                                CircularProgressIndicator(
                                    color = TvLiveRed,
                                    modifier = Modifier.size(36.dp).align(Alignment.Center)
                                )
                            } else if (filteredChannels.isEmpty()) {
                                Text(
                                    text = if (search.isNotBlank()) "Sin canales para \"$search\"" else "No hay canales",
                                    color = TvLiveTextMuted,
                                    fontSize = 13.5.sp,
                                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                                )
                            } else {
                                LazyColumn(
                                    state = channelListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
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
                                            isTv = true,
                                            onFavToggle = { viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon) },
                                            onClick = {
                                                if (isSelected) {
                                                    onPlayChannel(channel.id, selectedCat ?: "ALL", channel.name)
                                                } else {
                                                    selectedChannel = channel
                                                }
                                            },
                                            onDoubleClick = { onPlayChannel(channel.id, selectedCat ?: "ALL", channel.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Pestaña Favoritos
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TvLiveBackground)
                    ) {
                        if (favoriteChannels.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.StarBorder, contentDescription = null, tint = TvLiveTextMuted, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(text = "No tienes canales favoritos", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(text = "Selecciona la estrella en un canal para agregarlo a favoritos", color = TvLiveTextMuted, fontSize = 12.5.sp, textAlign = TextAlign.Center)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(favoriteChannels, key = { index, ch -> "${ch.id}_$index" }) { index, channel ->
                                    val isSelected = selectedChannel?.id == channel.id
                                    ChannelListItemJetGo(
                                        index = index + 1,
                                        channel = channel,
                                        isSelected = isSelected,
                                        isFav = true,
                                        isTv = true,
                                        onFavToggle = { viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon) },
                                        onClick = {
                                            if (isSelected) {
                                                onPlayChannel(channel.id, selectedCat ?: "ALL", channel.name)
                                            } else {
                                                selectedChannel = channel
                                            }
                                        },
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
}
