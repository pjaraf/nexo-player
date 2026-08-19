with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

# I need to append the rest of the file
rest_of_file = """        if (showAudioSubtitlesDialog) {
            AudioSubtitlesDialog(
                availableAudioTracks = availableAudioTracks,
                availableSubtitleTracks = availableSubtitleTracks,
                isSubtitlesDisabled = isSubtitlesDisabled,
                onSelectAudio = { track -> selectAudioTrack(track) },
                onSelectSubtitle = { track -> selectSubtitleTrack(track) },
                onDismiss = { showAudioSubtitlesDialog = false }
            )
        }
    }
}

@Composable
fun AudioSubtitlesDialog(
    availableAudioTracks: List<MediaTrackOption>,
    availableSubtitleTracks: List<MediaTrackOption>,
    isSubtitlesDisabled: Boolean,
    onSelectAudio: (MediaTrackOption) -> Unit,
    onSelectSubtitle: (MediaTrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14151F),
        title = {
            Text(
                "Idiomas y Subtítulos",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Audio",
                    color = TvFocusBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (availableAudioTracks.isEmpty()) {
                    Text("No hay pistas de audio alternativas disponibles.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    availableAudioTracks.forEach { track ->
                        val isSelected = track.isSelected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectAudio(track)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = TvFocusBlue, unselectedColor = Color.White)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(track.label, color = if (isSelected) Color.White else Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Subtítulos",
                    color = TvFocusBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (availableSubtitleTracks.isEmpty()) {
                    Text("No hay subtítulos disponibles.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSubtitle(MediaTrackOption("none", "none", "Desactivar Subtítulos", isSubtitlesDisabled))
                                onDismiss()
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = isSubtitlesDisabled,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = TvFocusBlue, unselectedColor = Color.White)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Desactivar Subtítulos", color = if (isSubtitlesDisabled) Color.White else Color.Gray)
                    }
                    availableSubtitleTracks.forEach { track ->
                        val isSelected = track.isSelected && !isSubtitlesDisabled
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSubtitle(track)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = TvFocusBlue, unselectedColor = Color.White)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(track.label, color = if (isSelected) Color.White else Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Text("Listo", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}

fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
"""

if "if (showAudioSubtitlesDialog)" not in content:
    with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'a') as f:
        f.write("\n" + rest_of_file)
