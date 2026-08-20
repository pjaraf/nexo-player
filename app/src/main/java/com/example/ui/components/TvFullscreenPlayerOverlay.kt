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

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                    )
                )
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = formatTime(currentPositionMs),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                // Progress Bar
                Box(modifier = Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.CenterStart) {
                    // Background track
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
                    
                    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    // Active track
                    Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(focusColor, CircleShape))
                    
                    // Thumb
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .wrapContentWidth(Alignment.End)
                    ) {
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(focusColor))
                    }
                }
                
                Text(
                    text = formatTime(durationMs),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Info & Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Thumbnail + Title
                Row(
                    modifier = Modifier.weight(1f).padding(end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 60.dp, height = 40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        )
                    } else {
                        // Fallback icon if no thumbnail
                        Box(
                            modifier = Modifier
                                .size(width = 60.dp, height = 40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF333333)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: Control Icons
                Row(
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

                    // Skip Next (Optional)
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
        targetValue = if (isFocused) 1.18f else 1.0f,
        animationSpec = tween(150),
        label = "icon_btn_scale"
    )

    val focusColor = Color(0xFFE50914)
    val focusGold = Color(0xFFFFC107)

    Surface(
        onClick = onClick,
        shape = if (isCircularBadge) CircleShape else RoundedCornerShape(8.dp),
        color = when {
            isFocused -> focusColor
            isCircularBadge -> Color.White.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        border = when {
            isFocused -> androidx.compose.foundation.BorderStroke(2.5.dp, focusGold)
            isCircularBadge -> androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
            else -> null
        },
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
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
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(if (isCircularBadge) 26.dp else 24.dp)
            )
        }
    }
}

