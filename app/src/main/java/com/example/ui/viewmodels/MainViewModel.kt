package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NexusApp
import com.example.data.api.XtreamApi
import com.example.data.models.*
import com.example.data.storage.AppStorage
import com.example.data.updater.AppUpdateManager
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class MainViewModel(application: Application = NexusApp.instance) : AndroidViewModel(application) {

    // --- Network Connectivity Flow ---
    val isOnline: StateFlow<Boolean> = NetworkMonitor.observeNetworkState(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, NetworkMonitor.isOnline(application))

    // --- Auth & Session ---
    private val _isLoggedIn = MutableStateFlow(AppStorage.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userInfo = MutableStateFlow(AppStorage.getUserInfo())
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

    // --- Active Profile & Kids Mode ---
    val activeProfile: StateFlow<Profile?> = AppStorage.activeProfileFlow

    val isKidsMode: StateFlow<Boolean> = activeProfile.map { it?.isKids == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --- Home Screen ---
    private val _homeHeroItem = MutableStateFlow<VodStream?>(null)
    val homeHeroItem: StateFlow<VodStream?> = _homeHeroItem.asStateFlow()

    private val _homeLiveRow = MutableStateFlow<List<LiveChannel>>(emptyList())
    val homeLiveRow: StateFlow<List<LiveChannel>> = _homeLiveRow.asStateFlow()

    private val _homeMoviesRow = MutableStateFlow<List<VodStream>>(emptyList())
    val homeMoviesRow: StateFlow<List<VodStream>> = _homeMoviesRow.asStateFlow()

    private val _homeSeriesRow = MutableStateFlow<List<SeriesItem>>(emptyList())
    val homeSeriesRow: StateFlow<List<SeriesItem>> = _homeSeriesRow.asStateFlow()

    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    // --- Live Screen ---
    private val _liveCategories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val liveCategories: StateFlow<List<LiveCategory>> = _liveCategories.asStateFlow()

    private val _selectedLiveCat = MutableStateFlow<String>("ALL")
    val selectedLiveCat: StateFlow<String> = _selectedLiveCat.asStateFlow()

    private val _liveChannels = MutableStateFlow<List<LiveChannel>>(emptyList())
    val liveChannels: StateFlow<List<LiveChannel>> = _liveChannels.asStateFlow()

    private val _liveSearch = MutableStateFlow("")
    val liveSearch: StateFlow<String> = _liveSearch.asStateFlow()

    private val _liveLoading = MutableStateFlow(false)
    val liveLoading: StateFlow<Boolean> = _liveLoading.asStateFlow()

    // --- Movies Screen ---
    private val _vodCategories = MutableStateFlow<List<VodCategory>>(emptyList())
    val vodCategories: StateFlow<List<VodCategory>> = _vodCategories.asStateFlow()

    private val _selectedVodCat = MutableStateFlow<String>("ALL")
    val selectedVodCat: StateFlow<String> = _selectedVodCat.asStateFlow()

    private val _vodStreams = MutableStateFlow<List<VodStream>>(emptyList())
    val vodStreams: StateFlow<List<VodStream>> = _vodStreams.asStateFlow()

    private val _vodSearch = MutableStateFlow("")
    val vodSearch: StateFlow<String> = _vodSearch.asStateFlow()

    private val _vodLoading = MutableStateFlow(false)
    val vodLoading: StateFlow<Boolean> = _vodLoading.asStateFlow()

    // --- Series Screen ---
    private val _seriesCategories = MutableStateFlow<List<SeriesCategory>>(emptyList())
    val seriesCategories: StateFlow<List<SeriesCategory>> = _seriesCategories.asStateFlow()

    private val _selectedSeriesCat = MutableStateFlow<String>("ALL")
    val selectedSeriesCat: StateFlow<String> = _selectedSeriesCat.asStateFlow()

    private val _seriesList = MutableStateFlow<List<SeriesItem>>(emptyList())
    val seriesList: StateFlow<List<SeriesItem>> = _seriesList.asStateFlow()

    private val _seriesSearch = MutableStateFlow("")
    val seriesSearch: StateFlow<String> = _seriesSearch.asStateFlow()

    private val _seriesLoading = MutableStateFlow(false)
    val seriesLoading: StateFlow<Boolean> = _seriesLoading.asStateFlow()

    // --- Continue Watching & Favorites Flows ---
    private val _progressList = MutableStateFlow<List<ProgressItem>>(emptyList())
    val progressList: StateFlow<List<ProgressItem>> = _progressList.asStateFlow()

    private val _favoritesList = MutableStateFlow<List<FavItem>>(emptyList())
    val favoritesList: StateFlow<List<FavItem>> = _favoritesList.asStateFlow()

    // --- In-App Updates ---
    val updateInfo: StateFlow<UpdateInfo?> = AppUpdateManager.latestUpdateInfo
    val updateDownloadState: StateFlow<UpdateDownloadState> = AppUpdateManager.downloadState
    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()
    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    init {
        loadSessionData()
        AppStorage.setDismissedUpdateVersion(null)
        checkForUpdates(manual = true)

        // React when device comes back online
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online && _isLoggedIn.value) {
                    loadHomeContent()
                }
            }
        }
    }

    fun checkForUpdates(manual: Boolean = false, customUrl: String? = null) {
        viewModelScope.launch {
            if (!isOnline.value) {
                if (manual) _updateStatusMessage.value = "Sin conexión a internet"
                return@launch
            }
            if (manual) {
                AppStorage.setDismissedUpdateVersion(null)
            }
            _isCheckingUpdates.value = true
            if (manual) _updateStatusMessage.value = "Buscando actualizaciones..."
            val result = AppUpdateManager.checkForUpdates(customUrl, force = manual)
            _isCheckingUpdates.value = false
            if (result != null) {
                if (manual) {
                    _updateStatusMessage.value = "¡Nueva versión encontrada: v${result.versionName}!"
                }
                // If auto-download is enabled, immediately start downloading the APK in the background
                if (AppStorage.isAutoDownloadUpdatesEnabled()) {
                    val app = getApplication<Application>()
                    startUpdateDownload(app, result, autoInstall = false)
                }
            } else {
                if (manual) {
                    _updateStatusMessage.value = "Tu aplicación ya está actualizada a la última versión."
                }
            }
        }
    }

    fun startUpdateDownload(context: android.content.Context, update: UpdateInfo, autoInstall: Boolean = false) {
        viewModelScope.launch {
            AppUpdateManager.downloadUpdate(context, update, autoInstall = autoInstall)
        }
    }

    fun installDownloadedApk(context: android.content.Context, filePath: String) {
        AppUpdateManager.installApk(context, filePath)
    }

    fun dismissUpdate() {
        AppUpdateManager.dismissUpdate()
        _updateStatusMessage.value = null
    }

    fun simulateUpdate() {
        val mockUpdate = UpdateInfo(
            versionCode = 36,
            versionName = "1.1.25",
            apkUrl = "https://github.com/pjaraf/nexo-player/releases/download/v1.1.25/app-debug.apk",
            changelog = "Versión de prueba simulada para comprobar el cuadro de diálogo de actualización automática en dispositivos y TV.",
            isMandatory = false
        )
        AppUpdateManager.setUpdateInfo(mockUpdate)
    }

    fun clearUpdateStatusMessage() {
        _updateStatusMessage.value = null
    }

    fun loadSessionData() {
        _isLoggedIn.value = AppStorage.isLoggedIn()
        _userInfo.value = AppStorage.getUserInfo()
        val p = AppStorage.getActiveProfile()
        if (p != null) {
            refreshUserData(p.id)
        }
    }

    fun refreshUserData(profileId: String = AppStorage.getActiveProfileId()) {
        _progressList.value = AppStorage.getProgressList(profileId)
        _favoritesList.value = AppStorage.getAllFavorites(profileId)
    }

    // --- Login / Logout ---
    fun login(user: String, pass: String, customServerUrl: String? = null, onSuccess: () -> Unit) {
        val trimmedUser = user.trim()
        val trimmedPass = pass.trim()

        if (trimmedUser.isBlank() || trimmedPass.isBlank()) {
            _loginError.value = "Por favor ingresa usuario y contraseña"
            return
        }

        if (!isOnline.value) {
            _loginError.value = "Sin conexión a internet. Conéctate a una red para iniciar sesión."
            return
        }

        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null
            try {
                // Clear any leftover previous session data
                XtreamApi.clearCache()
                _liveChannels.value = emptyList()
                _liveCategories.value = emptyList()
                _vodStreams.value = emptyList()
                _vodCategories.value = emptyList()
                _seriesList.value = emptyList()
                _seriesCategories.value = emptyList()
                _homeLiveRow.value = emptyList()
                _homeMoviesRow.value = emptyList()
                _homeSeriesRow.value = emptyList()
                _homeHeroItem.value = null

                // Determine candidate servers to automatically detect
                val candidateServers: List<String> = if (!customServerUrl.isNullOrBlank()) {
                    listOf(customServerUrl.trim().trimEnd('/'))
                } else {
                    AppStorage.AUTO_DETECT_SERVERS
                }

                var authenticatedResult: Pair<String, UserInfo>? = null

                for (server in candidateServers) {
                    try {
                        AppStorage.setServerUrl(server)
                        XtreamApi.clearCache()
                        val res = XtreamApi.login(trimmedUser, trimmedPass)
                        if (res != null && res.userInfo != null) {
                            authenticatedResult = Pair(server, res.userInfo)
                            break
                        }
                    } catch (_: Throwable) {
                        // Continue checking other servers
                    }
                }

                if (authenticatedResult != null) {
                    val (detectedServer, userInfo) = authenticatedResult
                    AppStorage.setServerUrl(detectedServer)
                    AppStorage.saveSession(trimmedUser, trimmedPass, userInfo, detectedServer)
                    _isLoggedIn.value = true
                    _userInfo.value = userInfo
                    _loginLoading.value = false
                    loadHomeContent()
                    onSuccess()
                } else {
                    _loginLoading.value = false
                    _loginError.value = "Usuario o contraseña incorrectos. Verifica tus credenciales."
                }
            } catch (e: Exception) {
                _loginLoading.value = false
                _loginError.value = "Error al conectar con el servidor. Verifica tu conexión a internet e inténtalo de nuevo."
            }
        }
    }

    fun loadM3uPlaylist(url: String, playlistName: String = "Mi Lista M3U", onSuccess: () -> Unit) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true))) {
            _loginError.value = "Por favor ingresa un enlace URL válido que empiece con http:// o https://"
            return
        }

        if (!isOnline.value) {
            _loginError.value = "Sin conexión a internet para descargar la lista M3U."
            return
        }

        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null
            try {
                XtreamApi.clearCache()
                _liveChannels.value = emptyList()
                _liveCategories.value = emptyList()
                _vodStreams.value = emptyList()
                _vodCategories.value = emptyList()
                _seriesList.value = emptyList()
                _seriesCategories.value = emptyList()
                _homeLiveRow.value = emptyList()
                _homeMoviesRow.value = emptyList()
                _homeSeriesRow.value = emptyList()
                _homeHeroItem.value = null

                val content = XtreamApi.loadM3uContent(trimmedUrl)
                if (content.isNullOrBlank()) {
                    _loginLoading.value = false
                    _loginError.value = "No se pudo descargar la lista M3U desde el enlace ingresado."
                    return@launch
                }

                val (channels, categories) = XtreamApi.parseM3uText(content)
                if (channels.isEmpty()) {
                    _loginLoading.value = false
                    _loginError.value = "No se encontraron canales válidos en el archivo M3U."
                    return@launch
                }

                val name = playlistName.trim().ifBlank { "Mi Lista M3U" }
                AppStorage.saveM3uSession(trimmedUrl, name)
                _isLoggedIn.value = true
                _userInfo.value = AppStorage.getUserInfo()
                _liveChannels.value = channels
                _liveCategories.value = categories
                _homeLiveRow.value = channels.take(15)
                _loginLoading.value = false
                loadHomeContent()
                onSuccess()
            } catch (e: Exception) {
                _loginLoading.value = false
                _loginError.value = "Error al procesar la lista M3U: ${e.localizedMessage ?: "Error desconocido"}"
            }
        }
    }

    fun logout() {
        XtreamApi.clearCache()
        AppStorage.clearSession()
        _isLoggedIn.value = false
        _userInfo.value = null
        _liveChannels.value = emptyList()
        _liveCategories.value = emptyList()
        _vodStreams.value = emptyList()
        _vodCategories.value = emptyList()
        _seriesList.value = emptyList()
        _seriesCategories.value = emptyList()
        _homeLiveRow.value = emptyList()
        _homeMoviesRow.value = emptyList()
        _homeSeriesRow.value = emptyList()
        _homeHeroItem.value = null
    }

    // --- Profile Management ---
    fun selectProfile(p: Profile) {
        AppStorage.setActiveProfileId(p.id)
        refreshUserData(p.id)
        loadHomeContent()
    }

    fun addProfile(name: String, color: String, isKids: Boolean): Profile {
        val p = AppStorage.addProfile(name, color, isKids)
        return p
    }

    fun updateProfile(id: String, name: String, color: String, isKids: Boolean) {
        AppStorage.updateProfile(id, name, color, isKids)
    }

    fun deleteProfile(id: String) {
        AppStorage.deleteProfile(id)
    }

    // --- Home Content Loading ---
    fun loadHomeContent() {
        viewModelScope.launch {
            _homeLoading.value = true
            val isKids = isKidsMode.value

            val liveJob = launch {
                val liveList = XtreamApi.getLiveChannels()
                _homeLiveRow.value = if (isKids) {
                    liveList.filter { AppStorage.isKidsCategory(it.groupName) || AppStorage.isKidsCategory(it.name) }
                } else {
                    liveList.take(20)
                }
            }

            val moviesJob = launch {
                val vodList = XtreamApi.getVodStreams()
                val finalMovies = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    val filtered = if (isKids) {
                        vodList.filter { AppStorage.isKidsCategory(it.displayName) }
                    } else {
                        vodList.filter { it.displayName.contains("2026") }
                    }
                    
                    // Fallback to newest if no 2026 movies are found
                    if (filtered.isEmpty() && !isKids) {
                        vodList.sortedByDescending { it.added?.toLongOrNull() ?: 0L }.take(20)
                    } else {
                        filtered.take(20)
                    }
                }
                
                _homeMoviesRow.value = finalMovies
                if (_homeHeroItem.value == null && finalMovies.isNotEmpty()) {
                    _homeHeroItem.value = finalMovies.first()
                }
            }

            val seriesJob = launch {
                val seriesList = XtreamApi.getSeriesList()
                val finalSeries = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    val filtered = if (isKids) {
                        seriesList.filter { AppStorage.isKidsCategory(it.displayName) }
                    } else {
                        seriesList.sortedWith(
                            compareByDescending<SeriesItem> { 
                                val r = it.rating?.toString()?.toDoubleOrNull() ?: 0.0
                                if (r.isNaN()) 0.0 else r
                            }.thenByDescending { it.lastModified?.toLongOrNull() ?: 0L }
                        )
                    }
                    filtered.take(20)
                }
                _homeSeriesRow.value = finalSeries
            }

            liveJob.join()
            moviesJob.join()
            seriesJob.join()

            refreshUserData()
            _homeLoading.value = false
        }
    }

    // --- Live Screen Methods ---
    fun loadLiveCategories() {
        viewModelScope.launch {
            _liveLoading.value = true
            val cats = XtreamApi.getLiveCategories()
            val isKids = isKidsMode.value
            _liveCategories.value = if (isKids) {
                cats.filter { AppStorage.isKidsCategory(it.categoryName) }
            } else {
                cats
            }
            loadLiveChannels(_selectedLiveCat.value)
        }
    }

    fun selectLiveCategory(catId: String) {
        _selectedLiveCat.value = catId
        loadLiveChannels(catId)
    }

    fun setLiveSearch(query: String) {
        _liveSearch.value = query
    }

    fun loadLiveChannels(catId: String) {
        viewModelScope.launch {
            _liveLoading.value = true
            val pid = AppStorage.getActiveProfileId()
            val channels = if (catId == "__FAVS__") {
                val favs = AppStorage.getFavorites(pid, "live")
                val favIds = favs.map { it.id }.toSet()
                XtreamApi.getLiveChannels().filter { favIds.contains(it.id) }
            } else {
                XtreamApi.getLiveChannels(if (catId == "ALL") null else catId)
            }

            val isKids = isKidsMode.value
            _liveChannels.value = if (isKids && catId == "ALL") {
                channels.filter { AppStorage.isKidsCategory(it.groupName) || AppStorage.isKidsCategory(it.name) }
            } else {
                channels
            }
            _liveLoading.value = false
        }
    }

    // --- Movies Screen Methods ---
    fun loadVodCategories() {
        viewModelScope.launch {
            _vodLoading.value = true
            val cats = XtreamApi.getVodCategories()
            val isKids = isKidsMode.value
            _vodCategories.value = if (isKids) {
                cats.filter { AppStorage.isKidsCategory(it.categoryName) }
            } else {
                cats
            }
            loadVodStreams(_selectedVodCat.value)
        }
    }

    fun selectVodCategory(catId: String) {
        _selectedVodCat.value = catId
        loadVodStreams(catId)
    }

    fun setVodSearch(query: String) {
        _vodSearch.value = query
    }

    fun loadVodStreams(catId: String) {
        viewModelScope.launch {
            _vodLoading.value = true
            val streams = XtreamApi.getVodStreams(if (catId == "ALL") null else catId)
            val isKids = isKidsMode.value
            _vodStreams.value = if (isKids && catId == "ALL") {
                streams.filter { AppStorage.isKidsCategory(it.displayName) }
            } else {
                streams
            }
            _vodLoading.value = false
        }
    }

    // --- Series Screen Methods ---
    fun loadSeriesCategories() {
        viewModelScope.launch {
            _seriesLoading.value = true
            val cats = XtreamApi.getSeriesCategories()
            val isKids = isKidsMode.value
            _seriesCategories.value = if (isKids) {
                cats.filter { AppStorage.isKidsCategory(it.categoryName) }
            } else {
                cats
            }
            loadSeriesList(_selectedSeriesCat.value)
        }
    }

    fun selectSeriesCategory(catId: String) {
        _selectedSeriesCat.value = catId
        loadSeriesList(catId)
    }

    fun setSeriesSearch(query: String) {
        _seriesSearch.value = query
    }

    fun loadSeriesList(catId: String) {
        viewModelScope.launch {
            _seriesLoading.value = true
            val list = XtreamApi.getSeriesList(if (catId == "ALL") null else catId)
            val isKids = isKidsMode.value
            _seriesList.value = if (isKids && catId == "ALL") {
                list.filter { AppStorage.isKidsCategory(it.displayName) }
            } else {
                list
            }
            _seriesLoading.value = false
        }
    }

    // --- Favorites Actions ---
    fun isFavorite(kind: String, id: String): Boolean {
        val pid = AppStorage.getActiveProfileId()
        return AppStorage.isFavorite(pid, kind, id)
    }

    fun toggleFavorite(kind: String, id: String, title: String, image: String?): Boolean {
        val pid = AppStorage.getActiveProfileId()
        val result = AppStorage.toggleFavorite(pid, kind, id, title, image)
        refreshUserData(pid)
        return result
    }

    // --- Progress Actions ---
    fun saveProgress(item: ProgressItem) {
        val pid = AppStorage.getActiveProfileId()
        AppStorage.upsertProgress(pid, item)
        refreshUserData(pid)
    }

    fun removeProgress(key: String) {
        val pid = AppStorage.getActiveProfileId()
        AppStorage.removeProgress(pid, key)
        refreshUserData(pid)
    }
}
