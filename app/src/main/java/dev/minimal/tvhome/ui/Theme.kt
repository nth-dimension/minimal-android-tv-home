package dev.minimal.tvhome.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

object Palette {
    val Background = Color(0xFF0E0F11)
    val Surface = Color(0xFF1A1C20)
    val SurfaceHigh = Color(0xFF262930)
    val Text = Color(0xFFECEAE6)
    val TextDim = Color(0xFF9A9DA6)
    val Accent = Color(0xFFE8B04A)
}

val HorizontalPadding = 48.dp

@Composable
fun HomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.Background,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.TextDim,
            border = Palette.Accent,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = SurfaceDefaults.colors(containerColor = Palette.Background, contentColor = Palette.Text),
        ) {
            content()
        }
    }
}
