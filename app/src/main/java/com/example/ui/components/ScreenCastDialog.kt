package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cast.CastDevice
import com.example.cast.CastPlaybackState
import com.example.cast.DlnaCastManager
import com.example.ui.theme.NexusBorder
import com.example.ui.theme.NexusPrimary
import com.example.ui.theme.NexusSurfaceVariant
import com.example.utils.ScreenCastHelper

@Composable
fun ScreenCastDialog(
    streamUrl: String? = null,
    title: String = "Nexo Player",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val discoveredDevices by DlnaCastManager.discoveredDevices.collectAsState()
    val isSearching by DlnaCastManager.isSearching.collectAsState()
    val playbackState by DlnaCastManager.playbackState.collectAsState()

    var showMirroringOption by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DlnaCastManager.startDiscovery(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF141520),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2E44)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NexusPrimary.copy(alpha = 0.15f))
                            .border(1.dp, NexusPrimary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = NexusPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Transmitir Video a TV",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Solo envía el video en alta calidad (sin duplicar tu pantalla)",
                            color = Color(0xFFA0A3BD),
                            fontSize = 11.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Active Casting Status / Remote Controls
                when (val state = playbackState) {
                    is CastPlaybackState.Connecting -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E2033),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = NexusPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Column {
                                    Text(
                                        text = "Conectando con ${state.device.name}...",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Enviando video en streaming...",
                                        color = Color(0xFFA0A3BD),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    is CastPlaybackState.Playing, is CastPlaybackState.Paused -> {
                        val isPlaying = state is CastPlaybackState.Playing
                        val devName = if (state is CastPlaybackState.Playing) state.device.name else (state as CastPlaybackState.Paused).device.name

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1B2A1E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CastConnected,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Transmitiendo en $devName",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isPlaying) {
                                        Button(
                                            onClick = { DlnaCastManager.pause() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E334D)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pausar", tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pausar", fontSize = 12.sp, color = Color.White)
                                        }
                                    } else {
                                        Button(
                                            onClick = { DlnaCastManager.resume() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Reanudar", tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reanudar", fontSize = 12.sp, color = Color.White)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = { DlnaCastManager.stop() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "Detener", tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Detener", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }

                // Discovered TVs List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TELEVISORES DETECTADOS EN TU WI-FI",
                        color = Color(0xFF8E92B2),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = NexusPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Actualizar",
                            color = NexusPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { DlnaCastManager.startDiscovery(context) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (discoveredDevices.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B1C2A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2C40)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TvOff,
                                contentDescription = null,
                                tint = Color(0xFF6B6E8A),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = if (isSearching) "Buscando Smart TVs en tu red..." else "No se detectaron Smart TVs compatibles",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Asegúrate de que tu televisor esté encendido y en el mismo Wi-Fi.",
                                color = Color(0xFF8E92B2),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(discoveredDevices, key = { it.id }) { dev ->
                            DeviceItemCard(
                                device = dev,
                                onClick = {
                                    if (!streamUrl.isNullOrBlank()) {
                                        DlnaCastManager.castToDevice(
                                            device = dev,
                                            videoUrl = streamUrl,
                                            title = title,
                                            context = context
                                        ) { success, errorMsg ->
                                            if (!success) {
                                                Toast.makeText(context, errorMsg ?: "Error al transmitir", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Inicia la reproducción de un canal o película para transmitir.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Alternative Options: External Caster App (Web Video Caster / BubbleUPnP)
                if (!streamUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            ScreenCastHelper.openInExternalPlayer(context, streamUrl, title)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B3E5C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transmitir con Web Video Cast / VLC",
                            color = Color.White,
                            fontSize = 12.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Show screen mirroring toggle
                TextButton(
                    onClick = { showMirroringOption = !showMirroringOption },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showMirroringOption) "Ocultar duplicación de pantalla" else "¿Prefieres duplicar la pantalla completa del móvil?",
                        color = Color(0xFFA0A3BD),
                        fontSize = 11.5.sp
                    )
                }

                AnimatedVisibility(visible = showMirroringOption) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E2032))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Duplicará toda la pantalla de tu móvil en la TV usando Smart View o Miracast.",
                            color = Color(0xFFA0A3BD),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                ScreenCastHelper.openCastSettings(context)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF393D5C)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir Smart View / Duplicar Pantalla", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cerrar",
                        color = Color(0xFF8E92B2),
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceItemCard(
    device: CastDevice,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E2032),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2F48)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF282B42)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.isRoku) Icons.Default.Tv else Icons.Default.ConnectedTv,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (device.isRoku) "Roku Streaming Device" else "${device.manufacturer} • ${device.ipAddress}",
                    color = Color(0xFFA0A3BD),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Enviar video",
                tint = NexusPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
