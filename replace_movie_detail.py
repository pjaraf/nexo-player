import re

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    movie_code = f.read()

with open('movie_tv_draft.kt', 'r') as f:
    movie_tv_code = f.read()

new_movie_code = '''package com.example.ui.screens

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.api.XtreamApi
import com.example.data.models.VodDetailResponse
import com.example.data.models.VodStream
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.POSTER_FALLBACK
import com.example.ui.theme.*
import com.example.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun MovieDetailScreen(
    movieId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateMovie: (movieId: String) -> Unit = {},
    onPlay: (url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = remember { com.example.utils.DeviceUtils.isTelevision(context) }
    val isTvOrLandscape = configuration.screenWidthDp >= 600 || isTv

    if (isTvOrLandscape) {
        MovieDetailTvScreen(
            movieId = movieId,
            viewModel = viewModel,
            onBack = onBack,
            onNavigateMovie = onNavigateMovie,
            onPlay = onPlay
        )
    } else {
        MovieDetailPhoneScreen(
            movieId = movieId,
            viewModel = viewModel,
            onBack = onBack,
            onNavigateMovie = onNavigateMovie,
            onPlay = onPlay
        )
    }
}
'''

# Get MovieDetailPhoneScreen from the original movie code
# The original MovieDetailScreen starts at `@Composable\nfun MovieDetailScreen` and ends at the end of the file.
movie_phone = re.search(r'(@Composable\nfun MovieDetailScreen\([\s\S]*)$', movie_code).group(1)
movie_phone = movie_phone.replace('fun MovieDetailScreen(', 'private fun MovieDetailPhoneScreen(')
movie_phone = movie_phone.replace('testTag("movie_detail_screen")', 'testTag("movie_detail_phone_screen")')

new_movie_code += movie_phone
new_movie_code += '\n' + movie_tv_code

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'w') as f:
    f.write(new_movie_code)

