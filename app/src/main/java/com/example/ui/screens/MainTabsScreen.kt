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
import com.example.data.storage.AppStorage
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
    onLogout: () -> Unit,
    onNavigatePin: (String) -> Unit = {},
    onSwitchProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var isSidebarOpen by remember { mutableStateOf(false) }
    var isAtFirstCaratula by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (AppStorage.isAutoCheckUpdatesEnabled()) {
            viewModel.checkForUpdates(manual = false)
        }
    }

    val sidebarFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSidebarOpen) {
        if (isSidebarOpen) {
            try {
                sidebarFocusRequester.requestFocus()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

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
                    onNavigateProfile = { currentTab = MainTab.PROFILE },
                    isSidebarOpen = isSidebarOpen,
                    onFirstItemFocused = { isAtFirstCaratula = it }
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
                    onNavigateMovie = onNavigateMovieDetail,
                    onPlayDirect = onNavigatePlayerDirect
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
                    onLogout = onLogout,
                    onNavigatePin = onNavigatePin,
                    onSwitchProfile = onSwitchProfile
                )
            }
        }
    }

    if (isTv) {
        BackHandler(enabled = currentTab != MainTab.HOME) {
            currentTab = MainTab.HOME
        }

        if (currentTab == MainTab.HOME) {
            val homeTabFocusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                if (isTv) {
                    kotlinx.coroutines.delay(250)
                    try {
                        homeTabFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }

            // --- TELEVISION HOME SCREEN WITH FIXED ICON-ONLY NAVIGATION RAIL ON THE LEFT ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexusBackground)
                    .testTag("main_tv_layout")
            ) {
                // Fixed Side Navigation Rail (Icon-only)
                Surface(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight(),
                    color = Color(0xFF0D0D12),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    ) {
                        MainTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            var isFocused by remember { mutableStateOf(false) }

                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isFocused && isSelected -> NexusPrimary
                                    isFocused -> Color.White.copy(alpha = 0.25f)
                                    isSelected -> NexusPrimary
                                    else -> Color.Transparent
                                },
                                border = if (isFocused) {
                                    androidx.compose.foundation.BorderStroke(2.5.dp, Color.White)
                                } else if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                } else null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .then(if (tab == MainTab.HOME) Modifier.focusRequester(homeTabFocusRequester) else Modifier)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .clickable { currentTab = tab }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                AndroidKeyEvent.KEYCODE_ENTER,
                                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                    currentTab = tab
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .testTag("tv_nav_${tab.testTag}")
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Main Content Area taking the rest of the screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    RenderTabContent()
                }
            }
        } else {
            // --- TELEVISION NON-HOME SCREENS: FULL SCREEN WITHOUT SIDEBAR ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexusBackground)
            ) {
                RenderTabContent()
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
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> TvSelectedRed
            isFocused -> TvFocusBlue
            else -> Color.Transparent
        },
        label = "tab_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected || isFocused -> Color.White
            else -> NexusTextSecondary
        },
        label = "tab_content_color"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = if (isFocused && !isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, TvFocusBlue)
        } else if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .testTag(tab.testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.title,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = tab.title,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
