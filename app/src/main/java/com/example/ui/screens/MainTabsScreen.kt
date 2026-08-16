package com.example.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Inicio", Icons.Default.Home, Icons.Outlined.Home, "tab_home"),
    LIVE("TV", Icons.Default.LiveTv, Icons.Outlined.LiveTv, "tab_live"),
    SERIES("Series", Icons.Default.Tv, Icons.Outlined.Tv, "tab_series"),
    MOVIES("Películas", Icons.Default.Movie, Icons.Outlined.Movie, "tab_movies"),
    PROFILE("Perfil", Icons.Default.Person, Icons.Outlined.Person, "tab_profile")
}

@Composable
fun MainTabsScreen(
    viewModel: MainViewModel,
    onNavigateLivePlayer: (channelId: String, categoryId: String, title: String) -> Unit,
    onNavigateMovieDetail: (movieId: String) -> Unit,
    onNavigateSeriesDetail: (seriesId: String) -> Unit,
    onNavigatePlayerDirect: (url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit,
    onNavigateFavorites: () -> Unit,
    onNavigateSwitchProfile: () -> Unit,
    onNavigateManageProfiles: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var isSidebarOpen by remember { mutableStateOf(false) }

    // Intercept BACK button when sidebar is open on TV
    BackHandler(enabled = isTv && isSidebarOpen) {
        isSidebarOpen = false
    }

    // Content renderer shared by both layouts
    @Composable
    fun RenderTabContent() {
        when (currentTab) {
            MainTab.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateLive = { chId ->
                        val ch = viewModel.homeLiveRow.value.find { it.id == chId }
                            ?: viewModel.liveChannels.value.find { it.id == chId }
                        val name = ch?.name ?: "Canal de TV"
                        onNavigateLivePlayer(chId, "ALL", name)
                    },
                    onNavigateMovie = onNavigateMovieDetail,
                    onNavigateSeries = onNavigateSeriesDetail,
                    onPlayDirect = onNavigatePlayerDirect,
                    onNavigateProfile = { currentTab = MainTab.PROFILE }
                )
            }
            MainTab.LIVE -> {
                LiveScreen(
                    viewModel = viewModel,
                    onPlayChannel = onNavigateLivePlayer,
                    onExitToMenu = { currentTab = MainTab.HOME }
                )
            }
            MainTab.MOVIES -> {
                MoviesScreen(
                    viewModel = viewModel,
                    onNavigateMovie = onNavigateMovieDetail
                )
            }
            MainTab.SERIES -> {
                SeriesScreen(
                    viewModel = viewModel,
                    onNavigateSeries = onNavigateSeriesDetail
                )
            }
            MainTab.PROFILE -> {
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateFavorites = onNavigateFavorites,
                    onNavigateSwitchProfile = onNavigateSwitchProfile,
                    onNavigateManageProfiles = onNavigateManageProfiles,
                    onLogout = onLogout
                )
            }
        }
    }

    if (isTv) {
        if (currentTab == MainTab.LIVE) {
            // --- FULLSCREEN TV MODE ON TELEVISION ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexusBackground)
            ) {
                RenderTabContent()
            }
        } else {
            // --- TELEVISION LAYOUT WITH HIDDEN FLOATING SIDEBAR (DRAWER) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexusBackground)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            val code = keyEvent.nativeKeyEvent.keyCode
                            when (code) {
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (!isSidebarOpen) {
                                        isSidebarOpen = true
                                        true
                                    } else {
                                        false
                                    }
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (isSidebarOpen) {
                                        isSidebarOpen = false
                                        true
                                    } else {
                                        false
                                    }
                                }
                                AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                    if (isSidebarOpen) {
                                        isSidebarOpen = false
                                        true
                                    } else {
                                        false
                                    }
                                }
                                AndroidKeyEvent.KEYCODE_MENU -> {
                                    isSidebarOpen = !isSidebarOpen
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .testTag("main_tv_layout")
            ) {
                // Main Content Area taking FULL 100% width of the TV screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    RenderTabContent()
                }

                // Subtle Menu Button Trigger in top-left
                Surface(
                    onClick = { isSidebarOpen = !isSidebarOpen },
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                        .testTag("tv_menu_trigger_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Menú (◀)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Scrim overlay when sidebar is open
                if (isSidebarOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { isSidebarOpen = false }
                    )
                }

                // Floating lateral navigation drawer
                AnimatedVisibility(
                    visible = isSidebarOpen,
                    enter = fadeIn() + slideInHorizontally { -it },
                    exit = fadeOut() + slideOutHorizontally { -it },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(start = 24.dp, top = 20.dp, bottom = 20.dp)
                            .width(260.dp)
                            .fillMaxHeight()
                            .shadow(24.dp, RoundedCornerShape(24.dp))
                            .testTag("floating_tv_sidebar"),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF101018).copy(alpha = 0.96f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Nexo Logo & TV Header
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(NexusPrimary, NexusPrimaryVariant)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "N",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    Text(
                                        text = "NEXO",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 2.sp
                                    )
                                }

                                Divider(
                                    color = Color.White.copy(alpha = 0.1f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Lateral Nav Items
                                MainTab.values().forEach { tab ->
                                    val isSelected = currentTab == tab
                                    TvSidebarNavItem(
                                        tab = tab,
                                        isSelected = isSelected,
                                        onClick = {
                                            currentTab = tab
                                            isSidebarOpen = false
                                        }
                                    )
                                }
                            }

                            // Bottom info in floating bar
                            Surface(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Text(
                                        text = "Control Remoto Activo",
                                        color = Color(0xFFA0A0AB),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- MOBILE / PHONE LAYOUT: Untouched standard Bottom Navigation ---
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = NexusSurface,
                    contentColor = Color.White,
                    windowInsets = WindowInsets.navigationBars,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    MainTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = NexusTextMuted,
                                unselectedTextColor = NexusTextMuted,
                                indicatorColor = NexusPrimary
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            },
            containerColor = NexusBackground,
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                RenderTabContent()
            }
        }
    }
}

@Composable
private fun TvSidebarNavItem(
    tab: MainTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> NexusPrimary
            isFocused -> Color.White.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        label = "tab_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isFocused -> Color.White
            else -> NexusTextSecondary
        },
        label = "tab_content_color"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = if (isFocused && !isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f))
        } else if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .testTag(tab.testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.title,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = tab.title,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
