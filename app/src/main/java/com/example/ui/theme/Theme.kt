package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Colors matching Nexus theme
val NexusBackground = Color(0xFF050505)
val NexusSurface = Color(0xFF14141E)
val NexusSurfaceVariant = Color(0xFF1C1C28)
val NexusPrimary = Color(0xFFE50914)
val NexusPrimaryVariant = Color(0xFFB80710)
val NexusAccent = Color(0xFF00E5FF)
val NexusText = Color(0xFFFFFFFF)
val NexusTextSecondary = Color(0xFFA0A0B0)
val NexusTextMuted = Color(0xFF707080)
val NexusBorder = Color(0x1FFFFFFF)
val NexusCardBg = Color(0xFF161622)

// TV Remote focus and selection colors
val TvFocusBlue = Color(0xFF007AFF)      // Blue focus on remote hover
val TvSelectedRed = Color(0xFFE50914)    // Red on active / selection
val TvFocusGold = Color(0xFFFFD700)      // Bright Gold focus highlight
val TvFocusCyan = Color(0xFF00E5FF)      // Cyan accent highlight
val TvFocusGlow = Color(0xFF38BDF8)      // Sky blue glow

private val DarkColorScheme = darkColorScheme(
    primary = NexusPrimary,
    onPrimary = Color.White,
    primaryContainer = NexusPrimaryVariant,
    onPrimaryContainer = Color.White,
    secondary = NexusAccent,
    onSecondary = Color.Black,
    background = NexusBackground,
    onBackground = NexusText,
    surface = NexusSurface,
    onSurface = NexusText,
    surfaceVariant = NexusSurfaceVariant,
    onSurfaceVariant = NexusTextSecondary,
    outline = NexusBorder
)

val NexusTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.5.sp,
        color = Color.White
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.25.sp,
        color = Color.White
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = Color.White
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = Color.White
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = NexusTextSecondary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = NexusTextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        color = Color.White
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp,
        color = NexusTextSecondary
    )
)

val NexusShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NexusTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NexusBackground.toArgb()
                window.navigationBarColor = NexusBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = NexusTypography,
        shapes = NexusShapes,
        content = content
    )
}
