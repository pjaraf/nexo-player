package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.Profile
import com.example.data.storage.AppStorage
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageProfilesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigatePin: (action: String) -> Unit
) {
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var pinSet by remember { mutableStateOf(false) }

    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    fun reload() {
        profiles = AppStorage.getProfiles()
        pinSet = AppStorage.hasPin()
    }

    LaunchedEffect(Unit) {
        reload()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Administrar perfiles",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("manage_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NexusBackground,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = NexusBackground,
        modifier = Modifier.testTag("manage_profiles_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Profiles list
            items(profiles, key = { it.id }) { p ->
                val avatarColor = try {
                    Color(android.graphics.Color.parseColor(p.color))
                } catch (e: Exception) {
                    NexusPrimary
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NexusSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                    modifier = Modifier.fillMaxWidth().testTag("manage_item_${p.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(avatarColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (p.isKids) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = p.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (p.isKids) "Kids" else "Estándar",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (p.isKids) NexusAccent else NexusTextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // Edit
                        IconButton(
                            onClick = {
                                editingProfile = p
                                showDialog = true
                            },
                            modifier = Modifier.testTag("manage_edit_${p.id}")
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Editar", tint = Color.White)
                        }

                        // Delete (only if > 1 profile)
                        if (profiles.size > 1) {
                            IconButton(
                                onClick = {
                                    viewModel.deleteProfile(p.id)
                                    reload()
                                },
                                modifier = Modifier.testTag("manage_delete_${p.id}")
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = NexusPrimary)
                            }
                        }
                    }
                }
            }

            // Add Profile Button
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, NexusBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingProfile = Profile(
                                id = "",
                                name = "",
                                color = AppStorage.AVATAR_COLORS.random(),
                                isKids = false
                            )
                            showDialog = true
                        }
                        .testTag("manage_add_profile_btn")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Añadir perfil",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            // Adult PIN Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CONTROL PARENTAL / PIN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        color = NexusTextSecondary
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pinSet) "PIN configurado" else "Sin PIN de bloqueo",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Los perfiles estándar requerirán este PIN de 4 dígitos para acceder.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = NexusTextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Button(
                            onClick = {
                                onNavigatePin(if (pinSet) "remove" else "set")
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pinSet) NexusSurfaceVariant else NexusPrimary
                            ),
                            modifier = Modifier.testTag("manage_pin_action_btn")
                        ) {
                            Text(
                                text = if (pinSet) "Eliminar" else "Crear PIN",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Profile Editor Dialog
    if (showDialog && editingProfile != null) {
        var profileName by remember { mutableStateOf(editingProfile!!.name) }
        var selectedColor by remember { mutableStateOf(editingProfile!!.color) }
        var isKids by remember { mutableStateOf(editingProfile!!.isKids) }

        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = NexusSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, NexusBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (editingProfile!!.id.isBlank()) "Nuevo Perfil" else "Editar Perfil",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Nombre del perfil", color = NexusTextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexusPrimary,
                            unfocusedBorderColor = NexusBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NexusSurfaceVariant,
                            unfocusedContainerColor = NexusSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("profile_editor_name")
                    )

                    Text(
                        text = "Color del avatar",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NexusTextSecondary,
                            letterSpacing = 1.sp
                        )
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(AppStorage.AVATAR_COLORS) { hex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                NexusPrimary
                            }
                            val isSelected = hex == selectedColor

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (isSelected) 3.dp else 0.dp,
                                        if (isSelected) Color.White else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }

                    // Kids Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NexusSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isKids = !isKids }
                            .testTag("profile_editor_kids_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = isKids,
                                onCheckedChange = { isKids = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NexusPrimary,
                                    uncheckedColor = NexusTextSecondary
                                )
                            )
                            Column {
                                Text(
                                    text = "Perfil para Niños (Kids)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "Filtra automáticamente canales y películas aptas",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 11.sp,
                                        color = NexusTextSecondary
                                    )
                                )
                            }
                        }
                    }

                    // Save Button
                    Button(
                        onClick = {
                            if (profileName.isNotBlank()) {
                                if (editingProfile!!.id.isBlank()) {
                                    viewModel.addProfile(profileName.trim(), selectedColor, isKids)
                                } else {
                                    viewModel.updateProfile(editingProfile!!.id, profileName.trim(), selectedColor, isKids)
                                }
                                reload()
                                showDialog = false
                            }
                        },
                        enabled = profileName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_editor_save_btn")
                    ) {
                        Text(
                            text = "GUARDAR",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
