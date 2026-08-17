import re

with open('series_tv.kt', 'r') as f:
    code = f.read()

# Replace function signature
code = re.sub(
    r'private fun SeriesDetailTvScreen\([\s\S]*?\)\s*\{',
    '''private fun MovieDetailTvScreen(
    movieId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateMovie: (movieId: String) -> Unit,
    onPlay: (url: String, title: String, kind: String, contentId: String, image: String, resumeMs: Long) -> Unit
) {''',
    code,
    count=1
)

# Replace local variables
code = re.sub(r'var info by remember \{ mutableStateOf<SeriesDetailInfo\?.*?\}', 'var detail by remember { mutableStateOf<com.example.data.models.VodDetailResponse?>(null) }', code)
code = re.sub(r'var episodesMap by remember .*?\}', 'var latestMovies by remember { mutableStateOf<List<com.example.data.models.VodStream>>(emptyList()) }', code)
code = re.sub(r'var selectedSeason by remember .*?\}', '', code)
code = re.sub(r'var selectedEpisode by remember .*?\}', '', code)
code = re.sub(r'var showSeasonPicker by remember .*?\}', '', code)

# Replace launch effect
effect_search = r'LaunchedEffect\(seriesId\) \{[\s\S]*?loading = false\n    \}'
effect_replace = '''LaunchedEffect(movieId) {
        loading = true
        detail = com.example.data.api.XtreamApi.getVodDetail(movieId)
        try {
            val allMovies = com.example.data.api.XtreamApi.getVodStreams()
            latestMovies = allMovies.filter { it.id != movieId }.take(25)
        } catch (e: Exception) {
            latestMovies = emptyList()
        }
        loading = false
    }'''
code = re.sub(effect_search, effect_replace, code)

code = re.sub(r'val currentEpisodes = remember[\s\S]*?\}', '', code)

code = re.sub(r'// Ensure selectedEpisode is valid[\s\S]*?\}\n    \}', '', code)

player_effect_search = r'// Update preview player media item when selected episode changes\n    LaunchedEffect\(selectedEpisode\) \{[\s\S]*?\} catch \(e: Exception\) \{[\s\S]*?\}\n        \}\n    \}'
player_effect_replace = '''LaunchedEffect(detail) {
        detail?.movieData?.let { data ->
            try {
                isPreviewLoading = true
                previewError = false
                val ext = data.containerExtension ?: "mp4"
                val streamUrl = com.example.data.api.XtreamApi.getVodStreamUrl(movieId, ext)
                if (streamUrl.isNotBlank()) {
                    exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(streamUrl)))
                    exoPlayer.prepare()
                    exoPlayer.play()
                    isPlaying = true
                }
            } catch (e: Exception) {
                android.util.Log.e("MovieDetailTv", "Error loading movie stream", e)
                isPreviewLoading = false
                previewError = true
            }
        }
    }'''
code = re.sub(player_effect_search, player_effect_replace, code)

# Replace auto-advance logic
auto_advance_search = r'// Automatically play next episode[\s\S]*?\}'
code = re.sub(auto_advance_search, '', code)

# Properties
props_search = r'val seriesTitle = info\?\.name \?: "Serie"\n    val seriesCover = info\?\.cover \?: info\?\.backdropPath\?\.firstOrNull\(\) \?: POSTER_FALLBACK\n    val releaseYear = info\?\.releaseDate\?\.take\(4\) \?: ""\n    val rating = info\?\.rating \?: 0\.0\n    val cleanRatingNumber = if \(rating > 0\) String\.format\("%.1f", rating\) else "Nuevo"\n    val plotText = info\?\.plot \?: info\?\.description \?: "No hay sinopsis disponible\."\n    val castText = info\?\.cast \?: "No disponible"'
props_replace = '''val info = detail?.info
    val movieData = detail?.movieData
    val seriesTitle = info?.name ?: movieData?.name ?: "Película"
    val seriesCover = info?.coverImage ?: movieData?.streamIcon ?: POSTER_FALLBACK
    val releaseYear = info?.releaseDate?.take(4) ?: ""
    val rating = info?.rating ?: 0.0
    val cleanRatingNumber = if (rating.toString().isNotBlank() && rating.toString() != "0" && rating.toString() != "0.0") String.format("%.1f", rating.toString().toDoubleOrNull() ?: 0.0) else "Nuevo"
    val plotText = info?.plot ?: info?.description ?: "No hay sinopsis disponible."
    val castText = info?.cast ?: "No disponible"
    val ext = movieData?.containerExtension ?: "mp4"
    val progressList by viewModel.progressList.collectAsState()
    val savedProgress = remember(progressList, movieId) {
        progressList.find { it.kind == "movie" && it.id == movieId }
    }
'''
code = re.sub(props_search, props_replace, code)

code = code.replace('testTag("series_detail_tv_screen")', 'testTag("movie_detail_tv_screen")')

# Replace tag
code = re.sub(r'val epNumber = selectedEpisode\?\.epNumber \?= 1\n                            Text\(\n                                text = "T\$selectedSeason - E\$epNumber",\n                                color = Color\(0xFFFF9800\),\n                                fontSize = 14\.sp,\n                                fontWeight = FontWeight\.Black,\n                                letterSpacing = 0\.5\.sp\n                            \)\n                            Spacer\(modifier = Modifier\.height\(6\.dp\)\)', '', code)

code = re.sub(r'val epNumber = selectedEpisode\?\.epNumber \?: 1\n                            Text\(\n                                text = "T\$selectedSeason - E\$epNumber",\n                                color = Color\(0xFFFF9800\),\n                                fontSize = 14\.sp,\n                                fontWeight = FontWeight\.Black,\n                                letterSpacing = 0\.5\.sp\n                            \)\n                            Spacer\(modifier = Modifier\.height\(6\.dp\)\)', '', code)


# Full screen buttons
btn_play_search = r'onPlayEpisode\([\s\S]*?\)\s*\}'
btn_play_replace = '''{
                                        val streamUrl = com.example.data.api.XtreamApi.getVodStreamUrl(movieId, ext)
                                        val resumeMs = savedProgress?.positionMs ?: 0L
                                        onPlay(streamUrl, seriesTitle, "movie", movieId, seriesCover, resumeMs)
                                    }'''
code = re.sub(btn_play_search, btn_play_replace, code)


# Bottom Row (Carousel)
bottom_row_search = r'// Bottom Row: Seasons & Episodes \[\+\] \(Only when not in full screen\)[\s\S]*?if \(!isFullScreenMode\) \{[\s\S]*?\}'
bottom_row_replace = '''if (!isFullScreenMode && latestMovies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Películas más nuevas",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp, end = 28.dp)
                ) {
                    items(latestMovies, key = { it.id }) { item ->
                        com.example.ui.components.MediaPosterCard(
                            title = item.displayName,
                            imageUrl = item.streamIcon,
                            rating = item.formattedRating,
                            badgeText = "PELÍCULA",
                            onClick = { onNavigateMovie(item.id) }
                        )
                    }
                }
            }
        }'''

# Since bottom row search is complex due to nested braces, it's easier to find it manually by string index.

with open('movie_tv_draft.kt', 'w') as f:
    f.write(code)

