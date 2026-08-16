package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.storage.AppStorage
import com.example.ui.components.PhoneLinkTvDialog
import com.example.ui.components.TvQrLoginDialog
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils

// High-resolution authentic TMDB blockbuster movie posters for the Netflix collage
private val POSTERS_COL_1 = listOf(
    "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg", // Deadpool & Wolverine
    "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", // Dune Part 2
    "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", // Inside Out 2
    "https://image.tmdb.org/t/p/w500/cxevDYdeFUrqaR2aAPMxn4Cx8YJ.jpg", // Godzilla x Kong
    "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"  // Oppenheimer
)

private val POSTERS_COL_2 = listOf(
    "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg", // Avengers Endgame
    "https://image.tmdb.org/t/p/w500/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg", // Avengers Infinity War
    "https://image.tmdb.org/t/p/w500/A4j8S6mo02Feo8PfOTDpEcyFmng.jpg", // The Batman
    "https://image.tmdb.org/t/p/w500/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg", // Barbie
    "https://image.tmdb.org/t/p/w500/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg"  // Kung Fu Panda 4
)

private val POSTERS_COL_3 = listOf(
    "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", // Avatar 2
    "https://image.tmdb.org/t/p/w500/gKkl37BQuKTanygYQG1pyYgLVgf.jpg", // Planet of the Apes
    "https://image.tmdb.org/t/p/w500/NNxYkU70HPurnNCSiCjYAmacwm.jpg", // Mission Impossible
    "https://image.tmdb.org/t/p/w500/sh7Rg8Er3tFcN9BpKIPOMvALgZd.jpg", // Civil War
    "https://image.tmdb.org/t/p/w500/1E5baAaEse26fej7uHcjOgEE2t2.jpg"  // Fast X
)

private val POSTERS_COL_4 = listOf(
    "https://image.tmdb.org/t/p/w500/d5NXSklXo0qyIYkgV94XAgMIckC.jpg", // Dune
    "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg", // Fight Club
    "https://image.tmdb.org/t/p/w500/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg", // Shawshank
    "https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", // Godfather
    "https://image.tmdb.org/t/p/w500/fiVW06jE7z9YnO4trhaMEdclSiC.jpg"  // Fast 9
)

private val POSTERS_COL_5 = listOf(
    "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg",
    "https://image.tmdb.org/t/p/w500/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg",
    "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
    "https://image.tmdb.org/t/p/w500/cxevDYdeFUrqaR2aAPMxn4Cx8YJ.jpg",
    "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || isTv
    val isLargeTv = isTv || configuration.screenWidthDp >= 900
    var username by remember { mutableStateOf(AppStorage.getUsername()) }
    var password by remember { mutableStateOf(AppStorage.getPassword()) }
    var showPassword by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var showSubscribeDialog by remember { mutableStateOf(false) }
    var showTvQrDialog by remember { mutableStateOf(false) }
    var showPhoneLinkDialog by remember { mutableStateOf(false) }

    val isOnline by viewModel.isOnline.collectAsState()
    val loading by viewModel.loginLoading.collectAsState()
    val error by viewModel.loginError.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("login_screen")
    ) {
        // --- 1. Tilted Perspective Poster Grid Wall (Vibrant Netflix backdrop look) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -12f
                    scaleX = 1.35f
                    scaleY = 1.35f
                    translationX = -40f
                    translationY = -30f
                }
                .alpha(0.72f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Column 1
                PosterColumn(
                    posters = POSTERS_COL_1,
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-40).dp)
                )
                // Column 2
                PosterColumn(
                    posters = POSTERS_COL_2,
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = 30.dp)
                )
                // Column 3
                PosterColumn(
                    posters = POSTERS_COL_3,
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-50).dp)
                )
                // Column 4
                PosterColumn(
                    posters = POSTERS_COL_4,
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = 20.dp)
                )
                // Column 5
                PosterColumn(
                    posters = POSTERS_COL_5,
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-30).dp)
                )
            }
        }

        // --- 2. Cinematic Ambient Glow (Netflix Crimson Red Glow & Soft Dark Vignette) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x22E50914), // Subtle red cinematic glow
                            Color(0x55000000),
                            Color(0xBB000000)
                        ),
                        radius = 1100f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        // --- 3. Top-Left Brand Logo (Exact placement with safe TV margins) ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .statusBarsPadding()
                .padding(
                    start = if (isLargeTv) 36.dp else 24.dp,
                    top = if (isLargeTv) 24.dp else 16.dp,
                    bottom = 12.dp
                )
                .align(Alignment.TopStart)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_nexus_logo),
                contentDescription = "Nexo Logo",
                modifier = Modifier
                    .size(if (isLargeTv) 44.dp else 38.dp)
                    .clip(CircleShape)
            )

            Text(
                text = "NEXO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (isLargeTv) 34.sp else 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = Color(0xFFE50914),
                    fontFamily = FontFamily.SansSerif
                )
            )
        }

        // --- 4. Centered Login Form Card (Optimized for 1080p/4K TVs to fit completely on-screen) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (isLargeTv) 32.dp else 20.dp,
                    vertical = if (isLargeTv) 16.dp else 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = if (isLargeTv) 460.dp else 440.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isLargeTv) 32.dp else 28.dp,
                        vertical = if (isLargeTv) 24.dp else 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isLargeTv) 12.dp else 14.dp)
                ) {
                    // Header Title
                    Text(
                        text = "Iniciar sesión",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = if (isLargeTv) 28.sp else 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // Offline warning banner
                    if (!isOnline) {
                        Surface(
                            color = Color(0xFFD32F2F).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE50914)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Sin conexión a internet. La aplicación esperará hasta que vuelvas a estar en línea para iniciar sesión.",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Error message if any
                    if (!error.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFE87C03).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE87C03)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error ?: "",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Username input field
                    var isUsernameFocused by remember { mutableStateOf(false) }
                    TextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = {
                            Text(
                                text = "Usuario",
                                color = Color(0xFFB3B3B3),
                                fontSize = if (isLargeTv) 13.sp else 14.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = TvFocusBlue,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLargeTv) 50.dp else 56.dp)
                            .onFocusChanged { isUsernameFocused = it.isFocused }
                            .border(
                                width = if (isUsernameFocused) 2.dp else 1.dp,
                                color = if (isUsernameFocused) TvFocusBlue else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .testTag("login_input_username")
                    )

                    // Password input field
                    var isPasswordFocused by remember { mutableStateOf(false) }
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = {
                            Text(
                                text = "Contraseña",
                                color = Color(0xFFB3B3B3),
                                fontSize = if (isLargeTv) 13.sp else 14.sp
                            )
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = { showPassword = !showPassword },
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = if (showPassword) "OCULTAR" else "MOSTRAR",
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = TvFocusBlue,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                viewModel.login(username, password, onLoginSuccess)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLargeTv) 50.dp else 56.dp)
                            .onFocusChanged { isPasswordFocused = it.isFocused }
                            .border(
                                width = if (isPasswordFocused) 2.dp else 1.dp,
                                color = if (isPasswordFocused) TvFocusBlue else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .testTag("login_input_password")
                    )

                    Spacer(modifier = Modifier.height(if (isLargeTv) 4.dp else 8.dp))

                    // Primary Login Button (Blue on Focus, Red on select / normal)
                    var isLoginBtnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            viewModel.login(username, password, onLoginSuccess)
                        },
                        enabled = !loading,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLoginBtnFocused) TvFocusBlue else TvSelectedRed,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFE50914).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLargeTv) 46.dp else 50.dp)
                            .onFocusChanged { isLoginBtnFocused = it.isFocused }
                            .border(
                                width = if (isLoginBtnFocused) 2.dp else 0.dp,
                                color = if (isLoginBtnFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .testTag("login_submit_btn")
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Iniciar sesión",
                                color = Color.White,
                                fontSize = if (isLargeTv) 15.sp else 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Remember me Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { rememberMe = !rememberMe }
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFE50914),
                                    uncheckedColor = Color(0xFF737373),
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recuérdame",
                                color = Color(0xFFB3B3B3),
                                fontSize = if (isLargeTv) 12.sp else 13.sp
                            )
                        }
                    }

                    if (isTv) {
                        Spacer(modifier = Modifier.height(if (isLargeTv) 8.dp else 14.dp))

                        // TV Quick Login via Mobile Code / PIN Box (Shown ONLY on Smart TV / Google TV)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF222232).copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTvQrDialog = true }
                                .testTag("login_tv_qr_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = if (isLargeTv) 8.dp else 12.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFFE50914).copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Código PIN",
                                            tint = Color(0xFFE50914),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Iniciar con Código PIN",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Ingresa el código desde tu teléfono",
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Icon(
                                    Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subscribe & Signup Footer Note
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "¿Todavía sin Nexo? ",
                            color = Color(0xFF737373),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Suscríbete ya.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable {
                                showSubscribeDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // TV QR & PIN Login Dialog (For Smart TV / Google TV)
        if (showTvQrDialog) {
            TvQrLoginDialog(
                onDismiss = { showTvQrDialog = false },
                onLoginSuccess = { serverUrl, user, pass ->
                    showTvQrDialog = false
                    username = user
                    password = pass
                    viewModel.login(user, pass, onLoginSuccess)
                }
            )
        }

        // Phone Link TV Dialog (For mobile phones transferring credentials)
        if (showPhoneLinkDialog) {
            PhoneLinkTvDialog(
                onDismiss = { showPhoneLinkDialog = false }
            )
        }

        // WhatsApp Subscription Dialog
        if (showSubscribeDialog) {
            AlertDialog(
                onDismissRequest = { showSubscribeDialog = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xFF181818),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCCCCCC),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366).copy(alpha = 0.15f))
                            .border(1.5.dp, Color(0xFF25D366), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "WhatsApp",
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Suscripción Nexo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Para obtener tu cuenta o renovar tu plan, contáctanos a través de WhatsApp:",
                            fontSize = 14.sp,
                            color = Color(0xFFBBBBBB),
                            textAlign = TextAlign.Center
                        )

                        // Highlighted WhatsApp Number Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF232323),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "+569 5939 6963",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF25D366)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSubscribeDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Entendido",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun PosterColumn(
    posters: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        posters.forEach { url ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1F1F))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
