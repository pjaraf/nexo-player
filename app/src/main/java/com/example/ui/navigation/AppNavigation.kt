package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.api.XtreamApi
import com.example.data.storage.AppStorage
import com.example.ui.components.OfflineBarrierScreen
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.*
import com.example.ui.viewmodels.MainViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val PROFILE_SELECT = "profile_select"
    const val MANAGE_PROFILES = "manage_profiles"
    const val PIN = "pin/{action}"
    const val TABS = "tabs"
    const val MOVIE_DETAIL = "movie?id={id}"
    const val SERIES_DETAIL = "series?id={id}"
    const val FAVORITES = "favorites"
    const val PLAYER = "player?url={url}&title={title}&isLive={isLive}&channelId={channelId}&categoryId={categoryId}&kind={kind}&contentId={contentId}&image={image}&resumeMs={resumeMs}&nextUrl={nextUrl}&nextTitle={nextTitle}&nextContentId={nextContentId}&nextEpImage={nextEpImage}"

    fun pin(action: String) = "pin/$action"
    fun movieDetail(id: String): String {
        val encoded = if (id.isNotBlank()) java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20") else "empty"
        return "movie?id=$encoded"
    }
    fun seriesDetail(id: String): String {
        val encoded = if (id.isNotBlank()) java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20") else "empty"
        return "series?id=$encoded"
    }

    fun player(
        url: String,
        title: String,
        isLive: Boolean,
        channelId: String? = null,
        categoryId: String? = null,
        kind: String? = null,
        contentId: String? = null,
        image: String? = null,
        resumeMs: Long = 0L,
        nextUrl: String? = null,
        nextTitle: String? = null,
        nextContentId: String? = null,
        nextEpImage: String? = null
    ): String {
        fun encode(s: String?) = s?.let { URLEncoder.encode(it, "UTF-8").replace("+", "%20") } ?: ""
        return "player?url=${encode(url)}&title=${encode(title)}&isLive=$isLive&channelId=${encode(channelId)}&categoryId=${encode(categoryId)}&kind=${encode(kind)}&contentId=${encode(contentId)}&image=${encode(image)}&resumeMs=$resumeMs&nextUrl=${encode(nextUrl)}&nextTitle=${encode(nextTitle)}&nextContentId=${encode(nextContentId)}&nextEpImage=${encode(nextEpImage)}"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val isOnline by mainViewModel.isOnline.collectAsState()
    val isLoggedIn by mainViewModel.isLoggedIn.collectAsState()
    val updateInfo by mainViewModel.updateInfo.collectAsState()
    val updateDownloadState by mainViewModel.updateDownloadState.collectAsState()

    LaunchedEffect(Unit) {
        AppStorage.setDismissedUpdateVersion(null)
        mainViewModel.checkForUpdates(manual = true)
    }

    if (updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            downloadState = updateDownloadState,
            onStartDownload = {
                mainViewModel.startUpdateDownload(context, updateInfo!!)
            },
            onInstall = { filePath ->
                mainViewModel.installDownloadedApk(context, filePath)
            },
            onDismiss = {
                mainViewModel.dismissUpdate()
            }
        )
    }

    val startDestination = Routes.SPLASH

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    val nextScreen = if (!AppStorage.isLoggedIn()) Routes.LOGIN else Routes.TABS
                    navController.navigate(nextScreen) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = mainViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.TABS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PROFILE_SELECT) {
            ProfileSelectScreen(
                viewModel = mainViewModel,
                onProfileSelected = { isKids, needsPin ->
                    if (needsPin) {
                        navController.navigate(Routes.pin("enter"))
                    } else {
                        navController.navigate(Routes.TABS) {
                            popUpTo(Routes.PROFILE_SELECT) { inclusive = true }
                        }
                    }
                },
                onManageProfiles = {
                    navController.navigate(Routes.MANAGE_PROFILES)
                }
            )
        }

        composable(Routes.MANAGE_PROFILES) {
            ManageProfilesScreen(
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigatePin = { action ->
                    navController.navigate(Routes.pin(action))
                }
            )
        }

        composable(
            route = Routes.PIN,
            arguments = listOf(navArgument("action") { type = NavType.StringType; defaultValue = "enter" })
        ) { backStackEntry ->
            val action = backStackEntry.arguments?.getString("action") ?: "enter"
            PinScreen(
                action = action,
                onSuccess = {
                    if (action == "enter") {
                        navController.navigate(Routes.TABS) {
                            popUpTo(Routes.PROFILE_SELECT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onClose = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.TABS) {
            MainTabsScreen(
                viewModel = mainViewModel,
                onNavigateLivePlayer = { channelId, catId, title ->
                    val streamUrl = XtreamApi.getLiveStreamUrl(channelId)
                    navController.navigate(
                        Routes.player(
                            url = streamUrl,
                            title = title,
                            isLive = true,
                            channelId = channelId,
                            categoryId = catId
                        )
                    )
                },
                onNavigateMovieDetail = { movieId ->
                    navController.navigate(Routes.movieDetail(movieId))
                },
                onNavigateSeriesDetail = { seriesId ->
                    navController.navigate(Routes.seriesDetail(seriesId))
                },
                onNavigatePlayerDirect = { url, title, kind, contentId, img, resumeMs ->
                    if (kind == "movie" && contentId.isNotBlank()) {
                        navController.navigate(Routes.movieDetail(contentId))
                    } else if (kind == "series" && contentId.isNotBlank()) {
                        navController.navigate(Routes.seriesDetail(contentId))
                    }
                    navController.navigate(
                        Routes.player(
                            url = url,
                            title = title,
                            isLive = false,
                            kind = kind,
                            contentId = contentId,
                            image = img,
                            resumeMs = resumeMs
                        )
                    )
                },
                onNavigateFavorites = {
                    navController.navigate(Routes.FAVORITES)
                },
                onNavigateSwitchProfile = {
                    navController.navigate(Routes.PROFILE_SELECT)
                },
                onNavigateManageProfiles = {
                    navController.navigate(Routes.MANAGE_PROFILES)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.MOVIE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "empty" })
        ) { entry ->
            val movieId = entry.arguments?.getString("id") ?: ""
            MovieDetailScreen(
                movieId = movieId,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateMovie = { newMovieId ->
                    navController.navigate(Routes.movieDetail(newMovieId))
                },
                onPlay = { url, title, kind, contentId, img, resumeMs ->
                    navController.navigate(
                        Routes.player(
                            url = url,
                            title = title,
                            isLive = false,
                            kind = kind,
                            contentId = contentId,
                            image = img,
                            resumeMs = resumeMs
                        )
                    )
                }
            )
        }

        composable(
            route = Routes.SERIES_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "empty" })
        ) { entry ->
            val seriesId = entry.arguments?.getString("id") ?: ""
            SeriesDetailScreen(
                seriesId = seriesId,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onPlayEpisode = { url, title, kind, contentId, img, resumeMs, nextUrl, nextTitle, nextContentId, nextEpImg ->
                    navController.navigate(
                        Routes.player(
                            url = url,
                            title = title,
                            isLive = false,
                            kind = kind,
                            contentId = contentId,
                            image = img,
                            resumeMs = resumeMs,
                            nextUrl = nextUrl,
                            nextTitle = nextTitle,
                            nextContentId = nextContentId,
                            nextEpImage = nextEpImg
                        )
                    )
                }
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateMovie = { movieId ->
                    navController.navigate(Routes.movieDetail(movieId))
                },
                onNavigateSeries = { seriesId ->
                    navController.navigate(Routes.seriesDetail(seriesId))
                },
                onPlayLive = { channelId, title ->
                    val streamUrl = XtreamApi.getLiveStreamUrl(channelId)
                    navController.navigate(
                        Routes.player(
                            url = streamUrl,
                            title = title,
                            isLive = true,
                            channelId = channelId,
                            categoryId = "ALL"
                        )
                    )
                }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("isLive") { type = NavType.BoolType; defaultValue = false },
                navArgument("channelId") { type = NavType.StringType; defaultValue = "" },
                navArgument("categoryId") { type = NavType.StringType; defaultValue = "" },
                navArgument("kind") { type = NavType.StringType; defaultValue = "" },
                navArgument("contentId") { type = NavType.StringType; defaultValue = "" },
                navArgument("image") { type = NavType.StringType; defaultValue = "" },
                navArgument("resumeMs") { type = NavType.LongType; defaultValue = 0L },
                navArgument("nextUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("nextTitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("nextContentId") { type = NavType.StringType; defaultValue = "" },
                navArgument("nextEpImage") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            fun decode(s: String?) = s?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) } ?: ""

            val rawUrl = decode(entry.arguments?.getString("url"))
            val title = decode(entry.arguments?.getString("title"))
            val isLive = entry.arguments?.getBoolean("isLive") ?: false
            val channelId = decode(entry.arguments?.getString("channelId")).takeIf { it.isNotBlank() }
            val categoryId = decode(entry.arguments?.getString("categoryId")).takeIf { it.isNotBlank() }
            val kind = decode(entry.arguments?.getString("kind")).takeIf { it.isNotBlank() }
            val contentId = decode(entry.arguments?.getString("contentId")).takeIf { it.isNotBlank() }
            val image = decode(entry.arguments?.getString("image")).takeIf { it.isNotBlank() }
            val resumeMs = entry.arguments?.getLong("resumeMs") ?: 0L
            val nextUrl = decode(entry.arguments?.getString("nextUrl")).takeIf { it.isNotBlank() }
            val nextTitle = decode(entry.arguments?.getString("nextTitle")).takeIf { it.isNotBlank() }
            val nextContentId = decode(entry.arguments?.getString("nextContentId")).takeIf { it.isNotBlank() }
            val nextEpImage = decode(entry.arguments?.getString("nextEpImage")).takeIf { it.isNotBlank() }

            PlayerScreen(
                initialStreamUrl = rawUrl,
                initialTitle = title,
                isLive = isLive,
                channelId = channelId,
                categoryId = categoryId,
                kind = kind,
                contentId = contentId,
                image = image,
                resumeMs = resumeMs,
                nextUrl = nextUrl,
                nextTitle = nextTitle,
                nextContentId = nextContentId,
                nextEpImage = nextEpImage,
                viewModel = mainViewModel,
                onClose = { navController.popBackStack() }
            )
        }
    }

    // Block content access and prevent loading default mock data when offline
    if (!isOnline && isLoggedIn) {
        OfflineBarrierScreen(
            isOnline = isOnline,
            onRetry = {
                mainViewModel.loadHomeContent()
            }
        )
    }
}
}
