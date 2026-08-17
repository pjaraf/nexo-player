package com.example.ui.screens

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.storage.AppStorage
import com.example.ui.components.PhoneLinkTvDialog
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onNavigateFavorites: () -> Unit,
    onNavigateSwitchProfile: () -> Unit,
    onNavigateManageProfiles: () -> Unit,
    onLogout: () -> Unit
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val userInfo by viewModel.userInfo.collectAsState()

    var showPhoneLinkDialog by remember { mutableStateOf(false) }

    val avatarColor = try {
        Color(android.graphics.Color.parseColor(activeProfile?.color ?: "#E50914"))
    } catch (e: Exception) {
        NexusPrimary
    }

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

                // Link to TV / Transfer Session
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
                    text = "VERSIÓN",
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                text = "Versión de la aplicación",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            color = NexusTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
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
        Text(label, color = NexusTextSecondary, fontSize = 13.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
