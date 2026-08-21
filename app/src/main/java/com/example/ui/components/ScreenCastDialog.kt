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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cast.CastDevice
import com.example.cast.CastPlaybackState
import com.example.cast.DlnaCastManager
import com.example.ui.theme.NexusPrimary
import com.example.utils.ScreenCastHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenCastDialog(
    streamUrl: String? = null,
    title: String = "Nexo Player",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val discoveredDevices by DlnaCastManager.discoveredDevices.collectAsState()
    val isSearching by DlnaCastManager.isSearching.collectAsState()
    val playbackState by DlnaCastManager.playbackState.collectAsState()

    var showManualIp by remember { mutableStateOf(false) }
    var manualIpText by remember { mutableStateOf("") }
    var isConnectingManualIp by remember { mutableStateOf(false) }
    var showMirroringOption by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DlnaCastManager.startDiscovery(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF13141F),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2E44)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
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
                            text = "Transmitir Video a Smart TV",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Transmisión directa de video a Roku, Android TV, Samsung, LG y Chromecast",
                            color = Color(0xFFA0A3BD),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Active Casting Status / Remote Controls
                when (val state = playbackState) {
                    is CastPlaybackState.Connecting -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E2033),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
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
                                        text = "Enviando señal de transmisión...",
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
                            color = Color(0xFF18291A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
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
                                        text = "Transmitiendo en: $devName",
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
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pausar", tint = Color.White, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pausar", fontSize = 11.5.sp, color = Color.White)
                                        }
                                    } else {
                                        Button(
                                            onClick = { DlnaCastManager.resume() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Reanudar", tint = Color.White, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reanudar", fontSize = 11.5.sp, color = Color.White)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = { DlnaCastManager.stop() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "Detener", tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Detener", fontSize = 11.5.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }

                // Discovered TVs Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DISPOSITIVOS EN TU WI-FI",
                        color = Color(0xFF8E92B2),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (showManualIp) "Ocultar IP" else "+ Conectar por IP",
                            color = NexusPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showManualIp = !showManualIp }
                        )

                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = NexusPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Buscar",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { DlnaCastManager.startDiscovery(context) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Manual IP input box
                AnimatedVisibility(visible = showManualIp) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1B1D2E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF323652)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Ingresa la dirección IP de tu TV (Ej: 192.168.1.50):",
                                color = Color(0xFFA0A3BD),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualIpText,
                                    onValueChange = { manualIpText = it },
                                    placeholder = { Text("192.168.1.X", fontSize = 12.sp, color = Color(0xFF6B6E8A)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (manualIpText.isNotBlank()) {
                                            coroutineScope.launch {
                                                isConnectingManualIp = true
                                                val dev = DlnaCastManager.addDirectIpDevice(manualIpText)
                                                isConnectingManualIp = false
                                                if (dev != null && !streamUrl.isNullOrBlank()) {
                                                    DlnaCastManager.castToDevice(dev, streamUrl, title, context) { ok, err ->
                                                        if (!ok) Toast.makeText(context, err ?: "Error", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = NexusPrimary,
                                        unfocusedBorderColor = Color(0xFF3A3E5C)
                                    )
                                )

                                Button(
                                    onClick = {
                                        if (manualIpText.isNotBlank()) {
                                            coroutineScope.launch {
                                                isConnectingManualIp = true
                                                val dev = DlnaCastManager.addDirectIpDevice(manualIpText)
                                                isConnectingManualIp = false
                                                if (dev != null) {
                                                    if (!streamUrl.isNullOrBlank()) {
                                                        DlnaCastManager.castToDevice(dev, streamUrl, title, context) { ok, err ->
                                                            if (!ok) Toast.makeText(context, err ?: "Error", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Dispositivo añadido. Selecciona un video para transmitir.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "IP no válida o sin respuesta", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    if (isConnectingManualIp) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text("Conectar", fontSize = 11.5.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Discovered Devices list
                if (discoveredDevices.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF181A26),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26283B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TvOff,
                                contentDescription = null,
                                tint = Color(0xFF6B6E8A),
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = if (isSearching) "Buscando Smart TVs en tu Wi-Fi..." else "No se detectaron TVs automáticamente",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Usa las opciones directas abajo o ingresa la IP del televisor.",
                                color = Color(0xFF8E92B2),
                                fontSize = 10.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
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

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Quick Casting Options: External App & System Cast
                if (!streamUrl.isNullOrBlank()) {
                    Button(
                        onClick = {
                            ScreenCastHelper.openInExternalPlayer(context, streamUrl, title)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262A45)),
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
                            text = "Transmitir con Web Video Caster / VLC",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                ScreenCastHelper.openCastSettings(context)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333652)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, tint = Color(0xFFA0A3BD), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Smart View", fontSize = 11.sp, color = Color(0xFFA0A3BD))
                        }

                        OutlinedButton(
                            onClick = {
                                ScreenCastHelper.copyStreamUrl(context, streamUrl)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333652)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFA0A3BD), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copiar Enlace", fontSize = 11.sp, color = Color(0xFFA0A3BD))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cerrar",
                        color = Color(0xFF8E92B2),
                        fontSize = 12.sp
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
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1B1D2C),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2F47)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF262942)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.isRoku) Icons.Default.Tv else Icons.Default.ConnectedTv,
                    contentDescription = null,
                    tint = NexusPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (device.isRoku) "Roku ECP" else "DLNA / UPnP"} • ${device.ipAddress}",
                    color = Color(0xFF8E92B2),
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Transmitir",
                tint = NexusPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
