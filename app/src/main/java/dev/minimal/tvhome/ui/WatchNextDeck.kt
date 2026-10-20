package dev.minimal.tvhome.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.minimal.tvhome.data.TvApp
import dev.minimal.tvhome.data.WatchNextItem

private val DeckCardWidth = 480.dp
private val DeckCardHeight = 270.dp
private val DeckShape = RoundedCornerShape(16.dp)

/** How far each card behind the front one peeks out to the right. */
private val PeekOffset = 32.dp
private const val BEHIND = 3

/**
 * Continue watching as a deck of big cards: the selected show in front, the next few stacked
 * behind it. Left and right move through the deck; select opens the front card.
 * One focus stop, so Up and Down move between rows as before.
 */
@Composable
fun WatchNextDeck(
    items: List<WatchNextItem>,
    appsByPackage: Map<String, TvApp>,
    resetKey: Int,
    onOpen: (WatchNextItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onDown: (() -> Boolean)? = null,
) {
    var index by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    var downHandled by remember { mutableStateOf(false) }
    LaunchedEffect(resetKey) { index = 0 }
    val current = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val front = items.getOrNull(current) ?: return

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The card stack is the focusable part, not the whole row: focus then moves down to the
        // first app below it, not to whichever app sits under the middle of the screen.
        val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        Box(
            Modifier
                .width(DeckCardWidth + PeekOffset * BEHIND)
                .height(DeckCardHeight + 16.dp)
                .then(focusModifier)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionRight -> {
                            // Swallow presses at the end so focus doesn't jump off the deck.
                            if (event.type == KeyEventType.KeyDown && current < items.lastIndex) index = current + 1
                            true
                        }
                        Key.DirectionLeft -> {
                            if (current == 0) return@onKeyEvent false
                            if (event.type == KeyEventType.KeyDown) index = current - 1
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (event.type == KeyEventType.KeyUp) onOpen(front)
                            true
                        }
                        Key.DirectionDown -> {
                            // The deck is wide, so plain focus search lands on whichever app sits
                            // under its middle. Go to the first app of the next row instead.
                            if (onDown == null) return@onKeyEvent false
                            if (event.type == KeyEventType.KeyDown) downHandled = onDown()
                            downHandled
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            // Only cards near the front are composed: one leaving on the left, the front, and those behind.
            for (i in (current - 1).coerceAtLeast(0)..(current + BEHIND + 1).coerceAtMost(items.lastIndex)) {
                val item = items[i]
                key(item.id) {
                    DeckCard(
                        item = item,
                        app = appsByPackage[item.packageName],
                        depth = (i - current).toFloat(),
                        focused = focused && i == current,
                    )
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        DeckInfo(front, appsByPackage[front.packageName], position = current + 1, total = items.size)
    }
}

/** One card, placed by its depth: below 0 it slides out left, 0 is the front, 1..3 stack behind. */
@Composable
private fun BoxScope.DeckCard(item: WatchNextItem, app: TvApp?, depth: Float, focused: Boolean) {
    val d by animateFloatAsState(depth.coerceIn(-1f, BEHIND + 1f), tween(320), label = "deck")
    val peekPx = with(LocalDensity.current) { PeekOffset.toPx() }
    val widthPx = with(LocalDensity.current) { DeckCardWidth.toPx() }
    val focusScale by animateFloatAsState(if (focused) 1.03f else 1f, tween(160), label = "focus")
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .zIndex(-d)
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 0.5f)
                if (d < 0f) {
                    translationX = d * widthPx * 0.5f
                    alpha = (1f + d).coerceIn(0f, 1f)
                } else {
                    val s = (1f - 0.1f * d) * (if (d < 0.5f) focusScale else 1f)
                    scaleX = s
                    scaleY = s
                    translationX = d * peekPx
                    alpha = if (d > BEHIND) (BEHIND + 1f - d).coerceIn(0f, 1f) else 1f
                }
            }
            .size(DeckCardWidth, DeckCardHeight)
            .clip(DeckShape)
            .background(Palette.Surface)
            .border(
                if (focused) BorderStroke(3.dp, Palette.Accent) else BorderStroke(0.dp, Color.Transparent),
                DeckShape,
            ),
    ) {
        PosterArt(item, app, DeckCardWidth, showBadge = true)
        // Cards further back are darker, so the front one reads as on top.
        val scrim = (0.22f * d).coerceIn(0f, 0.7f)
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
    }
}

@Composable
private fun DeckInfo(item: WatchNextItem, app: TvApp?, position: Int, total: Int) {
    Column(Modifier.padding(end = HorizontalPadding)) {
        Text(
            item.title,
            style = MaterialTheme.typography.headlineSmall.copy(shadow = OnPhotoShadow),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        item.subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.titleSmall.copy(shadow = OnPhotoShadow),
                color = Palette.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = rememberDrawableImage("icon:${item.packageName}", app?.icon, 64, 64)
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                app?.label ?: item.packageName,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = OnPhotoShadow),
                color = Palette.TextDim,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "$position of $total",
            style = MaterialTheme.typography.labelMedium.copy(shadow = OnPhotoShadow),
            color = Palette.TextDim,
        )
    }
}
