package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.NexusPrimary
import com.example.utils.ScreenCastHelper

@Composable
fun ScreenCastDialog(
    streamUrl: String? = null,
    title: String = "Nexo Player",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161722),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3048)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(NexusPrimary.copy(alpha = 0.15f))
                        .border(1.5.dp, NexusPrimary.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Transmitir Pantalla",
                        tint = NexusPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Transmitir Pantalla a Smart TV",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = "Proyecta en vivo hacia televisores Samsung (Smart View), LG, Android TV, Google TV, Roku o Fire TV.",
                    color = Color(0xFFA0A3BD),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Step Instructions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2030))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StepItem(
                        icon = Icons.Default.Wifi,
                        text = "1. Conecta tu teléfono y tu televisor a la misma red Wi-Fi."
                    )
                    StepItem(
                        icon = Icons.Default.Tv,
                        text = "2. Pulsa en 'Buscar Televisores' para abrir el menú de emisión inalámbrica."
                    )
                    StepItem(
                        icon = Icons.Default.PlayCircle,
                        text = "3. Selecciona tu TV en la lista para empezar a ver en pantalla gigante."
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Primary Action Button: Open Screen Cast / Wireless Display
                Button(
                    onClick = {
                        ScreenCastHelper.openCastSettings(context)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Buscar Televisores / Transmitir",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Secondary Action Button: If a video stream is available, offer external player / casting apps
                if (!streamUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            ScreenCastHelper.openInExternalPlayer(context, streamUrl, title)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F4264)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transmitir con Web Video Cast / VLC",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dismiss Text Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cerrar",
                        color = Color(0xFFA0A3BD),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StepItem(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFF9800),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.5.sp,
            lineHeight = 16.sp
        )
    }
}
