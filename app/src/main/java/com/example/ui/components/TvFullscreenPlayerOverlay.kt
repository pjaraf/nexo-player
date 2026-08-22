package com.example.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
    val focusColor = Color(0xFFE50914) // Netflix Red
    val focusGold = Color(0xFFFFC107)
    val playPauseFocusRequester = remember { FocusRequester() }

    BackHandler {
        onExit()
    }

    LaunchedEffect(Unit) {
        try {
            delay(120)
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
            .background(Color.Black.copy(alpha = 0.5f))
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    onUserInteraction?.invoke()
                }
                false
            }
    ) {
        // Botón Central Gigante de Play/Pause
        var isCenterPlayFocused by remember { mutableStateOf(false) }
        val centerScale by animateFloatAsState(
            targetValue = if (isCenterPlayFocused) 1.15f else 1.0f,
            animationSpec = tween(150),
            label = "center_play_scale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .scale(centerScale)
                .size(84.dp)
                .clip(CircleShape)
                .background(if (isCenterPlayFocused || !isPlaying) focusColor else Color.Black.copy(alpha = 0.6f))
                .border(3.5.dp, if (isCenterPlayFocused) focusGold else focusColor, CircleShape)
                .focusRequester(playPauseFocusRequester)
                .focusable()
                .onFocusChanged {
                    isCenterPlayFocused = it.isFocused
                    if (it.isFocused) onUserInteraction?.invoke()
                }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        onUserInteraction?.invoke()
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                onPlayPause()
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                onRewind()
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                onForward()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .clickable { onPlayPause() }
                .testTag("tv_center_play_pause"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }

        // Fila de Controles Inferior: Carátula a la izquierda, Información + Barra + Botones a la derecha
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.98f))
                    )
                )
                .padding(horizontal = 36.dp, vertical = 24.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. Carátula / Póster
            if (thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 110.dp, height = 160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(16.dp, RoundedCornerShape(12.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF222222))
                        .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }

            // 2. Título, barra de progreso y botones de acción
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Título
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Barra de Progreso
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Track base
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        )

                        val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        // Track activo
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(6.dp)
                                .background(focusColor, CircleShape)
                        )

                        // Thumb indicador
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .wrapContentWidth(Alignment.End)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(focusGold)
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        }
                    }

                    Text(
                        text = formatTime(durationMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Fila de Botones con soporte total D-Pad
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Salir de pantalla completa
                    TvOverlayIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Salir",
                        onClick = onExit,
                        onFocused = onUserInteraction
                    )

                    // Retroceder 10s
                    TvOverlayIconButton(
                        icon = Icons.Default.FastRewind,
                        description = "Retroceder 10 segundos",
                        onClick = onRewind,
                        onFocused = onUserInteraction
                    )

                    // Play / Pause
                    TvOverlayIconButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        description = if (isPlaying) "Pausar" else "Reproducir",
                        isCircularBadge = true,
                        isPlaying = isPlaying,
                        onClick = onPlayPause,
                        onFocused = onUserInteraction
                    )

                    // Avanzar 10s
                    TvOverlayIconButton(
                        icon = Icons.Default.FastForward,
                        description = "Avanzar 10 segundos",
                        onClick = onForward,
                        onFocused = onUserInteraction
                    )

                    // Siguiente episodio (Series)
                    if (onSkipNext != null) {
                        TvOverlayIconButton(
                            icon = Icons.Default.SkipNext,
                            description = "Siguiente episodio",
                            onClick = onSkipNext,
                            onFocused = onUserInteraction
                        )
                    }

                    // Idioma y Subtítulos
                    TvOverlayIconButton(
                        icon = Icons.Default.Language,
                        description = "Audio y Subtítulos",
                        onClick = onSubtitles,
                        onFocused = onUserInteraction
                    )

                    // Formato de pantalla
                    TvOverlayIconButton(
                        icon = Icons.Default.PictureInPictureAlt,
                        description = "Formato de Pantalla",
                        onClick = { onAspectRatio?.invoke() },
                        onFocused = onUserInteraction
                    )
                }
            }
        }
    }
}

@Composable
private fun TvOverlayIconButton(
    icon: ImageVector,
    description: String,
    isCircularBadge: Boolean = false,
    isPlaying: Boolean = false,
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
        targetValue = if (isFocused) 1.20f else 1.0f,
        animationSpec = tween(150),
        label = "icon_btn_scale"
    )

    val focusColor = Color(0xFFE50914)
    val focusGold = Color(0xFFFFC107)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = if (isCircularBadge) CircleShape else RoundedCornerShape(10.dp),
        color = when {
            isFocused -> focusColor
            isCircularBadge -> Color.White.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        border = if (isFocused) {
            androidx.compose.foundation.BorderStroke(2.5.dp, focusGold)
        } else if (isCircularBadge) {
            androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
        } else null,
        modifier = Modifier
            .size(52.dp)
            .scale(scale)
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
            .testTag("tv_btn_${description.lowercase().replace(" ", "_")}")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(if (isCircularBadge) 28.dp else 26.dp)
            )
        }
    }
}
