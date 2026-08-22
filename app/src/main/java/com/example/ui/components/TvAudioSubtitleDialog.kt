package com.example.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class TvMediaTrackOption(
    val id: Int,
    val label: String,
    val isSelected: Boolean
)

@Composable
fun TvAudioSubtitleDialog(
    show: Boolean,
    availableAudioTracks: List<TvMediaTrackOption>,
    availableSubtitleTracks: List<TvMediaTrackOption>,
    isSubtitlesDisabled: Boolean,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    val firstTrackFocusRequester = remember { FocusRequester() }
    val okButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        delay(120)
        try {
            firstTrackFocusRequester.requestFocus()
        } catch (_: Throwable) {
            try {
                okButtonFocusRequester.requestFocus()
            } catch (_: Throwable) {}
        }
    }

    BackHandler {
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14151F),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Idioma y Subtítulos (VLC)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // --- PISTAS DE AUDIO ---
                Text(
                    text = "PISTAS DE AUDIO",
                    color = Color(0xFFFF9800),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                if (availableAudioTracks.isEmpty()) {
                    Text(
                        text = "Audio predeterminado",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    availableAudioTracks.forEachIndexed { index, track ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Surface(
                            onClick = { onSelectAudioTrack(track.id) },
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isFocused -> Color(0xFF2962FF)
                                track.isSelected -> Color(0xFFE50914)
                                else -> Color.White.copy(alpha = 0.08f)
                            },
                            border = if (isFocused) {
                                BorderStroke(2.5.dp, Color(0xFFFFC107))
                            } else if (track.isSelected) {
                                BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstTrackFocusRequester) else Modifier)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                            AndroidKeyEvent.KEYCODE_ENTER,
                                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                onSelectAudioTrack(track.id)
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = track.label,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (track.isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // --- SUBTÍTULOS ---
                Text(
                    text = "SUBTÍTULOS",
                    color = Color(0xFFFF9800),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                // Desactivados option
                val disableSubInteractionSource = remember { MutableInteractionSource() }
                val isDisableSubFocused by disableSubInteractionSource.collectIsFocusedAsState()

                Surface(
                    onClick = onDisableSubtitles,
                    interactionSource = disableSubInteractionSource,
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isDisableSubFocused -> Color(0xFF2962FF)
                        isSubtitlesDisabled -> Color(0xFFE50914)
                        else -> Color.White.copy(alpha = 0.08f)
                    },
                    border = if (isDisableSubFocused) {
                        BorderStroke(2.5.dp, Color(0xFFFFC107))
                    } else if (isSubtitlesDisabled) {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (availableAudioTracks.isEmpty()) Modifier.focusRequester(firstTrackFocusRequester) else Modifier)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                    AndroidKeyEvent.KEYCODE_ENTER,
                                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                        onDisableSubtitles()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Desactivados",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isSubtitlesDisabled) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Subtitle tracks
                availableSubtitleTracks.forEach { track ->
                    val subInteractionSource = remember { MutableInteractionSource() }
                    val isSubFocused by subInteractionSource.collectIsFocusedAsState()
                    val isChosen = track.isSelected && !isSubtitlesDisabled

                    Surface(
                        onClick = { onSelectSubtitleTrack(track.id) },
                        interactionSource = subInteractionSource,
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isSubFocused -> Color(0xFF2962FF)
                            isChosen -> Color(0xFFE50914)
                            else -> Color.White.copy(alpha = 0.08f)
                        },
                        border = if (isSubFocused) {
                            BorderStroke(2.5.dp, Color(0xFFFFC107))
                        } else if (isChosen) {
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                        AndroidKeyEvent.KEYCODE_ENTER,
                                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                            onSelectSubtitleTrack(track.id)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = track.label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isChosen) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val okInteractionSource = remember { MutableInteractionSource() }
            val isOkFocused by okInteractionSource.collectIsFocusedAsState()

            Surface(
                onClick = onDismiss,
                interactionSource = okInteractionSource,
                shape = RoundedCornerShape(8.dp),
                color = if (isOkFocused) Color(0xFF2962FF) else Color.White.copy(alpha = 0.15f),
                border = if (isOkFocused) BorderStroke(2.dp, Color(0xFFFFC107)) else null,
                modifier = Modifier
                    .focusRequester(okButtonFocusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                AndroidKeyEvent.KEYCODE_ENTER,
                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                Text(
                    text = "Aceptar",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }
    )
}
