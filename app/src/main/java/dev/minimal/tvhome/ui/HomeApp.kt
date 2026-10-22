package dev.minimal.tvhome.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.minimal.tvhome.HomeViewModel
import dev.minimal.tvhome.data.HomeStyle
import dev.minimal.tvhome.data.Launch
import dev.minimal.tvhome.screensaver.ScreensaverPreviewActivity
import kotlinx.coroutines.flow.SharedFlow

enum class Screen { Home, AllApps, Search, Options, Diagnostics }

@Composable
fun HomeApp(viewModel: HomeViewModel, homePresses: SharedFlow<Unit>) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var menuPackage by remember { mutableStateOf<String?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }
    var showTimer by remember { mutableStateOf(false) }

    LaunchedEffect(homePresses) {
        homePresses.collect {
            screen = Screen.Home
            menuPackage = null
            showTimer = false
            resetKey++
        }
    }

    // One actions object for the life of the screen: a new one on every redraw would make both
    // layouts redraw with it. The lambdas read the latest state through this holder.
    val latest = rememberUpdatedState(state)
    val actions = remember(context) {
        HomeActions(
            openApp = { Launch.app(context, it) },
            openWatchNext = { Launch.watchNext(context, it, latest.value.appsByPackage[it.packageName]) },
            showMenu = { menuPackage = it.packageName },
            openAllApps = { screen = Screen.AllApps },
            openSettings = { Launch.settings(context) },
            openOptions = { screen = Screen.Options },
            openSearch = { screen = Screen.Search },
            openTimer = { showTimer = true },
        )
    }
    val wallpaperImage = rememberWallpaperImage(state.wallpaper)

    HomeTheme {
        Box(Modifier.fillMaxSize()) {
            // The photo background shows behind the home layouts only; Options and Diagnostics stay plain.
            val wallpaper = state.wallpaper?.takeIf { screen == Screen.Home || screen == Screen.AllApps }
            if (wallpaper != null) WallpaperBackground(wallpaperImage, state.style)

            when (screen) {
                Screen.Home -> {
                    // Nowhere further back to go, so Back does what Home does: return to the first card.
                    BackHandler { resetKey++ }
                    when (state.style) {
                        HomeStyle.Minimal -> HomeScreen(state, resetKey, actions)
                        HomeStyle.Shelf -> ShelfHomeScreen(state, resetKey, actions)
                    }
                }
                Screen.AllApps -> {
                    BackHandler { screen = Screen.Home }
                    AllAppsScreen(state.apps, actions.openApp, actions.showMenu)
                }
                Screen.Search -> {
                    BackHandler { screen = Screen.Home }
                    SearchScreen(state, actions)
                }
                Screen.Options -> {
                    BackHandler { screen = Screen.Home }
                    OptionsScreen(
                        state = state,
                        onSelectStyle = { style ->
                            viewModel.setStyle(style)
                            screen = Screen.Home
                        },
                        onWallpaperSettings = viewModel::setWallpaperSettings,
                        onNewPhoto = { viewModel.refreshWallpaper(force = true) },
                        onDiagnostics = { screen = Screen.Diagnostics },
                        onPreviewScreensaver = {
                            runCatching { context.startActivity(Intent(context, ScreensaverPreviewActivity::class.java)) }
                        },
                    )
                }
                Screen.Diagnostics -> {
                    BackHandler { screen = Screen.Options }
                    DiagnosticsScreen(viewModel, state)
                }
            }

            // Minimal shows the credit under its clock instead.
            if (wallpaper != null && screen == Screen.Home && state.style == HomeStyle.Shelf) {
                WallpaperCredit(wallpaper, Modifier.align(Alignment.BottomEnd))
            }

            if (showTimer) {
                SleepTimerDialog(minutesLeft = rememberSleepMinutesLeft(), onDismiss = { showTimer = false })
            }

            val menuApp = menuPackage?.let { state.appsByPackage[it] }
            if (menuApp != null) {
                AppMenuDialog(
                    app = menuApp,
                    pinned = menuApp.packageName in state.favoritePackages,
                    onDismiss = { menuPackage = null },
                    onTogglePin = { viewModel.togglePin(menuApp.packageName) },
                    onMove = { delta -> viewModel.move(menuApp.packageName, delta) },
                    onAppInfo = { Launch.appInfo(context, menuApp.packageName) },
                    onOptions = { screen = Screen.Options },
                )
            }
        }
    }
}
