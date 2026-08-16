package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Broadcast TV Lower-Third "Breaking News" Style Channel Banner
 * Exact design replica:
 * - Slanted Red/Coral "TV LOGO" trapezoid badge on the left
 * - Slanted "LIVE ● [TIME]" pill with blinking indicator and red underline accent
 * - Dark glossy main bar with top channel title & subtitle category info
 * - Slanted right cut with vibrant red angled accent blade
 */
@Composable
fun BreakingNewsTvBanner(
    channelName: String,
    channelNumber: String,
    categoryName: String,
    channelLogoUrl: String? = null,
    directionLabel: String? = null,
    totalChannels: Int = 0,
    currentIndex: Int = 1,
    modifier: Modifier = Modifier
) {
    // Current live time clock formatted HH:mm
    val currentTime = remember {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date())
    }

    // Blinking pulsing animation for LIVE white dot
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Column(
        modifier = modifier
            .widthIn(min = 320.dp, max = 560.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(8.dp))
    ) {
        // Main Upper Row: Left Red TV Badge + Main Dark Bar
        Row(
            modifier = Modifier.height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. LEFT RED TV TRAPEZOID BADGE (Angled right edge)
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val skew = 14.dp.toPx()
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width - skew, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        // Red gradient background
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF2B4E),
                                    Color(0xFFE50914),
                                    Color(0xFFC70613)
                                )
                            )
                        )
                        // Top light bevel highlight
                        val topHighlight = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width - 2f, 2.5f)
                            lineTo(0f, 2.5f)
                            close()
                        }
                        drawPath(topHighlight, color = Color.White.copy(alpha = 0.35f))
                    }
                    .padding(start = 12.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!channelLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channelLogoUrl,
                            contentDescription = "Canal Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(46.dp)
                                .height(26.dp)
                        )
                        Text(
                            text = "CH $channelNumber",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    } else {
                        Text(
                            text = "TV",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CANAL $channelNumber",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // 2. MAIN DARK HORIZONTAL LOWER-THIRD BAR (With right slanted cut & red blade)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .offset(x = (-8).dp) // Seamlessly join with left badge
                    .drawBehind {
                        val skew = 18.dp.toPx()
                        val bladeWidth = 14.dp.toPx()
                        val mainWidth = size.width - bladeWidth

                        // Dark slate glossy background
                        val darkPath = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(mainWidth, 0f)
                            lineTo(mainWidth - skew, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            path = darkPath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2E303A),
                                    Color(0xFF202128),
                                    Color(0xFF141518)
                                )
                            )
                        )

                        // Top bevel highlight on dark bar
                        val topHighlight = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(mainWidth, 0f)
                            lineTo(mainWidth - 2f, 2f)
                            lineTo(0f, 2f)
                            close()
                        }
                        drawPath(topHighlight, color = Color(0xFF6B7082))

                        // Gloss reflection overlay across the lower half
                        val glossPath = Path().apply {
                            moveTo(0f, size.height * 0.48f)
                            lineTo(mainWidth - (skew * 0.48f), size.height * 0.48f)
                            lineTo(mainWidth - skew, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            path = glossPath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.05f),
                                    Color.White.copy(alpha = 0.01f)
                                )
                            )
                        )

                        // Slanted Red Accent Blade on the far right
                        val bladePath = Path().apply {
                            moveTo(mainWidth + 4.dp.toPx(), 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width - skew, size.height)
                            lineTo(mainWidth - skew + 4.dp.toPx(), size.height)
                            close()
                        }
                        drawPath(
                            path = bladePath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF2B4E),
                                    Color(0xFFE50914)
                                )
                            )
                        )
                    }
                    .padding(start = 14.dp, end = 32.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Upper Big Bold Title (Channel Name in Breaking News style)
                    Text(
                        text = channelName.uppercase(),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Lower Subtitle Line (Category / Stream details)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = categoryName.uppercase(),
                            color = Color(0xFFD4D7E2),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (totalChannels > 0) {
                            Text(
                                text = "•  $currentIndex/$totalChannels",
                                color = Color(0xFFA6ABB8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!directionLabel.isNullOrBlank()) {
                            Text(
                                text = "•  $directionLabel",
                                color = Color(0xFFFF4B6E),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Sub Row Below: Slanted LIVE & Time Pill + Underline Accent
        Row(
            modifier = Modifier
                .padding(start = 6.dp, top = 2.dp)
                .height(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LIVE pill + Time box with slant
            Row(
                modifier = Modifier
                    .drawBehind {
                        val skew = 8.dp.toPx()
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width - skew, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, color = Color.Transparent)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Red "LIVE ●" pill
                Surface(
                    shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp),
                    color = Color(0xFFFF2B4E),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = dotAlpha))
                        )
                    }
                }

                // Right White Time Box
                Surface(
                    shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp),
                    color = Color(0xFFF2F4F8),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentTime,
                            color = Color(0xFF15161A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Red Underline accent stripe running forward
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(2.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF2B4E),
                                Color(0xFFFF2B4E).copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
