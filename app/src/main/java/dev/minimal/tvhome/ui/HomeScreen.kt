package dev.minimal.tvhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import dev.minimal.tvhome.HomeState
import dev.minimal.tvhome.data.TvApp
import dev.minimal.tvhome.data.WatchNextItem
import dev.minimal.tvhome.data.WatchNextResult

class HomeActions(
    val openApp: (TvApp) -> Unit,
    val openWatchNext: (WatchNextItem) -> Unit,
    val showMenu: (TvApp) -> Unit,
    val openAllApps: () -> Unit,
    val openSettings: () -> Unit,
    val openOptions: () -> Unit,
    val openSearch: () -> Unit,
    val openTimer: () -> Unit,
)

private enum class FirstRow { WatchNext, Recent, Favorites }

@Composable
fun HomeScreen(state: HomeState, resetKey: Int, actions: HomeActions) {
    val listState = rememberLazyListState()
    val recentRow = rememberLazyListState()
    val favoritesRow = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val watchNext = (state.watchNext as? WatchNextResult.Items)?.items.orEmpty()
    val firstRow = when {
        watchNext.isNotEmpty() -> FirstRow.WatchNext
        state.recent.isNotEmpty() -> FirstRow.Recent
        else -> FirstRow.Favorites
    }
    fun focusIf(row: FirstRow, index: Int) =
        if (row == firstRow && index == 0) Modifier.focusRequester(firstFocus) else Modifier

    // Down from the deck goes here: the first app in the row below it.
    val belowDeck = remember { FocusRequester() }
    val rowBelowDeck = if (state.recent.isNotEmpty()) FirstRow.Recent else FirstRow.Favorites
    fun belowDeckIf(row: FirstRow, index: Int) =
        if (watchNext.isNotEmpty() && row == rowBelowDeck && index == 0) Modifier.focusRequester(belowDeck) else Modifier

    // On launch, and on every Home or Back press: jump to the top and focus the first card.
    // Rows scroll back to their start too, so the first card exists to take focus.
    val focusFirst = { firstFocus.tryRequestFocus() }
    LaunchedEffect(resetKey, state.loading) {
        if (state.loading) return@LaunchedEffect
        listState.scrollToStart()
        recentRow.scrollToStart()
        favoritesRow.scrollToStart()
        focusWhenReady(focusFirst)
    }
    var hasFocus by remember { mutableStateOf(false) }
    FocusGuard(hasFocus = hasFocus, enabled = !state.loading, request = focusFirst)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus },
        contentPadding = PaddingValues(top = 32.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "top") { TopBar(actions.openSearch, actions.openTimer, actions.openSettings, actions.openOptions) }

        if (state.watchNext is WatchNextResult.NoPermission) {
            item(key = "watch-hint") {
                HintRow("Continue watching needs the TV listings permission. Run scripts/install.sh to grant it.")
            }
        }
        if (watchNext.isNotEmpty()) {
            item(key = "watch") {
                Column {
                    RowTitle("Continue watching")
                    WatchNextDeck(
                        items = watchNext,
                        appsByPackage = state.appsByPackage,
                        resetKey = resetKey,
                        onOpen = actions.openWatchNext,
                        modifier = Modifier.padding(vertical = 8.dp),
                        focusRequester = if (firstRow == FirstRow.WatchNext) firstFocus else null,
                        onDown = { belowDeck.tryRequestFocus() },
                    )
                }
            }
        }

        if (!state.loading && !state.hasUsageAccess) {
            item(key = "recent-hint") {
                HintRow("Recent apps needs usage access. Run scripts/install.sh to grant it.")
            }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recent") {
                ShelfRow("Recent", recentRow) {
                    itemsIndexed(state.recent, key = { _, it -> "r:${it.packageName}" }) { i, app ->
                        AppCard(
                            app = app,
                            onClick = { actions.openApp(app) },
                            onLongClick = { actions.showMenu(app) },
                            modifier = focusIf(FirstRow.Recent, i).then(belowDeckIf(FirstRow.Recent, i)),
                        )
                    }
                }
            }
        }

        item(key = "favorites") {
            ShelfRow("Favorites", favoritesRow) {
                itemsIndexed(state.favorites, key = { _, it -> "f:${it.packageName}" }) { i, app ->
                    AppCard(
                        app = app,
                        onClick = { actions.openApp(app) },
                        onLongClick = { actions.showMenu(app) },
                        modifier = focusIf(FirstRow.Favorites, i).then(belowDeckIf(FirstRow.Favorites, i)),
                    )
                }
                item(key = "all-apps") {
                    TextCard(
                        text = "All apps",
                        onClick = actions.openAllApps,
                        modifier = focusIf(FirstRow.Favorites, state.favorites.size)
                            .then(belowDeckIf(FirstRow.Favorites, state.favorites.size)),
                    )
                }
            }
        }
        if (!state.loading && state.favorites.isEmpty()) {
            item(key = "fav-hint") { HintRow("Long-press any app to pin it to Favorites.") }
        }
    }
}
