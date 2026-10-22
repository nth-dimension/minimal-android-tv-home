package dev.minimal.tvhome.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.minimal.tvhome.HomeState
import dev.minimal.tvhome.data.Ranking
import dev.minimal.tvhome.data.ShelfContent
import dev.minimal.tvhome.data.TvApp
import dev.minimal.tvhome.data.WatchNextItem
import dev.minimal.tvhome.data.WatchNextResult

// Sized for the 960x540dp canvas that TVs render at: the shelf plus about two rows of apps fit on screen.
private val ShelfHeight = 276.dp
private val SidePadding = 56.dp
private val TileWidth = 148.dp
private val TileHeight = 83.dp
private val TileGap = 26.dp
private val ShelfCardWidth = 224.dp
private val ShelfCardHeight = 126.dp
private const val COLUMNS = 5
private val TileShape = RoundedCornerShape(14.dp)

/**
 * Apple TV-like home: a large Top Shelf with in-progress content above a grid of app tiles.
 * The shelf follows whichever app is focused in the top row, like tvOS.
 */
@Composable
fun ShelfHomeScreen(state: HomeState, resetKey: Int, actions: HomeActions) {
    val listState = rememberLazyListState()
    val firstApp = remember { FocusRequester() }
    val firstShelfItem = remember { FocusRequester() }
    val shelfRow = rememberLazyListState()
    val rows = remember(state.gridApps) { state.gridApps.chunked(COLUMNS) }
    var shelfPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val watchNext = (state.watchNext as? WatchNextResult.Items)?.items.orEmpty()
    val focusedPackage = shelfPackage?.takeIf { it in state.appsByPackage } ?: state.gridApps.firstOrNull()?.packageName
    // The shelf narrows to one app only once you've visited that app in the top row.
    val shelfFilter = shelfPackage?.takeIf { it in state.appsByPackage }
    val shelf = remember(watchNext, shelfFilter) { Ranking.shelfContent(watchNext, shelfFilter) }

    // On launch, and on every Home or Back press: back to the top with all of Up Next showing,
    // and focus on its first tile. With nothing in Up Next, the first app takes focus.
    val focusFirst = { (watchNext.isNotEmpty() && firstShelfItem.tryRequestFocus()) || firstApp.tryRequestFocus() }
    LaunchedEffect(resetKey, state.loading) {
        if (state.loading) return@LaunchedEffect
        shelfPackage = null
        listState.scrollToStart()
        shelfRow.scrollToStart()
        focusWhenReady(focusFirst)
    }
    var hasFocus by remember { mutableStateOf(false) }
    FocusGuard(hasFocus = hasFocus, enabled = !state.loading, request = focusFirst)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus },
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "shelf") {
            TopShelf(
                shelf = shelf,
                focusedApp = focusedPackage?.let { state.appsByPackage[it] },
                appsByPackage = state.appsByPackage,
                noPermission = state.watchNext is WatchNextResult.NoPermission,
                hasWallpaper = state.wallpaper != null,
                onOpen = actions.openWatchNext,
                onOptions = actions.openOptions,
                onSearch = actions.openSearch,
                onTimer = actions.openTimer,
                rowState = shelfRow,
                firstItem = firstShelfItem,
            )
        }
        itemsIndexed(rows, key = { i, _ -> "row$i" }) { rowIndex, rowApps ->
            Row(
                modifier = Modifier.padding(horizontal = SidePadding).padding(top = if (rowIndex == 0) 4.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                rowApps.forEachIndexed { col, app ->
                    var modifier: Modifier = Modifier
                    if (rowIndex == 0 && col == 0) modifier = modifier.focusRequester(firstApp)
                    if (rowIndex == 0) {
                        modifier = modifier.onFocusChanged { if (it.isFocused) shelfPackage = app.packageName }
                    }
                    AppTile(
                        app = app,
                        onClick = { actions.openApp(app) },
                        onLongClick = { actions.showMenu(app) },
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopShelf(
    shelf: ShelfContent,
    focusedApp: TvApp?,
    appsByPackage: Map<String, TvApp>,
    noPermission: Boolean,
    hasWallpaper: Boolean,
    onOpen: (WatchNextItem) -> Unit,
    onOptions: () -> Unit,
    onSearch: () -> Unit,
    onTimer: () -> Unit,
    rowState: LazyListState,
    firstItem: FocusRequester,
) {
    var focusedItem by remember(shelf) { mutableStateOf<WatchNextItem?>(null) }
    val backdropItem = focusedItem ?: shelf.items.firstOrNull()

    Box(Modifier.fillMaxWidth().height(ShelfHeight)) {
        // With a photo background on, the photo is the backdrop, as on tvOS.
        if (!hasWallpaper) {
            Backdrop(
                imageUris = backdropItem?.imageUris.orEmpty(),
                app = backdropItem?.let { appsByPackage[it.packageName] } ?: focusedApp,
            )
            // Fade the backdrop into the page so the grid below reads cleanly.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Palette.Background.copy(alpha = 0.25f),
                        0.6f to Palette.Background.copy(alpha = 0.45f),
                        1f to Palette.Background,
                    ),
                ),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = SidePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clock(MaterialTheme.typography.titleLarge.copy(shadow = OnPhotoShadow))
            Spacer(Modifier.width(16.dp))
            // Left or Right past the end of the bar stays put, instead of jumping to a grid tile.
            QuietButton("Search", onSearch, Modifier.focusProperties { left = FocusRequester.Cancel })
            Spacer(Modifier.width(12.dp))
            QuietButton(sleepTimerLabel(rememberSleepMinutesLeft()), onTimer)
            Spacer(Modifier.width(12.dp))
            QuietButton("Options", onOptions, Modifier.focusProperties { right = FocusRequester.Cancel })
        }

        if (shelf.items.isEmpty()) {
            HeroApp(
                app = focusedApp,
                message = if (noPermission) "Up Next needs the TV listings permission. Run scripts/install.sh to grant it." else null,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        } else {
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = shelf.fromPackage?.let { appsByPackage[it]?.label } ?: "Up Next",
                    style = MaterialTheme.typography.titleSmall.copy(shadow = OnPhotoShadow),
                    color = Palette.Text.copy(alpha = 0.9f),
                    modifier = Modifier.padding(start = SidePadding),
                )
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = SidePadding, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(shelf.items, key = { _, it -> "s:${it.id}" }) { i, item ->
                        ShelfCard(
                            modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                            item = item,
                            app = appsByPackage[item.packageName],
                            showBadge = shelf.fromPackage == null,
                            onClick = { onOpen(item) },
                            onFocused = { focusedItem = item },
                        )
                    }
                }
            }
        }
    }
}

/** Blurred, dimmed art behind the shelf: the featured poster, or the focused app's banner. */
@Composable
private fun Backdrop(imageUris: List<String>, app: TvApp?) {
    // Blurred by 32 dp afterwards, so a small decode looks the same and costs a fraction to blur.
    val px = with(LocalDensity.current) { 240.dp.roundToPx() }
    val remote = rememberRemoteImage(imageUris, px)
    val image: ImageBitmap? = remote ?: app?.banner
    Crossfade(targetState = image, animationSpec = tween(450), label = "backdrop") { img ->
        if (img != null) {
            Image(
                img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(32.dp).graphicsLayer { alpha = 0.65f },
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.Background))))
        }
    }
}

/** Shown when there is nothing in progress: the focused app, large, like tvOS apps without shelf content. */
@Composable
private fun HeroApp(app: TvApp?, message: String?, modifier: Modifier) {
    if (app == null) return
    Row(modifier.padding(start = SidePadding, bottom = 28.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.size(ShelfCardWidth, ShelfCardHeight).clip(TileShape).background(Palette.Surface)) {
            AppArt(app)
        }
        Spacer(Modifier.width(24.dp))
        Column {
            Text(app.label, style = MaterialTheme.typography.headlineMedium)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun ShelfCard(
    item: WatchNextItem,
    app: TvApp?,
    showBadge: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.width(ShelfCardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(ShelfCardWidth, ShelfCardHeight).onFocusChanged { if (it.isFocused) onFocused() },
            interactionSource = interaction,
            shape = CardDefaults.shape(TileShape),
            colors = CardDefaults.colors(containerColor = Palette.Surface),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(focusedBorder = Border.None),
            glow = liftGlow(),
        ) {
            PosterArt(item, app, ShelfCardWidth, showBadge)
        }
        Text(
            text = listOfNotNull(item.title, item.subtitle).joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) Palette.Text else Color.Transparent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** App tile: rounded, lifts and grows on focus, label only under the focused tile. */
@Composable
private fun AppTile(app: TvApp, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(Modifier.width(TileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(TileWidth, TileHeight),
            interactionSource = interaction,
            shape = CardDefaults.shape(TileShape),
            colors = CardDefaults.colors(containerColor = Palette.Surface),
            scale = CardDefaults.scale(focusedScale = 1.15f),
            border = CardDefaults.border(focusedBorder = Border.None),
            glow = liftGlow(),
        ) {
            AppArt(app)
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) Palette.Text else Color.Transparent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun liftGlow() = CardDefaults.glow(
    focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.7f), elevation = 18.dp),
)
