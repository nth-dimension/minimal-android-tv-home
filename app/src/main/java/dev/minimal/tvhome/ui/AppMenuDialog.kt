package dev.minimal.tvhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.minimal.tvhome.data.TvApp

/** Long-press menu. Move buttons keep the menu open so an app can be nudged several places. */
@Composable
fun AppMenuDialog(
    app: TvApp,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: (Int) -> Unit,
    onAppInfo: () -> Unit,
    onOptions: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Text),
        ) {
            Column(
                modifier = Modifier.width(340.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                QuietButton(
                    text = if (pinned) "Remove from favorites" else "Pin to favorites",
                    onClick = { onTogglePin(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().focusRequester(first),
                )
                if (pinned) {
                    QuietButton("Move left", { onMove(-1) }, Modifier.fillMaxWidth())
                    QuietButton("Move right", { onMove(1) }, Modifier.fillMaxWidth())
                }
                QuietButton("App info", { onAppInfo(); onDismiss() }, Modifier.fillMaxWidth())
                QuietButton("Home options", { onOptions(); onDismiss() }, Modifier.fillMaxWidth())
            }
        }
    }
}
