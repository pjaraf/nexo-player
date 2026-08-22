import re

with open("app/src/main/java/com/example/ui/screens/LiveScreen.kt", "r") as f:
    content = f.read()

start_marker = "// =========================================================================\n// INTERFAZ PARA TELEVISORES (SMART TV / ANDROID TV / TV BOX - D-PAD NATIVO)\n// ========================================================================="
end_marker = "// =========================================================================\n// INTERFAZ PARA TELÉFONOS (MÓVIL / MINI REPRODUCTOR 16:9 AJUSTADO)\n// ========================================================================="

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx == -1 or end_idx == -1:
    print(f"Markers not found! start_idx={start_idx}, end_idx={end_idx}")
    exit(1)

new_tv_section = """// =========================================================================
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
    var showLateralMenu by remember { mutableStateOf(false) }
    var showChannelBanner by remember { mutableStateOf(true) }
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

    // Auto-hide lateral menu after 8 seconds of inactivity
    LaunchedEffect(showLateralMenu, lastInteractionTime) {
        if (showLateralMenu) {
            delay(8000)
            showLateralMenu = false
        }
    }

    // Function to zap next / previous channel
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
        lastInteractionTime = System.currentTimeMillis()
    }

    // Back button handling
    BackHandler(enabled = true) {
        if (showLateralMenu) {
            showLateralMenu = false
            lastInteractionTime = System.currentTimeMillis()
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
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    lastInteractionTime = System.currentTimeMillis()
                    val nativeKeyCode = keyEvent.nativeKeyEvent.keyCode
                    when {
                        // When lateral menu is CLOSED:
                        !showLateralMenu -> {
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
                                AndroidKeyEvent.KEYCODE_MENU -> {
                                    showLateralMenu = true
                                    showChannelBanner = false
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    showChannelBanner = true
                                    true
                                }
                                else -> false
                            }
                        }
                        // When lateral menu is OPEN:
                        else -> {
                            when (nativeKeyCode) {
                                AndroidKeyEvent.KEYCODE_MENU -> {
                                    showLateralMenu = false
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                } else false
            }
            .clickable {
                showLateralMenu = !showLateralMenu
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

        // 4. TV Lower-Third OSD Channel Banner (appears when zapping / changing channel)
        AnimatedVisibility(
            visible = showChannelBanner && !showLateralMenu,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            val currentIdx = displayChannels.indexOfFirst { it.id == selectedChannel?.id }
            val chNumber = if (currentIdx >= 0) String.format("%03d", currentIdx + 1) else "---"
            val categoryLabel = categories.find { it.categoryId == selectedCat }?.categoryName ?: "TODOS"

            Surface(
                color = Color(0xF0121212),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFF333333)),
                shadowElevation = 12.dp,
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = selectedChannel?.name ?: "Canal en Vivo",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // LIVE Badge
                            Surface(
                                color = PhoneRed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "EN VIVO",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Categoría: $categoryLabel • Presiona [OK] o [←] para ver categorías y canales",
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp
                        )
                    }

                    // Favorite Button
                    selectedChannel?.let { ch ->
                        val isFav = favorites.any { it.kind == "live" && it.id == ch.id }
                        IconButton(
                            onClick = {
                                viewModel.toggleFavorite("live", ch.id, ch.name, ch.streamIcon)
                            }
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorito",
                                tint = if (isFav) Color(0xFFFFC107) else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Lateral Overlay: Categories + Channels Lists (COLLAPSIBLE / QUE SE OCULTA)
        AnimatedVisibility(
            visible = showLateralMenu,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterStart)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(580.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xF80E0E0E),
                                Color(0xF4141414),
                                Color(0xE8141414),
                                Color(0xB0101010),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(vertical = 20.dp, horizontal = 16.dp)
            ) {
                // COLUMNA 1: CATEGORÍAS (Lateral Ocultable)
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF161616).copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "CATEGORÍAS",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )

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
                                containerColor = if (activeTvTab == LiveSubTab.CATEGORIA) JetOrange else Color(0xFF242424),
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
                                containerColor = if (activeTvTab == LiveSubTab.FAVORITOS) JetOrange else Color(0xFF242424),
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
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) JetOrange else if (isFocused) Color(0xFF2A2A38) else Color.Transparent,
                                    border = if (isFocused && !isSelected) BorderStroke(1.5.dp, Color.White) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isFocused = it.isFocused }
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
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) JetOrange else if (isFocused) Color(0xFF2A2A38) else Color.Transparent,
                                    border = if (isFocused && !isSelected) BorderStroke(1.5.dp, Color.White) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isFocused = it.isFocused }
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

                // COLUMNA 2: LISTA DE CANALES (Lateral Ocultable)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF161616).copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    val currentCatName = if (activeTvTab == LiveSubTab.FAVORITOS) {
                        "FAVORITOS (${favoriteChannels.size})"
                    } else {
                        categories.find { it.categoryId == selectedCat }?.categoryName?.uppercase() ?: "TODOS LOS CANALES"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentCatName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${displayChannels.size} canales",
                            color = JetOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (loading && channels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = JetOrange, modifier = Modifier.size(36.dp))
                        }
                    } else if (displayChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No hay canales disponibles",
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
                                        showLateralMenu = false
                                        showChannelBanner = true
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onDoubleClick = {
                                        selectedChannel = channel
                                        showLateralMenu = false
                                        showChannelBanner = true
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onFavToggle = {
                                        viewModel.toggleFavorite("live", channel.id, channel.name, channel.streamIcon)
                                        lastInteractionTime = System.currentTimeMillis()
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
"""

content = content[:start_idx] + new_tv_section + "\n" + content[end_idx:]

with open("app/src/main/java/com/example/ui/screens/LiveScreen.kt", "w") as f:
    f.write(content)

print("Successfully replaced TvLiveScreen")
