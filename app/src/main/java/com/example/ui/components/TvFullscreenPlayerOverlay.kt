package com.example.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
    onAspectRatio: (() -> Unit)? = null
) {
    val focusColor = Color(0xFFE50914) // Netflix Red
    val focusGold = Color(0xFFFFC107)
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            delay(150)
            playPauseFocusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        // Big Center Play/Pause Button
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
                .border(3.dp, if (isCenterPlayFocused) focusGold else focusColor, CircleShape)
                .focusable()
                .onFocusChanged { isCenterPlayFocused = it.isFocused }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                onPlayPause()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .clickable { onPlayPause() }
                .focusRequester(playPauseFocusRequester),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }

        // Bottom Controls Row: Large Poster Cover on left, Info + Progress Bar + Control Buttons on right
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
            // 1. CARÁTULA / PÓSTER GRANDE (AL LADO DE LA BARRA DE PROGRESO)
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

            // 2. COLUMNA DERECHA: TÍTULO, BARRA DE PROGRESO Y BOTONES
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Movie / Series Title
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // BARRA DE PROGRESO (Directamente al lado de la carátula)
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

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Background track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        )

                        val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        // Active track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(5.dp)
                                .background(focusColor, CircleShape)
                        )

                        // Thumb
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

                // Control Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Back / Exit
                    TvOverlayIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Salir",
                        onClick = onExit
                    )

                    // Rewind 10s
                    TvOverlayIconButton(
                        icon = Icons.Default.FastRewind,
                        description = "Retroceder 10 segundos",
                        onClick = onRewind
                    )

                    // Play / Pause Mini Button
                    TvOverlayIconButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        description = if (isPlaying) "Pausar" else "Reproducir",
                        isCircularBadge = true,
                        isPlaying = isPlaying,
                        onClick = onPlayPause
                    )

                    // Forward 10s
                    TvOverlayIconButton(
                        icon = Icons.Default.FastForward,
                        description = "Avanzar 10 segundos",
                        onClick = onForward
                    )

                    // Skip Next (Optional for Series)
                    if (onSkipNext != null) {
                        TvOverlayIconButton(
                            icon = Icons.Default.SkipNext,
                            description = "Siguiente episodio",
                            onClick = onSkipNext
                        )
                    }

                    // Subtitles / Audio
                    TvOverlayIconButton(
                        icon = Icons.Default.Language,
                        description = "Audio y Subtítulos",
                        onClick = onSubtitles
                    )

                    // Aspect Ratio
                    TvOverlayIconButton(
                        icon = Icons.Default.PictureInPictureAlt,
                        description = "Formato de Pantalla",
                        onClick = { onAspectRatio?.invoke() }
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
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.20f else 1.0f,
        animationSpec = tween(150),
        label = "icon_btn_scale"
    )

    val focusColor = Color(0xFFE50914)
    val focusGold = Color(0xFFFFC107)

    Box(
        modifier = Modifier
            .size(52.dp)
            .scale(scale)
            .clip(if (isCircularBadge) CircleShape else RoundedCornerShape(10.dp))
            .background(
                when {
                    isFocused -> focusColor
                    isCircularBadge -> Color.White.copy(alpha = 0.15f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else if (isCircularBadge) 1.5.dp else 0.dp,
                color = if (isFocused) focusGold else if (isCircularBadge) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                shape = if (isCircularBadge) CircleShape else RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
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
            .clickable { onClick() },
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

