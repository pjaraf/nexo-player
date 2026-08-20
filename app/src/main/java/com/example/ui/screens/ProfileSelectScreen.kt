package com.example.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Profile
import com.example.data.storage.AppStorage
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils
import kotlinx.coroutines.delay

@Composable
fun ProfileSelectScreen(
    viewModel: MainViewModel,
    onProfileSelected: (isKids: Boolean, needsPin: Boolean) -> Unit,
    onManageProfiles: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    val firstProfileFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        profiles = AppStorage.getProfiles()
    }

    LaunchedEffect(profiles) {
        if (profiles.isNotEmpty()) {
            delay(150)
            try {
                firstProfileFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
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
                    text = "O",
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
                itemsIndexed(profiles, key = { _, it -> it.id }) { index, profile ->
                    val avatarColor = try {
                        Color(android.graphics.Color.parseColor(profile.color))
                    } catch (e: Exception) {
                        NexusPrimary
                    }
                    var isProfileFocused by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(
                        targetValue = if (isProfileFocused) 1.10f else 1.0f,
                        animationSpec = tween(150),
                        label = "profile_scale"
                    )

                    fun selectThisProfile() {
                        viewModel.selectProfile(profile)
                        val hasPin = AppStorage.hasPin()
                        onProfileSelected(profile.isKids, !profile.isKids && hasPin)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .scale(scale)
                            .then(if (index == 0) Modifier.focusRequester(firstProfileFocusRequester) else Modifier)
                            .onFocusChanged { isProfileFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                        AndroidKeyEvent.KEYCODE_ENTER,
                                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                            selectThisProfile()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable { selectThisProfile() }
                            .testTag("profile_item_${profile.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(avatarColor)
                                .border(
                                    width = if (isProfileFocused) 3.5.dp else 2.dp,
                                    color = if (isProfileFocused) Color(0xFFFFC107) else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp)
                                ),
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
                                fontWeight = if (isProfileFocused) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = if (isProfileFocused) 15.sp else 14.sp,
                                color = if (isProfileFocused) Color(0xFFFFC107) else Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Add Profile Item
                item {
                    var isAddFocused by remember { mutableStateOf(false) }
                    val addScale by animateFloatAsState(
                        targetValue = if (isAddFocused) 1.10f else 1.0f,
                        animationSpec = tween(150),
                        label = "add_scale"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .scale(addScale)
                            .onFocusChanged { isAddFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                        AndroidKeyEvent.KEYCODE_ENTER,
                                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                            onManageProfiles()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable { onManageProfiles() }
                            .testTag("profile_add_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(NexusSurfaceVariant)
                                .border(
                                    width = if (isAddFocused) 3.5.dp else 1.5.dp,
                                    color = if (isAddFocused) Color(0xFFFFC107) else NexusBorder,
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Añadir",
                                tint = if (isAddFocused) Color(0xFFFFC107) else NexusTextSecondary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Text(
                            text = "Añadir",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isAddFocused) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = if (isAddFocused) 15.sp else 14.sp,
                                color = if (isAddFocused) Color(0xFFFFC107) else NexusTextSecondary
                            )
                        )
                    }
                }
            }

            // Bottom manage button
            var isManageFocused by remember { mutableStateOf(false) }
            val manageScale by animateFloatAsState(
                targetValue = if (isManageFocused) 1.08f else 1.0f,
                animationSpec = tween(150),
                label = "manage_scale"
            )

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isManageFocused) TvFocusBlue else NexusSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    if (isManageFocused) 2.5.dp else 1.5.dp,
                    if (isManageFocused) Color(0xFFFFC107) else NexusBorder
                ),
                modifier = Modifier
                    .scale(manageScale)
                    .onFocusChanged { isManageFocused = it.isFocused }
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                AndroidKeyEvent.KEYCODE_ENTER,
                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                    onManageProfiles()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
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
                        tint = if (isManageFocused) Color.White else NexusTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Administrar perfiles",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            color = if (isManageFocused) Color.White else NexusTextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

