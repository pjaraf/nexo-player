import re

with open("app/src/main/java/com/example/ui/screens/LiveScreen.kt", "r") as f:
    content = f.read()

# Find the start of TvLiveScreen
start_idx = content.find("@Composable\nprivate fun TvLiveScreen(")
if start_idx == -1:
    print("Not found TvLiveScreen")
    exit(1)

# Find the start of PhoneLiveScreen
end_idx = content.find("// =========================================================================\n// INTERFAZ PARA TELÉFONOS", start_idx)

if end_idx == -1:
    print("End not found")
    exit(1)

new_tv_screen = """@Composable
private fun TvLiveScreen(
    viewModel: MainViewModel,
    onPlayChannel: (channelId: String, categoryId: String, title: String) -> Unit,
    onExitToMenu: (() -> Unit)? = null
) {
    val categories by viewModel.liveCategories.collectAsState()
    val selectedCat by viewModel.selectedLiveCat.collectAsState()
    val channels by viewModel.liveChannels.collectAsState()
    val loading by viewModel.liveLoading.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()

    var activeTvTab by remember { mutableStateOf(LiveSubTab.CATEGORIA) }
    
    val favoriteChannels = remember(favorites, channels) {
        val favIds = favorites.filter { it.kind == "live" }.map { it.id }.toSet()
        channels.filter { favIds.contains(it.id) }
    }
    
    val displayChannels = if (activeTvTab == LiveSubTab.FAVORITOS) favoriteChannels else channels

    LaunchedEffect(Unit) {
        viewModel.loadLiveCategories()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(JetBackground)
    ) {
        // MENÚ LATERAL (CATEGORÍAS)
        Column(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .background(JetSidebarBg)
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "TV EN VIVO",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { activeTvTab = LiveSubTab.CATEGORIA },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTvTab == LiveSubTab.CATEGORIA) JetOrange else Color(0xFF202020),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text("Cat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { activeTvTab = LiveSubTab.FAVORITOS },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTvTab == LiveSubTab.FAVORITOS) JetOrange else Color(0xFF202020),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text("Fav", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (activeTvTab == LiveSubTab.CATEGORIA) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        val isSelected = selectedCat == "ALL"
                        var isFocused by remember { mutableStateOf(false) }
                        Surface(
                            color = if (isSelected) JetOrange else if (isFocused) Color(0xFF333344) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickable { viewModel.selectLiveCategory("ALL") }
                        ) {
                            Text(
                                text = "TODOS LOS CANALES",
                                color = if (isSelected || isFocused) Color.White else JetTextMuted,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                    itemsIndexed(categories, key = { index, cat -> "${cat.categoryId}_$index" }) { _, cat ->
                        val isSelected = selectedCat == cat.categoryId
                        var isFocused by remember { mutableStateOf(false) }
                        Surface(
                            color = if (isSelected) JetOrange else if (isFocused) Color(0xFF333344) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickable { viewModel.selectLiveCategory(cat.categoryId) }
                        ) {
                            Text(
                                text = cat.categoryName.uppercase(),
                                color = if (isSelected || isFocused) Color.White else JetTextMuted,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Mis Favoritos", color = JetTextMuted, fontSize = 14.sp)
                }
            }
        }

        // PANEL DERECHO (CANALES)
        Box(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
                .background(JetBackground)
        ) {
            if (loading && channels.isEmpty()) {
                CircularProgressIndicator(color = JetOrange, modifier = Modifier.size(48.dp).align(Alignment.Center))
            } else if (displayChannels.isEmpty()) {
                Text(
                    text = "No hay canales disponibles",
                    color = JetTextMuted,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(displayChannels, key = { index, ch -> "${ch.id}_$index" }) { index, channel ->
                        val isFav = favorites.any { it.kind == "live" && it.id == channel.id }
                        ChannelListItemJetGo(
                            channel = channel,
                            index = index + 1,
                            isSelected = false,
                            isFav = isFav,
                            isTv = true,
                            onClick = { onPlayChannel(channel.id, selectedCat, channel.name) },
                            onDoubleClick = { onPlayChannel(channel.id, selectedCat, channel.name) },
                            onFavToggle = { viewModel.toggleFavorite(channel.id, channel.name, channel.streamIcon, "live") }
                        )
                    }
                }
            }
        }
    }
}

"""

content = content[:start_idx] + new_tv_screen + content[end_idx:]

with open("app/src/main/java/com/example/ui/screens/LiveScreen.kt", "w") as f:
    f.write(content)
