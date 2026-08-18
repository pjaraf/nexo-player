package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private val LOGIN_POSTERS_COL_1 = listOf(
    "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg", // Deadpool & Wolverine
    "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", // Dune Part 2
    "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", // Inside Out 2
    "https://image.tmdb.org/t/p/w500/cxevDYdeFUrqaR2aAPMxn4Cx8YJ.jpg", // Godzilla x Kong
    "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"  // Oppenheimer
)

private val LOGIN_POSTERS_COL_2 = listOf(
    "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg", // Avengers Endgame
    "https://image.tmdb.org/t/p/w500/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg", // Avengers Infinity War
    "https://image.tmdb.org/t/p/w500/A4j8S6mo02Feo8PfOTDpEcyFmng.jpg", // The Batman
    "https://image.tmdb.org/t/p/w500/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg", // Barbie
    "https://image.tmdb.org/t/p/w500/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg"  // Kung Fu Panda 4
)

private val LOGIN_POSTERS_COL_3 = listOf(
    "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", // Avatar 2
    "https://image.tmdb.org/t/p/w500/gKkl37BQuKTanygYQG1pyYgLVgf.jpg", // Planet of the Apes
    "https://image.tmdb.org/t/p/w500/NNxYkU70HPurnNCSiCjYAmacwm.jpg", // Mission Impossible
    "https://image.tmdb.org/t/p/w500/sh7Rg8Er3tFcN9BpKIPOMvALgZd.jpg", // Civil War
    "https://image.tmdb.org/t/p/w500/1E5baAaEse26fej7uHcjOgEE2t2.jpg"  // Fast X
)

private val LOGIN_POSTERS_COL_4 = listOf(
    "https://image.tmdb.org/t/p/w500/d5NXSklXo0qyIYkgV94XAgMIckC.jpg", // Dune
    "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg", // Fight Club
    "https://image.tmdb.org/t/p/w500/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg", // Shawshank
    "https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", // Godfather
    "https://image.tmdb.org/t/p/w500/fiVW06jE7z9YnO4trhaMEdclSiC.jpg"  // Fast 9
)

private val LOGIN_POSTERS_COL_5 = listOf(
    "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg",
    "https://image.tmdb.org/t/p/w500/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg",
    "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
    "https://image.tmdb.org/t/p/w500/cxevDYdeFUrqaR2aAPMxn4Cx8YJ.jpg",
    "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg"
)

@Composable
fun NetflixPosterWallBackground(
    modifier: Modifier = Modifier,
    alpha: Float = 0.55f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Tilted Perspective Poster Grid Wall
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
                .alpha(alpha)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PosterColumnWall(posters = LOGIN_POSTERS_COL_1, modifier = Modifier.weight(1f).offset(y = (-40).dp))
                PosterColumnWall(posters = LOGIN_POSTERS_COL_2, modifier = Modifier.weight(1f).offset(y = 30.dp))
                PosterColumnWall(posters = LOGIN_POSTERS_COL_3, modifier = Modifier.weight(1f).offset(y = (-50).dp))
                PosterColumnWall(posters = LOGIN_POSTERS_COL_4, modifier = Modifier.weight(1f).offset(y = 20.dp))
                PosterColumnWall(posters = LOGIN_POSTERS_COL_5, modifier = Modifier.weight(1f).offset(y = (-30).dp))
            }
        }

        // 2. Cinematic Ambient Glow (Netflix Crimson Red Glow & Dark Vignette)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x33E50914),
                            Color(0x77000000),
                            Color(0xEE000000)
                        ),
                        radius = 1200f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.70f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun PosterColumnWall(
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
