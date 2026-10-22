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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.abs
import kotlin.math.sign

private val DeckCardWidth = 480.dp
private val DeckCardHeight = 270.dp
private val DeckShape = RoundedCornerShape(16.dp)

/** How far a card sits from the centre, and how big it is, by its distance in steps (0, 1, 2, 3). */
private val StepOffsets = listOf(0.dp, 280.dp, 440.dp, 560.dp)
private val StepScales = listOf(1f, 0.615f, 0.48f, 0.345f)
private const val SIDE = 2

/**
 * Continue watching as a carousel: the selected show in the centre, its neighbours smaller and
 * tucked under it on either side, its title and description on the tile. Left and right move
 * through the carousel; select opens the centre card. One focus stop, so Up and Down move between
 * rows as before.
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

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // The card strip is the focusable part, not the title below it.
        val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        Box(
            Modifier
                .fillMaxWidth()
                .height(DeckCardHeight + 24.dp)
                .then(focusModifier)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionRight -> {
                            // Swallow presses at the end so focus doesn't jump off the carousel.
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
                            // The carousel is full width, so plain focus search lands on whichever
                            // app sits under its middle. Go to the first app of the next row instead.
                            if (onDown == null) return@onKeyEvent false
                            if (event.type == KeyEventType.KeyDown) downHandled = onDown()
                            downHandled
                        }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            // Only cards near the centre are composed: those showing, plus one fading out each side.
            for (i in (current - SIDE - 1).coerceAtLeast(0)..(current + SIDE + 1).coerceAtMost(items.lastIndex)) {
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
    }
}

/** One card, placed by its depth: 0 is the centre, negative to the left, positive to the right. */
@Composable
private fun BoxScope.DeckCard(item: WatchNextItem, app: TvApp?, depth: Float, focused: Boolean) {
    val d by animateFloatAsState(depth.coerceIn(-SIDE - 1f, SIDE + 1f), tween(320), label = "deck")
    val stepsPx = with(LocalDensity.current) { StepOffsets.map { it.toPx() } }
    val focusScale by animateFloatAsState(if (focused) 1.03f else 1f, tween(160), label = "focus")
    val distance = abs(d)
    Box(
        Modifier
            .align(Alignment.Center)
            .zIndex(-distance)
            .graphicsLayer {
                // Offsets between whole steps are interpolated, so cards glide while animating.
                val step = distance.toInt().coerceAtMost(StepOffsets.lastIndex - 1)
                val t = distance - step
                translationX = sign(d) * (stepsPx[step] + (stepsPx[step + 1] - stepsPx[step]) * t)
                val scale = StepScales[step] + (StepScales[step + 1] - StepScales[step]) * t
                val s = scale * (if (distance < 0.5f) focusScale else 1f)
                scaleX = s
                scaleY = s
                alpha = if (distance > SIDE) (SIDE + 1f - distance).coerceIn(0f, 1f) else 1f
            }
            .size(DeckCardWidth, DeckCardHeight)
            .clip(DeckShape)
            .background(Palette.Surface)
            .border(
                if (focused) BorderStroke(3.dp, Palette.Accent) else BorderStroke(0.dp, Color.Transparent),
                DeckShape,
            ),
    ) {
        PosterArt(item, app, DeckCardWidth, showBadge = false, showTitle = false)
        // The text sits on the art, over a gradient: full on the centre card, fading out on the
        // way to the sides, where the cards are mostly tucked away anyway.
        val textAlpha = (1f - distance).coerceIn(0f, 1f)
        if (textAlpha > 0f) TileText(item, app, Modifier.graphicsLayer { alpha = textAlpha })
        // Cards further out are darker, so the centre one reads as on top.
        val scrim = (0.3f * distance).coerceIn(0f, 0.7f)
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
    }
}

private val TileGradient = Brush.verticalGradient(
    0f to Color.Transparent,
    0.4f to Color.Black.copy(alpha = 0.55f),
    1f to Color.Black.copy(alpha = 0.9f),
)

/** Title, app and episode line, and description, bottom-left on the tile above the progress bar. */
@Composable
private fun BoxScope.TileText(item: WatchNextItem, app: TvApp?, modifier: Modifier) {
    Column(
        modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(TileGradient)
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 16.dp),
    ) {
        Text(
            item.title,
            style = MaterialTheme.typography.titleLarge.copy(shadow = OnPhotoShadow),
            color = Palette.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = app?.icon
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
            }
            val meta = listOfNotNull(app?.label ?: item.packageName, item.subtitle)
            Text(
                meta.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.description?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
