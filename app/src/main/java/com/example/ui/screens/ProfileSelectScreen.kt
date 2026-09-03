package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Profile
import com.example.data.storage.AppStorage
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@Composable
fun ProfileSelectScreen(
    viewModel: MainViewModel,
    onProfileSelected: (Profile) -> Unit,
    onManageProfiles: () -> Unit,
    onLogout: () -> Unit
) {
    var profiles by remember { mutableStateOf(AppStorage.getProfiles()) }

    LaunchedEffect(Unit) {
        profiles = AppStorage.getProfiles()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "NEXO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 4.sp
                )
            )
            Text(
                text = "¿Quién está viendo?",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = NexusTextSecondary,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(profiles) { profile ->
                    ProfileAvatarItem(
                        profile = profile,
                        onClick = {
                            viewModel.selectProfile(profile)
                            onProfileSelected(profile)
                        }
                    )
                }

                item {
                    AddProfileItem(onClick = onManageProfiles)
                }
            }

            TextButton(onClick = onLogout) {
                Text("Cerrar sesión", color = NexusTextSecondary)
            }
        }
    }
}

@Composable
private fun ProfileAvatarItem(
    profile: Profile,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val avatarColor = try {
        Color(android.graphics.Color.parseColor(profile.color))
    } catch (_: Exception) {
        NexusPrimary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(avatarColor)
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) TvFocusBlue else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (profile.isKids) {
                Icon(
                    imageVector = Icons.Default.ChildCare,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Text(
                    text = profile.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        if (profile.isKids) {
            Surface(
                color = NexusAccent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "KIDS",
                    color = NexusAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun AddProfileItem(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(NexusSurfaceVariant)
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) TvFocusBlue else NexusBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Añadir perfil",
                tint = NexusTextSecondary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Añadir",
            color = NexusTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
