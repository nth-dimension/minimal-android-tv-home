package dev.minimal.tvhome

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.tv.TvContract
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.minimal.tvhome.data.AppRepository
import dev.minimal.tvhome.data.HomePrefs
import dev.minimal.tvhome.data.HomeRedirectService
import dev.minimal.tvhome.data.HomeStyle
import dev.minimal.tvhome.data.MediaListenerService
import dev.minimal.tvhome.data.Platform
import dev.minimal.tvhome.data.PlayHistory
import dev.minimal.tvhome.data.PlayedItem
import dev.minimal.tvhome.data.Ranking
import dev.minimal.tvhome.data.RecentAppsRepository
import dev.minimal.tvhome.data.TvApp
import dev.minimal.tvhome.data.WatchNextItem
import dev.minimal.tvhome.data.WatchNextRepository
import dev.minimal.tvhome.data.WatchNextResult
import dev.minimal.tvhome.data.BackgroundMode
import dev.minimal.tvhome.data.Wallpaper
import dev.minimal.tvhome.data.WallpaperRepository
import dev.minimal.tvhome.data.WallpaperRules
import dev.minimal.tvhome.data.WallpaperSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeState(
    val loading: Boolean = true,
    val apps: List<TvApp> = emptyList(),
    val appsByPackage: Map<String, TvApp> = emptyMap(),
    val watchNext: WatchNextResult = WatchNextResult.Items(emptyList()),
    val recent: List<TvApp> = emptyList(),
    val hasUsageAccess: Boolean = true,
    val favorites: List<TvApp> = emptyList(),
    val favoritePackages: List<String> = emptyList(),
    val style: HomeStyle = HomeStyle.Minimal,
    /** Top Shelf grid: favorites first, then the rest alphabetically. */
    val gridApps: List<TvApp> = emptyList(),
    val wallpaperSettings: WallpaperSettings = WallpaperSettings(),
    /** Set only when the Unsplash background is on and a photo is saved. */
    val wallpaper: Wallpaper? = null,
    val wallpaperStatus: WallpaperStatus = WallpaperStatus(),
    val unsplashConfigured: Boolean = false,
)

data class WallpaperStatus(
    val current: Wallpaper? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

private data class Snapshot(
    val loading: Boolean = true,
    val apps: List<TvApp> = emptyList(),
    val watchNext: WatchNextResult = WatchNextResult.Items(emptyList()),
    val lastUsed: Map<String, Long>? = emptyMap(),
    val played: List<PlayedItem> = emptyList(),
) {
    // Derived once per snapshot, not once per emission of any of the five inputs to the state.
    val appsByPackage: Map<String, TvApp> = apps.associateBy { it.packageName }
    val merged: WatchNextResult = (watchNext as? WatchNextResult.Items)?.let {
        WatchNextResult.Items(Ranking.mergePlayed(it.items, played, appsByPackage.keys, System.currentTimeMillis()))
    } ?: watchNext
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val appRepo = AppRepository(application)
    private val watchNextRepo = WatchNextRepository(application)
    private val recentRepo = RecentAppsRepository(application)
    private val prefs = HomePrefs(application)
    private val wallpaperRepo = WallpaperRepository(application, BuildConfig.UNSPLASH_ACCESS_KEY)
    private val wallpaperStatus = MutableStateFlow(WallpaperStatus())
    private var wallpaperJob: Job? = null

    private val snapshot = MutableStateFlow(Snapshot())
    private var refreshJob: Job? = null
    private var watchNextJob: Job? = null
    private var observerRegistered = false

    val state: StateFlow<HomeState> =
        combine(snapshot, prefs.favorites, prefs.style, prefs.wallpaperSettings, wallpaperStatus) { s, favs, style, wpSettings, wpStatus ->
            val byPackage = s.appsByPackage
            val recentPackages = s.lastUsed?.let {
                Ranking.rankRecent(it, byPackage.keys, excluded = setOf(context.packageName))
            }.orEmpty()
            HomeState(
                loading = s.loading,
                apps = s.apps,
                appsByPackage = byPackage,
                // Apps that don't publish Watch Next rows still show what you played in them.
                watchNext = s.merged,
                recent = recentPackages.mapNotNull { byPackage[it] },
                hasUsageAccess = s.lastUsed != null,
                favorites = favs.mapNotNull { byPackage[it] },
                favoritePackages = favs,
                style = style,
                gridApps = Ranking.gridOrder(favs, s.apps.map { it.packageName }).mapNotNull { byPackage[it] },
                wallpaperSettings = wpSettings,
                wallpaper = wpStatus.current.takeIf { wpSettings.mode == BackgroundMode.Unsplash },
                wallpaperStatus = wpStatus,
                unsplashConfigured = wallpaperRepo.isConfigured,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh(reloadApps = true)
    }

    private val watchNextObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshWatchNext()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Show the saved photo immediately, then fetch a new one whenever settings change or one is due.
        viewModelScope.launch {
            wallpaperStatus.value = WallpaperStatus(current = withContext(Dispatchers.IO) { wallpaperRepo.current() })
        }
        prefs.wallpaperSettings.onEach { refreshWallpaper() }.launchIn(viewModelScope)
        // The saved history is read from disk, so off the main thread; the flow is empty until then.
        viewModelScope.launch(Dispatchers.IO) { PlayHistory.load(application) }
        PlayHistory.items.onEach { played -> snapshot.update { it.copy(played = played) } }.launchIn(viewModelScope)
        refresh(reloadApps = true)
    }

    /**
     * On every return to the home screen: Continue watching and recent apps, which change while
     * other apps run. The app list only changes with a package, so the package receiver reloads
     * it; doing so here too meant re-reading every app's art on every Home press.
     */
    fun refreshOnResume() = refresh(reloadApps = false)

    private fun refresh(reloadApps: Boolean) {
        registerObserverIfPossible()
        refreshWallpaper()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // One source failing keeps its last value; the screen must never stay on "loading".
            val previous = snapshot.value
            val next = withContext(Dispatchers.IO) {
                Snapshot(
                    loading = false,
                    apps = if (reloadApps || previous.apps.isEmpty()) {
                        runCatching { appRepo.loadApps() }.getOrElse { Log.w(TAG, "App list failed", it); previous.apps }
                    } else {
                        previous.apps
                    },
                    watchNext = watchNextRepo.load(),
                    lastUsed = runCatching { recentRepo.lastUsed() }
                        .getOrElse { Log.w(TAG, "Usage stats failed", it); previous.lastUsed },
                )
            }
            snapshot.update { next.copy(played = it.played) }
        }
    }

    /** Apps often write several rows at once, so changes are debounced. */
    fun refreshWatchNext() {
        watchNextJob?.cancel()
        watchNextJob = viewModelScope.launch {
            delay(500)
            val result = withContext(Dispatchers.IO) { watchNextRepo.load() }
            snapshot.update { it.copy(watchNext = result) }
        }
    }

    fun togglePin(packageName: String) {
        viewModelScope.launch { prefs.updateFavorites { Ranking.togglePin(it, packageName) } }
    }

    fun move(packageName: String, delta: Int) {
        val installed = state.value.appsByPackage.keys
        viewModelScope.launch { prefs.updateFavorites { Ranking.move(it, packageName, delta, installed) } }
    }

    /**
     * Whether to show the system's TV-listings permission dialog: only once, and only while the
     * permission is missing. The install script normally grants it over adb, and a dialog on
     * every boot (the activity keeps no saved state) would be worse than the hint row.
     */
    suspend fun shouldAskForTvListings(): Boolean {
        if (!watchNextRepo.isSupported() || watchNextRepo.hasPermission() || prefs.askedTvListings.first()) return false
        prefs.markTvListingsAsked()
        return true
    }

    /**
     * Loads a new photo if one is due, or right away when [force] is set. Nothing is emitted when
     * no fetch happens, so a resume with a fresh photo doesn't redraw the whole screen twice.
     */
    fun refreshWallpaper(force: Boolean = false) {
        if (wallpaperJob?.isActive == true && !force) return
        wallpaperJob?.cancel()
        wallpaperJob = viewModelScope.launch {
            val settings = prefs.wallpaperSettings.first()
            if (settings.mode != BackgroundMode.Unsplash) return@launch
            val current = wallpaperStatus.value.current
            if (!force && !WallpaperRules.isStale(current, settings, System.currentTimeMillis())) return@launch
            wallpaperStatus.value = wallpaperStatus.value.copy(loading = true)
            val result = wallpaperRepo.refreshIfNeeded(settings, force)
            wallpaperStatus.value = WallpaperStatus(current = result.wallpaper, loading = false, error = result.error)
        }
    }

    fun setWallpaperSettings(settings: WallpaperSettings) {
        viewModelScope.launch { prefs.setWallpaperSettings(settings) }
    }

    fun setStyle(style: HomeStyle) {
        viewModelScope.launch { prefs.setStyle(style) }
    }

    fun rawWatchNext(): List<WatchNextItem> =
        runCatching { watchNextRepo.queryRaw() }.getOrDefault(emptyList())

    fun hasTvListingsPermission() = watchNextRepo.hasPermission()

    fun hasTvProvider() = watchNextRepo.isSupported()

    fun hasPlaybackAccess() = MediaListenerService.hasAccess(context)

    fun isFireTv() = Platform.isFireTv(context)

    fun hasHomeRedirect() = HomeRedirectService.isEnabled(context)

    fun playHistory(): List<PlayedItem> = PlayHistory.items.value

    private fun registerObserverIfPossible() {
        if (observerRegistered || !watchNextRepo.isSupported() || !watchNextRepo.hasPermission()) return
        observerRegistered = runCatching {
            context.contentResolver.registerContentObserver(
                TvContract.WatchNextPrograms.CONTENT_URI, true, watchNextObserver,
            )
        }.isSuccess
    }

    override fun onCleared() {
        runCatching { context.unregisterReceiver(packageReceiver) }
        if (observerRegistered) context.contentResolver.unregisterContentObserver(watchNextObserver)
    }

    private companion object {
        const val TAG = "MinimalHome"
    }
}
