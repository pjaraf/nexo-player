package com.example.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.models.Profile
import com.example.data.models.UserInfo
import com.example.data.storage.AppStorage
import com.example.ui.components.PhoneLinkTvDialog
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onNavigateFavorites: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    val activeProfile by viewModel.activeProfile.collectAsState()
    val userInfo by viewModel.userInfo.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val updateStatusMessage by viewModel.updateStatusMessage.collectAsState()

    var showPhoneLinkDialog by remember { mutableStateOf(false) }

    val avatarColor = try {
        Color(android.graphics.Color.parseColor(activeProfile?.color ?: "#E50914"))
    } catch (e: Exception) {
        NexusPrimary
    }

    if (isTv) {
        // --- DEDICATED 16:9 TV PROFILE SCREEN (Optimized 2-Column Balanced Layout) ---
        TvProfileScreenLayout(
            activeProfile = activeProfile,
            userInfo = userInfo,
            avatarColor = avatarColor,
            isCheckingUpdates = isCheckingUpdates,
            updateStatusMessage = updateStatusMessage,
            onCheckUpdates = { viewModel.checkForUpdates(manual = true) },
            onNavigateFavorites = onNavigateFavorites,
            onLogout = {
                viewModel.logout()
                onLogout()
            }
        )
    } else {
        // --- MOBILE / PHONE LAYOUT ---
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Mi Perfil",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NexusBackground,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = NexusBackground,
            modifier = Modifier.testTag("profile_screen")
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Profile Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NexusSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(avatarColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (activeProfile?.isKids == true) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                            } else {
                                Text(
                                    text = activeProfile?.name?.take(1)?.uppercase() ?: "P",
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = activeProfile?.name ?: "Usuario",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(999.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = userInfo?.status?.uppercase() ?: "ACTIVO",
                                        color = Color(0xFF10B981),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                if (activeProfile?.isKids == true) {
                                    Surface(
                                        color = NexusAccent.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(999.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "KIDS",
                                            color = NexusAccent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Actions List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "CONFIGURACIÓN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            color = NexusTextSecondary
                        )
                    )

                    // Favorites
                    MenuOptionCard(
                        title = "Mis Favoritos",
                        subtitle = "Películas y series guardadas",
                        icon = Icons.Outlined.FavoriteBorder,
                        onClick = onNavigateFavorites,
                        testTag = "profile_favorites_btn"
                    )

                    MenuOptionCard(
                        title = "Vincular Televisor (TV)",
                        subtitle = "Iniciar sesión en tu Smart TV con PIN o QR",
                        icon = Icons.Default.Tv,
                        onClick = { showPhoneLinkDialog = true },
                        testTag = "profile_link_tv_btn"
                    )
                }

                // App Version Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ACTUALIZACIONES Y SISTEMA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            color = NexusTextSecondary
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = NexusSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_nexus_logo),
                                        contentDescription = "Nexo Logo",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = "Versión instalada",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                    color = NexusPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(color = NexusBorder)

                            // Check for updates button
                            var isCheckFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { viewModel.checkForUpdates(manual = true) },
                                enabled = !isCheckingUpdates,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCheckFocused) TvFocusBlue else NexusSurfaceVariant
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isCheckFocused) 2.dp else 1.dp,
                                    if (isCheckFocused) Color(0xFFFFC107) else NexusBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .onFocusChanged { isCheckFocused = it.isFocused }
                                    .focusable()
                                    .testTag("profile_check_updates_btn")
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Comprobando versión...", fontSize = 13.sp, color = Color.White)
                                } else {
                                    Icon(
                                        Icons.Outlined.SystemUpdate,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Comprobar Actualizaciones", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (updateStatusMessage != null) {
                                Text(
                                    text = updateStatusMessage ?: "",
                                    color = if (updateStatusMessage?.contains("Nueva versión", ignoreCase = true) == true) Color(0xFF10B981) else NexusTextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Subscription Details
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "DETALLES DE LA CUENTA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            color = NexusTextSecondary
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = NexusSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DetailRow("Usuario", AppStorage.getUsername().ifBlank { "demo_user" })
                            HorizontalDivider(color = NexusBorder)
                            DetailRow("Vencimiento", userInfo?.expDate ?: "Ilimitado")
                            HorizontalDivider(color = NexusBorder)
                            DetailRow("Conexiones activas", "${userInfo?.activeCons ?: "1"} / ${userInfo?.maxConnections ?: "3"}")
                        }
                    }
                }

                // Logout Button
                Button(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexusPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_logout_btn")
                ) {
                    Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = NexusPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cerrar Sesión",
                        color = NexusPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }

            if (showPhoneLinkDialog) {
                PhoneLinkTvDialog(
                    onDismiss = { showPhoneLinkDialog = false }
                )
            }
        }
    }
}

/**
 * TV Specific 16:9 Layout for Profile Screen.
 * Places Profile & Account details on the left, Configuration & System updates on the right.
 * Eliminates vertical clipping and provides full D-Pad remote control support.
 */
@Composable
private fun TvProfileScreenLayout(
    activeProfile: Profile?,
    userInfo: UserInfo?,
    avatarColor: Color,
    isCheckingUpdates: Boolean,
    updateStatusMessage: String?,
    onCheckUpdates: () -> Unit,
    onNavigateFavorites: () -> Unit,
    onLogout: () -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        try {
            firstFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .padding(horizontal = 36.dp, vertical = 20.dp)
            .testTag("tv_profile_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = NexusPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Mi Perfil",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color.White
                    )
                )
            }

            // 16:9 Two Column Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // === LEFT COLUMN: User Info & Account & Logout ===
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile Overview Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = NexusSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(avatarColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (activeProfile?.isKids == true) {
                                    Icon(Icons.Default.Face, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                                } else {
                                    Text(
                                        text = activeProfile?.name?.take(1)?.uppercase() ?: "P",
                                        color = Color.White,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = activeProfile?.name ?: "Usuario",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(999.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = userInfo?.status?.uppercase() ?: "ACTIVO",
                                            color = Color(0xFF10B981),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (activeProfile?.isKids == true) {
                                        Surface(
                                            color = NexusAccent.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(999.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusAccent.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "KIDS",
                                                color = NexusAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Account Details Card
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "DETALLES DE LA CUENTA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                fontSize = 10.sp,
                                color = NexusTextSecondary
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = NexusSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DetailRow("Usuario", AppStorage.getUsername().ifBlank { "demo_user" })
                                HorizontalDivider(color = NexusBorder)
                                DetailRow("Vencimiento", userInfo?.expDate ?: "Ilimitado")
                                HorizontalDivider(color = NexusBorder)
                                DetailRow("Conexiones activas", "${userInfo?.activeCons ?: "1"} / ${userInfo?.maxConnections ?: "3"}")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // TV Logout Button with D-Pad focus & keys
                    var isLogoutFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLogoutFocused) TvFocusBlue else NexusSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            if (isLogoutFocused) 2.5.dp else 1.dp,
                            if (isLogoutFocused) Color(0xFFFFC107) else NexusPrimary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .onFocusChanged { isLogoutFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                        AndroidKeyEvent.KEYCODE_ENTER,
                                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                            onLogout()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable { onLogout() }
                            .testTag("tv_profile_logout_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Outlined.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (isLogoutFocused) Color.White else NexusPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cerrar Sesión",
                                color = if (isLogoutFocused) Color.White else NexusPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // === RIGHT COLUMN: Configuration & App Updates ===
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // CONFIGURACIÓN Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CONFIGURACIÓN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                fontSize = 10.sp,
                                color = NexusTextSecondary
                            )
                        )

                        // Mis Favoritos (First focus receiver)
                        TvMenuOptionCard(
                            title = "Mis Favoritos",
                            subtitle = "Películas y series guardadas",
                            icon = Icons.Outlined.FavoriteBorder,
                            onClick = onNavigateFavorites,
                            modifier = Modifier.focusRequester(firstFocusRequester),
                            testTag = "tv_profile_favorites_btn"
                        )
                    }

                    // ACTUALIZACIONES Y SISTEMA Section
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ACTUALIZACIONES Y SISTEMA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                fontSize = 10.sp,
                                color = NexusTextSecondary
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = NexusSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_nexus_logo),
                                            contentDescription = "Nexo Logo",
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                        )
                                        Text(
                                            text = "Versión instalada",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                        color = NexusPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                HorizontalDivider(color = NexusBorder)

                                // Check for updates button (TV focusable & D-pad key events)
                                var isCheckFocused by remember { mutableStateOf(false) }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCheckFocused) TvFocusBlue else NexusSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isCheckFocused) 2.5.dp else 1.dp,
                                        if (isCheckFocused) Color(0xFFFFC107) else NexusBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .onFocusChanged { isCheckFocused = it.isFocused }
                                        .focusable()
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.nativeKeyEvent.keyCode) {
                                                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                    AndroidKeyEvent.KEYCODE_ENTER,
                                                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                        if (!isCheckingUpdates) onCheckUpdates()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .clickable {
                                            if (!isCheckingUpdates) onCheckUpdates()
                                        }
                                        .testTag("tv_profile_check_updates_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isCheckingUpdates) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Comprobando versión...", fontSize = 12.sp, color = Color.White)
                                        } else {
                                            Icon(
                                                Icons.Outlined.SystemUpdate,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Comprobar Actualizaciones",
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                if (updateStatusMessage != null) {
                                    Text(
                                        text = updateStatusMessage,
                                        color = if (updateStatusMessage.contains("Nueva versión", ignoreCase = true)) Color(0xFF10B981) else NexusTextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 1.dp)
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

/**
 * TV Menu Option Card with high contrast focus indicator & D-Pad support.
 */
@Composable
private fun TvMenuOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isFocused) TvFocusBlue else NexusSurface,
        border = androidx.compose.foundation.BorderStroke(
            if (isFocused) 2.5.dp else 1.dp,
            if (isFocused) Color(0xFFFFC107) else NexusBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
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
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFocused) Color.White.copy(alpha = 0.2f) else NexusSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isFocused) Color.White else NexusPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isFocused) Color.White.copy(alpha = 0.9f) else NexusTextSecondary,
                        fontSize = 10.sp
                    )
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) Color.White else NexusTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MenuOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isFocused) TvFocusBlue.copy(alpha = 0.25f) else NexusSurface,
        border = androidx.compose.foundation.BorderStroke(
            if (isFocused) 2.dp else 1.dp,
            if (isFocused) TvFocusBlue else NexusBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isFocused) TvFocusBlue else NexusSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isFocused) Color.White.copy(alpha = 0.8f) else NexusTextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) TvFocusBlue else NexusTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NexusTextSecondary, fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}
