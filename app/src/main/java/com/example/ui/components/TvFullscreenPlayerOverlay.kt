package com.example.ui.components

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            delay(100)
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
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isCenterPlayFocused || !isPlaying) focusColor else Color.Transparent)
                .border(2.dp, if (isCenterPlayFocused) Color.White else focusColor, CircleShape)
                .focusable()
                .onFocusChanged { isCenterPlayFocused = it.isFocused }
                .clickable { onPlayPause() }
                .focusRequester(playPauseFocusRequester),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(50.dp)
            )
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
                .padding(horizontal = 40.dp, vertical = 30.dp),
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
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right: Control Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Back / Exit
                    var focusExit by remember { mutableStateOf(false) }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Salir",
                        tint = if (focusExit) focusColor else Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .focusable()
                            .onFocusChanged { focusExit = it.isFocused }
                            .clickable { onExit() }
                    )

                    // Rewind
                    var focusRew by remember { mutableStateOf(false) }
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Retroceder",
                        tint = if (focusRew) focusColor else Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .focusable()
                            .onFocusChanged { focusRew = it.isFocused }
                            .clickable { onRewind() }
                    )

                    // Play/Pause (Circle)
                    var focusPlay by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (focusPlay) focusColor else Color.White, CircleShape)
                            .focusable()
                            .onFocusChanged { focusPlay = it.isFocused }
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = if (focusPlay) focusColor else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Forward
                    var focusFwd by remember { mutableStateOf(false) }
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Avanzar",
                        tint = if (focusFwd) focusColor else Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .focusable()
                            .onFocusChanged { focusFwd = it.isFocused }
                            .clickable { onForward() }
                    )

                    // Skip Next (Optional)
                    if (onSkipNext != null) {
                        var focusSkip by remember { mutableStateOf(false) }
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = if (focusSkip) focusColor else Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .focusable()
                                .onFocusChanged { focusSkip = it.isFocused }
                                .clickable { onSkipNext() }
                        )
                    }

                    // Subtitles
                    var focusSubs by remember { mutableStateOf(false) }
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Subtítulos",
                        tint = if (focusSubs) focusColor else Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .focusable()
                            .onFocusChanged { focusSubs = it.isFocused }
                            .clickable { onSubtitles() }
                    )

                    // PIP / Aspect Ratio
                    var focusPip by remember { mutableStateOf(false) }
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Aspect Ratio",
                        tint = if (focusPip) focusColor else Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .focusable()
                            .onFocusChanged { focusPip = it.isFocused }
                            .clickable { onAspectRatio?.invoke() }
                    )
                }
            }
        }
    }
}
