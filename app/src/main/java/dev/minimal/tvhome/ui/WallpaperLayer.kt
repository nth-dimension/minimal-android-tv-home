package dev.minimal.tvhome.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.minimal.tvhome.data.HomeStyle
import dev.minimal.tvhome.data.Images
import dev.minimal.tvhome.data.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The decoded wallpaper. Lives above the screen switch in [HomeApp], so leaving for Options or
 * Search and coming back shows the same bitmap again rather than decoding it from disk anew.
 * The previous photo stays until the next one is decoded.
 */
@Composable
fun rememberWallpaperImage(wallpaper: Wallpaper?): ImageBitmap? {
    val context = LocalContext.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(wallpaper?.file) {
        val file = wallpaper?.file ?: run { image = null; return@LaunchedEffect }
        val screenWidth = context.resources.displayMetrics.widthPixels
        withContext(Dispatchers.IO) { Images.decodeFile(file, screenWidth)?.asImageBitmap() }?.let { image = it }
    }
    return image
}

/** Full-screen photo behind the home screen, with a dark overlay so text stays readable. */
@Composable
fun WallpaperBackground(image: ImageBitmap?, style: HomeStyle) {
    Crossfade(targetState = image, animationSpec = tween(900), label = "wallpaper") { img ->
        if (img != null) {
            Box(Modifier.fillMaxSize()) {
                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(scrim(style)))
            }
        }
    }
}

/** Soft shadow for text drawn straight onto a photo. */
val OnPhotoShadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)

/**
 * Unsplash asks apps to credit the photographer wherever a photo is shown. [compact] is the
 * smaller, fainter line that sits under the clock; otherwise it sits in the bottom corner.
 */
@Composable
fun WallpaperCredit(wallpaper: Wallpaper, modifier: Modifier = Modifier, compact: Boolean = false) {
    val base = MaterialTheme.typography.labelSmall
    Text(
        text = "Photo by ${wallpaper.photographer} on Unsplash",
        style = base.copy(
            shadow = OnPhotoShadow,
            fontSize = if (compact) base.fontSize * 0.75f else base.fontSize,
            lineHeight = if (compact) base.lineHeight * 0.75f else base.lineHeight,
        ),
        color = Palette.Text.copy(alpha = if (compact) 0.5f else 0.75f),
        modifier = if (compact) modifier else modifier.padding(end = HorizontalPadding, bottom = 20.dp),
    )
}

// Minimal has text all over the screen, so it gets an even dim. Top Shelf keeps the top bright, like tvOS.
private fun scrim(style: HomeStyle): Brush = when (style) {
    HomeStyle.Minimal -> Brush.verticalGradient(
        0f to Palette.Background.copy(alpha = 0.55f),
        1f to Palette.Background.copy(alpha = 0.8f),
    )
    HomeStyle.Shelf -> Brush.verticalGradient(
        0f to Palette.Background.copy(alpha = 0.35f),
        0.3f to Palette.Background.copy(alpha = 0.2f),
        0.5f to Palette.Background.copy(alpha = 0.35f),
        1f to Palette.Background.copy(alpha = 0.88f),
    )
}
