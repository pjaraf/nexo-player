package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Profile
import com.example.data.storage.AppStorage
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@Composable
fun ProfileSelectScreen(
    viewModel: MainViewModel,
    onProfileSelected: (isKids: Boolean, needsPin: Boolean) -> Unit,
    onManageProfiles: () -> Unit
) {
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }

    LaunchedEffect(Unit) {
        profiles = AppStorage.getProfiles()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E0E10),
                        Color(0xFF0D0D0D),
                        Color(0xFF000000)
                    ),
                    radius = 900f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("profile_select_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "NEX",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = "US",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = NexusPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¿Quién está viendo?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Profiles Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val avatarColor = try {
                        Color(android.graphics.Color.parseColor(profile.color))
                    } catch (e: Exception) {
                        NexusPrimary
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clickable {
                                viewModel.selectProfile(profile)
                                val hasPin = AppStorage.hasPin()
                                onProfileSelected(profile.isKids, !profile.isKids && hasPin)
                            }
                            .testTag("profile_item_${profile.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(avatarColor)
                                .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profile.isKids) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "Kids",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 8.dp)
                                ) {
                                    Text(
                                        text = "KIDS",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = profile.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Add Profile Item
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clickable { onManageProfiles() }
                            .testTag("profile_add_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(NexusSurfaceVariant)
                                .border(
                                    1.5.dp,
                                    NexusBorder,
                                    RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Añadir",
                                tint = NexusTextSecondary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Text(
                            text = "Añadir",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = NexusTextSecondary
                            )
                        )
                    }
                }
            }

            // Bottom manage button
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = NexusSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                modifier = Modifier
                    .clickable { onManageProfiles() }
                    .testTag("manage_profiles_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = NexusTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Administrar perfiles",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            color = NexusTextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
