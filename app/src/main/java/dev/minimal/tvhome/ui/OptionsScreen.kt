package dev.minimal.tvhome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.minimal.tvhome.HomeState
import dev.minimal.tvhome.data.BackgroundMode
import dev.minimal.tvhome.data.HomeStyle
import dev.minimal.tvhome.data.WallpaperInterval
import dev.minimal.tvhome.data.WallpaperSettings
import dev.minimal.tvhome.data.WallpaperTopic

private val OptionShape = RoundedCornerShape(14.dp)

/** Pick the home layout and background. Every choice applies straight away. */
@Composable
fun OptionsScreen(
    state: HomeState,
    onSelectStyle: (HomeStyle) -> Unit,
    onWallpaperSettings: (WallpaperSettings) -> Unit,
    onNewPhoto: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val current = state.style
    val wp = state.wallpaperSettings
    val selectedCard = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { selectedCard.requestFocus() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = HorizontalPadding, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Home options", style = MaterialTheme.typography.headlineSmall) }

        item { SectionLabel("Home style") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                StyleOption(
                    title = "Minimal",
                    description = "Rows for Continue watching, Recent and Favorites.",
                    selected = current == HomeStyle.Minimal,
                    onClick = { onSelectStyle(HomeStyle.Minimal) },
                    modifier = if (current == HomeStyle.Minimal) Modifier.focusRequester(selectedCard) else Modifier,
                ) { MinimalPreview() }
                StyleOption(
                    title = "Top Shelf",
                    description = "Apple TV-like. What you're watching sits on top, apps in a grid below. Favorites fill the top row.",
                    selected = current == HomeStyle.Shelf,
                    onClick = { onSelectStyle(HomeStyle.Shelf) },
                    modifier = if (current == HomeStyle.Shelf) Modifier.focusRequester(selectedCard) else Modifier,
                ) { ShelfPreview() }
            }
        }

        item { SectionLabel("Background") }
        item {
            ChoiceRow {
                ChoiceButton("Solid", wp.mode == BackgroundMode.Solid) {
                    onWallpaperSettings(wp.copy(mode = BackgroundMode.Solid))
                }
                ChoiceButton("Unsplash photos", wp.mode == BackgroundMode.Unsplash) {
                    onWallpaperSettings(wp.copy(mode = BackgroundMode.Unsplash))
                }
            }
        }
        if (wp.mode == BackgroundMode.Unsplash) {
            if (!state.unsplashConfigured) {
                item {
                    Hint(
                        "Unsplash photos need a free access key from unsplash.com/developers. " +
                            "Reinstall with: scripts/install.sh --unsplash-key YOUR_KEY",
                    )
                }
            } else {
                item { SubLabel("Topic") }
                item {
                    ChoiceRow {
                        WallpaperTopic.entries.forEach { topic ->
                            ChoiceButton(topic.label, wp.topic == topic) { onWallpaperSettings(wp.copy(topic = topic)) }
                        }
                    }
                }
                item { SubLabel("New photo") }
                item {
                    ChoiceRow {
                        WallpaperInterval.entries.forEach { interval ->
                            ChoiceButton(interval.label, wp.interval == interval) {
                                onWallpaperSettings(wp.copy(interval = interval))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        QuietButton("New photo now", onNewPhoto)
                    }
                }
                item {
                    val status = state.wallpaperStatus
                    Hint(
                        when {
                            status.loading -> "Loading a new photo…"
                            status.error != null -> status.error
                            status.current != null -> "Showing a photo by ${status.current.photographer}."
                            else -> "The first photo loads in a moment."
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { QuietButton("Diagnostics", onDiagnostics) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.TextDim,
        modifier = Modifier.padding(top = 20.dp),
    )
}

@Composable
private fun SubLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextDim, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
}

@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

/** A selectable pill. The selected one is tinted with the accent; focus always reads as white. */
@Composable
private fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Palette.Accent.copy(alpha = 0.25f) else Palette.Surface,
            contentColor = if (selected) Palette.Accent else Palette.Text,
            focusedContainerColor = Palette.Text,
            focusedContentColor = Palette.Background,
        ),
    ) {
        Text(if (selected) "✓  $text" else text)
    }
}

@Composable
private fun StyleOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    preview: @Composable () -> Unit,
) {
    Column(Modifier.width(300.dp)) {
        Card(
            onClick = onClick,
            modifier = modifier.size(300.dp, 169.dp),
            shape = CardDefaults.shape(OptionShape),
            colors = CardDefaults.colors(containerColor = Palette.Surface),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(
                border = if (selected) Border(BorderStroke(2.dp, Palette.Accent), shape = OptionShape) else Border.None,
                focusedBorder = Border(BorderStroke(3.dp, Palette.Text), shape = OptionShape),
            ),
        ) {
            Box(Modifier.fillMaxSize().padding(16.dp)) { preview() }
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Text("Current", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
            }
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Sketch of the Minimal layout: three rows of cards. */
@Composable
private fun MinimalPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(if (row == 0) 3 else 4) {
                    Box(
                        Modifier
                            .size(if (row == 0) 70.dp else 52.dp, if (row == 0) 40.dp else 29.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (row == 0) Palette.Accent.copy(alpha = 0.75f) else Palette.TextDim.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

/** Sketch of the Top Shelf layout: a wide shelf over a five-column grid. */
@Composable
private fun ShelfPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(listOf(Palette.Accent.copy(alpha = 0.75f), Palette.SurfaceHigh))),
        )
        repeat(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) {
                    Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(4.dp)).background(Palette.TextDim.copy(alpha = 0.4f)))
                }
            }
        }
    }
}
