package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.utils.DeviceUtils

val POSTERS_COL_1 = listOf(
    "https://image.tmdb.org/t/p/w300/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg",
    "https://image.tmdb.org/t/p/w300/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
    "https://image.tmdb.org/t/p/w300/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg"
)
val POSTERS_COL_2 = listOf(
    "https://image.tmdb.org/t/p/w300/or06FN3Dka5tukK1e9sl16pB3iy.jpg",
    "https://image.tmdb.org/t/p/w300/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg",
    "https://image.tmdb.org/t/p/w300/A4j8S6mo02Feo8PfOTDpEcyFmng.jpg"
)
val POSTERS_COL_3 = listOf(
    "https://image.tmdb.org/t/p/w300/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
    "https://image.tmdb.org/t/p/w300/gKkl37BQuKTanygYQG1pyYgLVgf.jpg",
    "https://image.tmdb.org/t/p/w300/NNxYkU70HPurnNCSiCjYAmacwm.jpg"
)
val POSTERS_COL_4 = listOf(
    "https://image.tmdb.org/t/p/w300/d5NXSklXo0qyIYkgV94XAgMIckC.jpg",
    "https://image.tmdb.org/t/p/w300/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
    "https://image.tmdb.org/t/p/w300/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg"
)

@Composable
fun PosterColumn(
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
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161616))
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

@Composable
fun CinematicBackground() {
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTelevision(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Render poster wall safely
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = if (isTv) -6f else -10f
                    scaleX = 1.15f
                    scaleY = 1.15f
                }
                .alpha(0.45f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PosterColumn(posters = POSTERS_COL_1, modifier = Modifier.weight(1f).offset(y = (-20).dp))
                PosterColumn(posters = POSTERS_COL_2, modifier = Modifier.weight(1f).offset(y = 20.dp))
                PosterColumn(posters = POSTERS_COL_3, modifier = Modifier.weight(1f).offset(y = (-30).dp))
                if (!isTv) {
                    PosterColumn(posters = POSTERS_COL_4, modifier = Modifier.weight(1f).offset(y = 10.dp))
                }
            }
        }

        // Ambient dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.70f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )
    }
}
