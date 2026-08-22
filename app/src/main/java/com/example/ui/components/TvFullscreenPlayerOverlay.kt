package com.example.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun TvFullscreenPlayerOverlay(
    isPlaying: Boolean,
    title: String,
    thumbnailUrl: String,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onExit: () -> Unit,
    onSubtitles: () -> Unit,
    onSkipNext: (() -> Unit)? = null,
    onAspectRatio: (() -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null
) {
    val playPauseFocusRequester = remember { FocusRequester() }

    BackHandler {
        onExit()
    }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            playPauseFocusRequester.requestFocus()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.75f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.6f),
                        Color.Black.copy(alpha = 0.95f)
                    )
                )
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    onUserInteraction?.invoke()
                    if (keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                        onExit()
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        // 1. Barra Superior: Título y botón volver
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TvPlayerActionButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Volver",
                onClick = onExit,
                onFocused = onUserInteraction
            )

            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. Fila Inferior: Póster + Información + Barra de Tiempo + Botones de Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Póster o carátula
            if (thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 95.dp, height = 140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .shadow(12.dp, RoundedCornerShape(10.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                )
            }

            // Panel de Control Principal
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Barra de Progreso y Tiempo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Track de fondo
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape)
                        )

                        val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        // Barra de progreso activa
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(6.dp)
                                .background(Color(0xFFE50914), CircleShape)
                        )

                        // Indicador / Thumb
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .wrapContentWidth(Alignment.End)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFC107))
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        }
                    }

                    Text(
                        text = formatTime(durationMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Botones de Reproducción y Opciones (D-Pad navegables en una sola fila continua)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Retroceder 10s
                    TvPlayerActionButton(
                        icon = Icons.Default.FastRewind,
                        label = "-10s",
                        onClick = onRewind,
                        onFocused = onUserInteraction
                    )

                    // 2. Play / Pausa (Foco Principal Inicial)
                    TvPlayerActionButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = if (isPlaying) "Pausar" else "Reproducir",
                        isPrimaryPlay = true,
                        focusRequester = playPauseFocusRequester,
                        onClick = onPlayPause,
                        onFocused = onUserInteraction
                    )

                    // 3. Avanzar 10s
                    TvPlayerActionButton(
                        icon = Icons.Default.FastForward,
                        label = "+10s",
                        onClick = onForward,
                        onFocused = onUserInteraction
                    )

                    // 4. Siguiente Episodio (Para Series)
                    if (onSkipNext != null) {
                        TvPlayerActionButton(
                            icon = Icons.Default.SkipNext,
                            label = "Sig. Ep.",
                            onClick = onSkipNext,
                            onFocused = onUserInteraction
                        )
                    }

                    // 5. Idioma y Subtítulos
                    TvPlayerActionButton(
                        icon = Icons.Default.Language,
                        label = "Audio / Sub",
                        onClick = onSubtitles,
                        onFocused = onUserInteraction
                    )

                    // 6. Formato de Pantalla (Ajuste / Zoom / 16:9)
                    if (onAspectRatio != null) {
                        TvPlayerActionButton(
                            icon = Icons.Default.AspectRatio,
                            label = "Pantalla",
                            onClick = onAspectRatio,
                            onFocused = onUserInteraction
                        )
                    }

                    // 7. Salir de la película / Volver
                    TvPlayerActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "Salir",
                        onClick = onExit,
                        onFocused = onUserInteraction
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPlayerActionButton(
    icon: ImageVector,
    label: String,
    isPrimaryPlay: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocused: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(120),
        label = "btn_scale"
    )

    val tvFocusBlue = Color(0xFF007AFF)
    val tvFocusGold = Color(0xFFFFC107)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        color = when {
            isFocused -> tvFocusBlue
            isPrimaryPlay -> Color(0xFFE50914)
            else -> Color.White.copy(alpha = 0.16f)
        },
        border = if (isFocused) {
            BorderStroke(2.5.dp, tvFocusGold)
        } else if (isPrimaryPlay) {
            BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        },
        modifier = Modifier
            .scale(scale)
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
            .testTag("tv_ctrl_${label.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(if (isPrimaryPlay) 22.dp else 19.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
