package dev.minimal.tvhome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.minimal.tvhome.data.TvApp
import dev.minimal.tvhome.data.WatchNextItem

val AppCardWidth = 192.dp
val AppCardHeight = 108.dp
val PosterWidth = 256.dp
val PosterHeight = 144.dp

private val CardShape = RoundedCornerShape(12.dp)

@Composable
private fun focusScale() = CardDefaults.scale(focusedScale = 1.08f)

@Composable
private fun focusBorder() = CardDefaults.border(
    focusedBorder = Border(border = BorderStroke(2.dp, Palette.Accent), shape = CardShape),
)

@Composable
private fun cardShape() = CardDefaults.shape(shape = CardShape)

@Composable
private fun cardColors() = CardDefaults.colors(containerColor = Palette.Surface, contentColor = Palette.Text)

/** App banner card. The label only shows under the focused card, to keep the screen quiet. */
@Composable
fun AppCard(
    app: TvApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(Modifier.width(AppCardWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(AppCardWidth, AppCardHeight),
            interactionSource = interaction,
            shape = cardShape(),
            colors = cardColors(),
            scale = focusScale(),
            border = focusBorder(),
        ) {
            AppArt(app)
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Palette.Text else Color.Transparent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
internal fun AppArt(app: TvApp) {
    val banner = rememberDrawableImage("banner:${app.packageName}", app.banner, 320, 180)
    if (banner != null) {
        Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        return
    }
    val icon = rememberDrawableImage("icon:${app.packageName}", app.icon, 96, 96)
    Row(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A plain text tile, such as "All apps". */
@Composable
fun TextCard(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(Modifier.width(AppCardWidth)) {
        Card(
            onClick = onClick,
            modifier = modifier.size(AppCardWidth, AppCardHeight),
            shape = cardShape(),
            colors = CardDefaults.colors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Text),
            scale = focusScale(),
            border = focusBorder(),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Poster (or title placeholder), optional source-app badge, and progress bar. Fills its parent. */
@Composable
internal fun PosterArt(item: WatchNextItem, app: TvApp?, width: Dp, showBadge: Boolean) {
    val posterPx = with(LocalDensity.current) { width.roundToPx() }
    val poster = rememberRemoteImage(item.imageUris, posterPx)
    val badge = if (showBadge) rememberDrawableImage("icon:${item.packageName}", app?.icon, 64, 64) else null
    Box(Modifier.fillMaxSize()) {
        if (poster != null) {
            Image(poster, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Palette.SurfaceHigh).padding(16.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (badge != null) {
            Image(
                badge,
                contentDescription = app?.label,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
        item.progress?.let { progress ->
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Palette.Accent))
            }
        }
    }
}
