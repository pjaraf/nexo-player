package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.link.TvLinkManager
import com.example.data.storage.AppStorage
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fullscreen / Large Dialog shown on the TV when requesting Quick Login via Phone / QR Code.
 */
@Composable
fun TvQrLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (serverUrl: String, user: String, pass: String) -> Unit
) {
    var pinCode by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var webUrl by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Pulse animation for waiting state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    DisposableEffect(Unit) {
        val (pin, url) = TvLinkManager.startTvPairingServer { serverUrl, user, pass ->
            isSuccess = true
            coroutineScope.launch {
                delay(1200)
                onLoginSuccess(serverUrl, user, pass)
            }
        }
        pinCode = pin
        webUrl = url

        coroutineScope.launch(Dispatchers.IO) {
            val bitmap = TvLinkManager.generateQrCodeBitmap(url, size = 480)
            withContext(Dispatchers.Main) {
                qrBitmap = bitmap
            }
        }

        onDispose {
            TvLinkManager.stopTvPairingServer()
        }
    }

    Dialog(
        onDismissRequest = { if (!isSuccess) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = NexusSurface,
                border = BorderStroke(1.5.dp, NexusPrimary.copy(alpha = 0.5f)),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar
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
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = "INICIAR SESIÓN DESDE EL TELÉFONO",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(NexusSurfaceVariant, CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    if (isSuccess) {
                        // Success View
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 40.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                    .border(2.dp, Color(0xFF10B981), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "¡Dispositivo Vinculado!",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Iniciando sesión en tu televisor...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = NexusTextSecondary)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(color = NexusPrimary, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        // Main Pairing UI (Side-by-side or stacked)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: QR Code
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    modifier = Modifier
                                        .size(220.dp)
                                        .scale(pulseScale)
                                        .padding(12.dp)
                                ) {
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = "Código QR de Vinculación",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = Color.Black)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "1. Escanea con la cámara de tu celular",
                                    color = NexusTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Right: PIN and instructions
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text(
                                    text = "O INGRESA ESTE CÓDIGO:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 2.sp,
                                        color = NexusTextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // PIN Badge
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = NexusSurfaceVariant,
                                    border = BorderStroke(1.5.dp, NexusPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp, horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val formattedPin = if (pinCode.length >= 6) {
                                            "${pinCode.substring(0, 3)}  ${pinCode.substring(3)}"
                                        } else pinCode

                                        Text(
                                            text = formattedPin,
                                            style = MaterialTheme.typography.displaySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 6.sp,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Instructions List
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InstructionItem(
                                        number = "1",
                                        text = "Abre Nexo en tu teléfono móvil."
                                    )
                                    InstructionItem(
                                        number = "2",
                                        text = "Ve a Mi Perfil y pulsa 'Vincular Televisor'."
                                    )
                                    InstructionItem(
                                        number = "3",
                                        text = "Escribe el código PIN de arriba para entrar al instante."
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .background(
                                            NexusPrimary.copy(alpha = 0.12f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = NexusPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Esperando confirmación desde tu teléfono...",
                                        color = NexusPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Switch back to manual keyboard
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NexusBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = NexusTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Prefiero escribir con el control remoto",
                                color = NexusTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionItem(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(NexusPrimary.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, NexusPrimary.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = NexusPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp
        )
    }
}

/**
 * Dialog on the Mobile Phone allowing user to link an active TV by typing PIN or selecting detected TV.
 */
@Composable
fun PhoneLinkTvDialog(
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var tvIpInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    var discoveredTvs by remember { mutableStateOf<List<TvLinkManager.DiscoveredTv>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val currentServerUrl = AppStorage.getServerUrl()
    val currentUsername = AppStorage.getUsername()
    val currentPassword = AppStorage.getPassword()

    // Auto-discover TV on same Wi-Fi
    LaunchedEffect(Unit) {
        isScanning = true
        try {
            val list = TvLinkManager.discoverTvOnLocalSubnet()
            discoveredTvs = list
            if (list.isNotEmpty()) {
                tvIpInput = list.first().ip
                if (list.first().pin.isNotBlank()) {
                    pinInput = list.first().pin
                }
            }
        } finally {
            isScanning = false
        }
    }

    fun submitPairing(targetIp: String, pin: String) {
        if (pin.length < 6) {
            statusMessage = "Por favor ingresa los 6 dígitos del código PIN"
            isSuccess = false
            return
        }
        if (currentUsername.isBlank() || currentPassword.isBlank()) {
            statusMessage = "No tienes una sesión iniciada en este teléfono para transferir"
            isSuccess = false
            return
        }

        isSending = true
        statusMessage = "Conectando con el televisor..."
        focusManager.clearFocus()

        coroutineScope.launch {
            val result = TvLinkManager.sendCredentialsToTv(
                tvIpOrUrl = targetIp.ifBlank { "192.168.1.1" },
                pin = pin,
                serverUrl = currentServerUrl,
                username = currentUsername,
                password = currentPassword
            )

            isSending = false
            result.onSuccess { msg ->
                isSuccess = true
                statusMessage = msg
                delay(2000)
                onDismiss()
            }.onFailure { err ->
                isSuccess = false
                statusMessage = err.message ?: "Error al transferir credenciales"
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NexusSurface,
                border = BorderStroke(1.dp, NexusBorder),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(NexusPrimary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = NexusPrimary, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "Vincular con Televisor",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = NexusTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Transfiere tu cuenta activa de este teléfono a tu Smart TV o Android TV al instante sin escribir contraseñas.",
                        style = MaterialTheme.typography.bodySmall.copy(color = NexusTextSecondary),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Account summary to transfer
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NexusSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = NexusPrimary, modifier = Modifier.size(28.dp))
                            Column {
                                Text(
                                    text = "Cuenta a transferir:",
                                    fontSize = 11.sp,
                                    color = NexusTextSecondary
                                )
                                Text(
                                    text = currentUsername.ifBlank { "Sin sesión activa" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Detected TVs Section
                    if (discoveredTvs.isNotEmpty()) {
                        Text(
                            text = "TELEVISORES ENCONTRADOS EN WI-FI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                color = NexusPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        discoveredTvs.forEach { tv ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NexusSurfaceVariant,
                                border = BorderStroke(1.dp, NexusPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable {
                                        tvIpInput = tv.ip
                                        pinInput = tv.pin
                                        submitPairing(tv.ip, tv.pin)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.ConnectedTv, contentDescription = null, tint = NexusPrimary)
                                        Column {
                                            Text("Nexo Android TV", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("IP: ${tv.ip}", color = NexusTextSecondary, fontSize = 11.sp)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            tvIpInput = tv.ip
                                            pinInput = tv.pin
                                            submitPairing(tv.ip, tv.pin)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Conectar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // PIN Code Input Field
                    Text(
                        text = "CÓDIGO PIN MOSTRADO EN TU TV (6 DÍGITOS)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            color = NexusTextSecondary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it },
                        placeholder = { Text("Ej: 742918", color = NexusTextSecondary.copy(alpha = 0.5f)) },
                        leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null, tint = NexusPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val ip = tvIpInput.ifBlank {
                                discoveredTvs.firstOrNull()?.ip ?: "192.168.1.1"
                            }
                            submitPairing(ip, pinInput)
                        }),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tv_pin_input")
                    )

                    // Optional manual TV IP if not auto-detected
                    if (discoveredTvs.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = tvIpInput,
                            onValueChange = { tvIpInput = it },
                            placeholder = { Text("IP de la TV (Opcional, ej: 192.168.1.50)", color = NexusTextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = NexusTextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Status / Alert message
                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSuccess) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusMessage!!,
                                color = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Submit Action Button
                    Button(
                        onClick = {
                            val ip = tvIpInput.ifBlank {
                                discoveredTvs.firstOrNull()?.ip ?: "192.168.1.1"
                            }
                            submitPairing(ip, pinInput)
                        },
                        enabled = !isSending && pinInput.length == 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("send_to_tv_btn")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Enviando...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.SendToMobile, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Transferir Sesión al Televisor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
