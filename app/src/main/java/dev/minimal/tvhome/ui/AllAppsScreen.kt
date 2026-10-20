package dev.minimal.tvhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.minimal.tvhome.data.TvApp

@Composable
fun AllAppsScreen(apps: List<TvApp>, onOpen: (TvApp) -> Unit, onMenu: (TvApp) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isEmpty()) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text(
            "All apps",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = HorizontalPadding, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(AppCardWidth),
            contentPadding = PaddingValues(horizontal = HorizontalPadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(apps, key = { _, it -> it.packageName }) { i, app ->
                AppCard(
                    app = app,
                    onClick = { onOpen(app) },
                    onLongClick = { onMenu(app) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}
