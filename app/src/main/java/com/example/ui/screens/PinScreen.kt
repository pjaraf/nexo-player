package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.storage.AppStorage
import com.example.ui.theme.*

@Composable
fun PinScreen(
    action: String = "enter", // "enter", "set", "remove"
    onSuccess: () -> Unit,
    onClose: () -> Unit
) {
    var step by remember { mutableStateOf("pin") } // "pin" or "confirm"
    var pinValue by remember { mutableStateOf("") }
    var confirmValue by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val currentVal = if (step == "pin") pinValue else confirmValue
    val existingPin = remember { AppStorage.getPin() }

    fun handleFilled(valFilled: String) {
        when (action) {
            "enter" -> {
                if (valFilled == existingPin) {
                    onSuccess()
                } else {
                    errorMsg = "PIN incorrecto"
                    pinValue = ""
                }
            }
            "set" -> {
                step = "confirm"
            }
            "remove" -> {
                if (valFilled == existingPin) {
                    AppStorage.clearPin()
                    onSuccess()
                } else {
                    errorMsg = "PIN incorrecto"
                    pinValue = ""
                }
            }
        }
    }

    fun handleConfirmed(valConfirmed: String) {
        if (valConfirmed == pinValue) {
            AppStorage.setPin(pinValue)
            onSuccess()
        } else {
            errorMsg = "Los PIN no coinciden"
            confirmValue = ""
        }
    }

    fun onKeyPress(digit: String) {
        errorMsg = null
        if (currentVal.length >= 4) return
        val next = currentVal + digit
        if (step == "pin") {
            pinValue = next
            if (next.length == 4) handleFilled(next)
        } else {
            confirmValue = next
            if (next.length == 4) handleConfirmed(next)
        }
    }

    fun onBackspace() {
        errorMsg = null
        if (step == "pin") {
            if (pinValue.isNotEmpty()) pinValue = pinValue.dropLast(1)
        } else {
            if (confirmValue.isNotEmpty()) confirmValue = confirmValue.dropLast(1)
        }
    }

    val title = when (action) {
        "set" -> if (step == "pin") "Crea un PIN de 4 dígitos" else "Confirma tu nuevo PIN"
        "remove" -> "Ingresa tu PIN actual"
        else -> "Ingresa tu PIN de acceso"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("pin_screen")
    ) {
        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("pin_close_btn")
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NexusPrimary,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4 PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { i ->
                        val isFilled = currentVal.length > i
                        val isErr = errorMsg != null
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isErr -> NexusPrimary
                                        isFilled -> Color.White
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    2.dp,
                                    if (isErr) NexusPrimary else if (isFilled) Color.White else NexusBorder,
                                    CircleShape
                                )
                        )
                    }
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg ?: "",
                        color = NexusPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // Keypad
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "back")
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                items(keys) { key ->
                    when (key) {
                        "" -> Box(modifier = Modifier.size(72.dp))
                        "back" -> {
                            var isFocused by remember { mutableStateOf(false) }
                            val borderStroke = when {
                                isFocused -> androidx.compose.foundation.BorderStroke(2.dp, TvFocusBlue)
                                else -> androidx.compose.foundation.BorderStroke(1.dp, NexusBorder)
                            }
                            val bgColor = if (isFocused) Color(0xFF1E2638) else NexusSurfaceVariant

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = bgColor,
                                border = borderStroke,
                                modifier = Modifier
                                    .size(72.dp)
                                    .focusable()
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .clickable { onBackspace() }
                                    .testTag("pin_backspace_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Borrar",
                                        tint = if (isFocused) TvFocusBlue else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            var isFocused by remember { mutableStateOf(false) }
                            val borderStroke = when {
                                isFocused -> androidx.compose.foundation.BorderStroke(2.dp, TvFocusBlue)
                                else -> androidx.compose.foundation.BorderStroke(1.dp, NexusBorder)
                            }
                            val bgColor = if (isFocused) Color(0xFF1E2638) else NexusSurfaceVariant

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = bgColor,
                                border = borderStroke,
                                modifier = Modifier
                                    .size(72.dp)
                                    .focusable()
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .clickable { onKeyPress(key) }
                                    .testTag("pin_key_$key")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFocused) Color.White else Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
