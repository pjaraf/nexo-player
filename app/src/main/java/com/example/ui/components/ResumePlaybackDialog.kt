package com.example.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun ResumePlaybackDialog(
    title: String,
    coverUrl: String? = null,
    positionMs: Long,
    durationMs: Long,
    onResume: () -> Unit,
    onStartFromBeginning: () -> Unit,
    onDismiss: () -> Unit
) {
    val continueFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            continueFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0L)
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    val progressFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, Color(0xFF007AFF).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .shadow(24.dp, RoundedCornerShape(20.dp)),
                color = Color(0xFF14151F),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header con carátula y títulos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 65.dp, height = 95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "¿Deseas continuar viendo?",
                                color = Color(0xFFFFC107),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Te quedaste en ${formatTime(positionMs)}" + if (durationMs > 0) " de ${formatTime(durationMs)}" else "",
                                color = Color(0xFFA0A3BD),
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Barra de progreso visual
                    if (progressFraction > 0f) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .height(6.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFE50914), Color(0xFFFFC107))
                                            ),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Botones de acción D-Pad
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Continuar viendo
                        TvDialogButton(
                            icon = Icons.Default.PlayArrow,
                            text = "Continuar viendo (${formatTime(positionMs)})",
                            isPrimary = true,
                            focusRequester = continueFocusRequester,
                            onClick = onResume
                        )

                        // 2. Empezar del inicio
                        TvDialogButton(
                            icon = Icons.Default.Replay,
                            text = "Empezar desde el inicio",
                            isPrimary = false,
                            onClick = onStartFromBeginning
                        )

                        // 3. Cancelar
                        TvDialogButton(
                            icon = Icons.Default.Close,
                            text = "Cancelar",
                            isPrimary = false,
                            isCancel = true,
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDialogButton(
    icon: ImageVector,
    text: String,
    isPrimary: Boolean = false,
    isCancel: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val tvFocusBlue = Color(0xFF007AFF)
    val tvFocusGold = Color(0xFFFFC107)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        color = when {
            isFocused -> tvFocusBlue
            isPrimary -> Color(0xFFE50914)
            isCancel -> Color.White.copy(alpha = 0.08f)
            else -> Color.White.copy(alpha = 0.16f)
        },
        border = if (isFocused) {
            BorderStroke(2.5.dp, tvFocusGold)
        } else if (isPrimary) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
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
            .testTag("dialog_btn_${text.take(10).lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (isPrimary || isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
