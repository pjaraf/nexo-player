package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import android.view.KeyEvent as AndroidKeyEvent
import kotlinx.coroutines.delay
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
import com.example.ui.components.CinematicBackground
import com.example.ui.viewmodels.MainViewModel
import com.example.utils.DeviceUtils

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
    var showM3uDialog by remember { mutableStateOf(false) }

    val isOnline by viewModel.isOnline.collectAsState()
    val loading by viewModel.loginLoading.collectAsState()
    val error by viewModel.loginError.collectAsState()

    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) {
            delay(200)
            try {
                initialFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("login_screen")
    ) {
        CinematicBackground()

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
                    val passwordFocusRequester = remember { FocusRequester() }

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
                        keyboardActions = KeyboardActions(
                            onNext = {
                                try {
                                    passwordFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLargeTv) 50.dp else 56.dp)
                            .focusRequester(initialFocusRequester)
                            .onFocusChanged { isUsernameFocused = it.isFocused }
                            .border(
                                width = if (isUsernameFocused) 2.5.dp else 1.dp,
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
                                viewModel.login(username, password, onSuccess = onLoginSuccess)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLargeTv) 50.dp else 56.dp)
                            .focusRequester(passwordFocusRequester)
                            .onFocusChanged { isPasswordFocused = it.isFocused }
                            .border(
                                width = if (isPasswordFocused) 2.5.dp else 1.dp,
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
                            viewModel.login(username, password, onSuccess = onLoginSuccess)
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
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                        AndroidKeyEvent.KEYCODE_ENTER,
                                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                            viewModel.login(username, password, onSuccess = onLoginSuccess)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .border(
                                width = if (isLoginBtnFocused) 2.5.dp else 0.dp,
                                color = if (isLoginBtnFocused) Color(0xFFFFC107) else Color.Transparent,
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
                        var isTvQrFocused by remember { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isTvQrFocused) TvFocusBlue else Color(0xFF222232).copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isTvQrFocused) 2.5.dp else 1.dp,
                                if (isTvQrFocused) Color(0xFFFFC107) else Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isTvQrFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                            AndroidKeyEvent.KEYCODE_ENTER,
                                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                                            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                                showTvQrDialog = true
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
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
                onLoginSuccess = { payload ->
                    showTvQrDialog = false
                    if (payload.isM3u) {
                        if (!payload.m3uContent.isNullOrBlank()) {
                            viewModel.loadM3uFileContent(payload.m3uName, payload.m3uContent, onLoginSuccess)
                        } else {
                            val targetUrl = payload.m3uUrl.ifBlank { payload.serverUrl }
                            viewModel.loadM3uPlaylist(targetUrl, payload.m3uName, onLoginSuccess)
                        }
                    } else {
                        username = payload.username
                        password = payload.password
                        viewModel.login(payload.username, payload.password, payload.serverUrl, onLoginSuccess)
                    }
                }
            )
        }

        // M3U Playlist Dialog
        if (showM3uDialog) {
            M3uPlaylistDialog(
                loading = loading,
                onDismiss = { showM3uDialog = false },
                onLoadUrl = { url, name ->
                    viewModel.loadM3uPlaylist(url, name) {
                        showM3uDialog = false
                        onLoginSuccess()
                    }
                },
                onLoadFile = { fileName, content ->
                    viewModel.loadM3uFileContent(fileName, content) {
                        showM3uDialog = false
                        onLoginSuccess()
                    }
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
fun M3uPlaylistDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onLoadUrl: (url: String, name: String) -> Unit,
    onLoadFile: (fileName: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Subir Archivo, 1 = Enlace URL

    // State for File Upload
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileContent by remember { mutableStateOf<String?>(null) }
    var parsedChannelCount by remember { mutableStateOf<Int?>(null) }
    var fileErrorMessage by remember { mutableStateOf<String?>(null) }
    var customFilePlaylistName by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "lista.m3u"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex) ?: "lista.m3u"
                    }
                }
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content.isNullOrBlank()) {
                    fileErrorMessage = "El archivo seleccionado está vacío"
                    selectedFileName = null
                    selectedFileContent = null
                    parsedChannelCount = null
                } else {
                    fileErrorMessage = null
                    selectedFileName = fileName
                    selectedFileContent = content
                    customFilePlaylistName = fileName.substringBeforeLast(".")
                    val count = Regex("""#EXTINF:""", RegexOption.IGNORE_CASE).findAll(content).count()
                    parsedChannelCount = count
                }
            } catch (e: Exception) {
                fileErrorMessage = "Error al leer archivo: ${e.localizedMessage ?: "Error desconocido"}"
            }
        }
    }

    // State for URL
    var m3uUrl by remember { mutableStateOf(AppStorage.getM3uUrl().takeIf { !it.startsWith("local://") } ?: "") }
    var urlPlaylistName by remember { mutableStateOf("Mi Lista M3U") }

    var tabFileFocused by remember { mutableStateOf(false) }
    var tabUrlFocused by remember { mutableStateOf(false) }
    var selectFileBtnFocused by remember { mutableStateOf(false) }
    var fileNameInputFocused by remember { mutableStateOf(false) }
    var urlInputFocused by remember { mutableStateOf(false) }
    var urlNameInputFocused by remember { mutableStateOf(false) }
    var confirmFocused by remember { mutableStateOf(false) }
    var cancelFocused by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF1A1A24),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCCCCCC),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(TvFocusBlue.copy(alpha = 0.2f))
                    .border(1.5.dp, TvFocusBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (selectedTab == 0) Icons.Default.FileUpload else Icons.Default.Link,
                    contentDescription = "Lista M3U",
                    tint = TvFocusBlue,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        title = {
            Text(
                text = "Cargar Lista M3U",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tab Selector (Subir Archivo vs Enlace URL)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111118))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tab 0: Subir Archivo
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (tabFileFocused) TvFocusBlue else if (selectedTab == 0) Color(0xFFE50914) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            if (tabFileFocused) 2.dp else 0.dp,
                            if (tabFileFocused) Color(0xFFFFC107) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .onFocusChanged { tabFileFocused = it.isFocused }
                            .focusable()
                            .clickable { selectedTab = 0 }
                            .testTag("m3u_tab_file")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Subir Archivo",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0 || tabFileFocused) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Tab 1: Enlace URL
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (tabUrlFocused) TvFocusBlue else if (selectedTab == 1) Color(0xFFE50914) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            if (tabUrlFocused) 2.dp else 0.dp,
                            if (tabUrlFocused) Color(0xFFFFC107) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .onFocusChanged { tabUrlFocused = it.isFocused }
                            .focusable()
                            .clickable { selectedTab = 1 }
                            .testTag("m3u_tab_url")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enlace URL",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1 || tabUrlFocused) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    // --- TAB 0: SUBIR ARCHIVO M3U ---
                    Text(
                        text = "Selecciona un archivo .m3u o .m3u8 desde tu dispositivo o almacenamiento:",
                        fontSize = 12.sp,
                        color = Color(0xFFB0B0B0)
                    )

                    // Button to launch file picker
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectFileBtnFocused) TvFocusBlue else Color(0xFF222230),
                        border = androidx.compose.foundation.BorderStroke(
                            if (selectFileBtnFocused) 2.dp else 1.dp,
                            if (selectFileBtnFocused) Color(0xFFFFC107) else Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .onFocusChanged { selectFileBtnFocused = it.isFocused }
                            .focusable()
                            .clickable {
                                filePickerLauncher.launch("*/*")
                            }
                            .testTag("m3u_select_file_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = "Elegir archivo",
                                tint = if (selectFileBtnFocused) Color.White else Color(0xFFFFC107),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedFileName != null) "Cambiar archivo seleccionado" else "Examinar y elegir archivo M3U",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Display selected file info or error
                    if (fileErrorMessage != null) {
                        Text(
                            text = fileErrorMessage ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp
                        )
                    } else if (selectedFileName != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF142E1F),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = selectedFileName ?: "",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (parsedChannelCount != null) {
                                        Text(
                                            text = "$parsedChannelCount canales detectados",
                                            color = Color(0xFF81C784),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Custom name for the list
                        Text(
                            text = "Nombre para la lista (opcional):",
                            fontSize = 12.sp,
                            color = Color(0xFF9090A0)
                        )

                        TextField(
                            value = customFilePlaylistName,
                            onValueChange = { customFilePlaylistName = it },
                            placeholder = {
                                Text("Mi Lista M3U", color = Color(0xFF707080), fontSize = 13.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedIndicatorColor = TvFocusBlue,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { fileNameInputFocused = it.isFocused }
                                .border(
                                    width = if (fileNameInputFocused) 2.dp else 1.dp,
                                    color = if (fileNameInputFocused) TvFocusBlue else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .testTag("m3u_file_input_name")
                        )
                    }
                } else {
                    // --- TAB 1: ENLACE URL M3U ---
                    Text(
                        text = "Ingresa el enlace URL de tu lista de canales M3U:",
                        fontSize = 12.sp,
                        color = Color(0xFFB0B0B0)
                    )

                    // M3U URL Input
                    TextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        placeholder = {
                            Text("https://ejemplo.com/lista.m3u", color = Color(0xFF707080), fontSize = 13.sp)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = TvFocusBlue,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { urlInputFocused = it.isFocused }
                            .border(
                                width = if (urlInputFocused) 2.dp else 1.dp,
                                color = if (urlInputFocused) TvFocusBlue else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .testTag("m3u_input_url")
                    )

                    // Playlist Name (Optional)
                    Text(
                        text = "Nombre para la lista (opcional):",
                        fontSize = 12.sp,
                        color = Color(0xFF9090A0)
                    )

                    TextField(
                        value = urlPlaylistName,
                        onValueChange = { urlPlaylistName = it },
                        placeholder = {
                            Text("Mi Lista M3U", color = Color(0xFF707080), fontSize = 13.sp)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = TvFocusBlue,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { urlNameInputFocused = it.isFocused }
                            .border(
                                width = if (urlNameInputFocused) 2.dp else 1.dp,
                                color = if (urlNameInputFocused) TvFocusBlue else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .testTag("m3u_input_name")
                    )
                }
            }
        },
        confirmButton = {
            val canConfirm = if (selectedTab == 0) {
                !selectedFileContent.isNullOrBlank()
            } else {
                m3uUrl.isNotBlank()
            }

            Button(
                onClick = {
                    if (selectedTab == 0) {
                        val content = selectedFileContent
                        if (!content.isNullOrBlank()) {
                            val name = customFilePlaylistName.ifBlank { selectedFileName ?: "Archivo M3U" }
                            onLoadFile(name, content)
                        }
                    } else {
                        if (m3uUrl.isNotBlank()) {
                            onLoadUrl(m3uUrl, urlPlaylistName)
                        }
                    }
                },
                enabled = !loading && canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (confirmFocused) TvFocusBlue else TvSelectedRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .onFocusChanged { confirmFocused = it.isFocused }
                    .border(
                        width = if (confirmFocused) 2.dp else 0.dp,
                        color = if (confirmFocused) Color(0xFFFFC107) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .testTag("m3u_confirm_btn")
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (selectedTab == 0) "Cargar Archivo" else "Cargar Lista",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .onFocusChanged { cancelFocused = it.isFocused }
                    .border(
                        width = if (cancelFocused) 2.dp else 1.dp,
                        color = if (cancelFocused) TvFocusBlue else Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .testTag("m3u_cancel_btn")
            ) {
                Text("Cancelar")
            }
        }
    )
}

