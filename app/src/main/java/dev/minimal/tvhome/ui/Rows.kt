package dev.minimal.tvhome.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun ShelfRow(title: String, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    Column {
        RowTitle(title)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = HorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
fun RowTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(shadow = OnPhotoShadow),
        color = Palette.TextDim,
        modifier = Modifier.padding(start = HorizontalPadding, bottom = 8.dp),
    )
}

@Composable
fun HintRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim,
        modifier = Modifier.padding(horizontal = HorizontalPadding),
    )
}

@Composable
fun TopBar(onSearch: () -> Unit, onTimer: () -> Unit, onSettings: () -> Unit, onOptions: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clock(MaterialTheme.typography.headlineSmall.copy(shadow = OnPhotoShadow))
        Spacer(Modifier.weight(1f))
        // Left or Right past the end of the bar stays put; otherwise focus search would pick
        // whatever card happens to be nearest on the rows below.
        QuietButton("Search", onSearch, Modifier.focusProperties { left = FocusRequester.Cancel })
        Spacer(Modifier.width(12.dp))
        QuietButton(sleepTimerLabel(rememberSleepMinutesLeft()), onTimer)
        Spacer(Modifier.width(12.dp))
        QuietButton("Options", onOptions)
        Spacer(Modifier.width(12.dp))
        QuietButton("Settings", onSettings, Modifier.focusProperties { right = FocusRequester.Cancel })
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Text,
            focusedContentColor = Palette.Background,
        ),
    ) {
        Text(text)
    }
}

@Composable
internal fun Clock(style: TextStyle = MaterialTheme.typography.headlineSmall) {
    val context = LocalContext.current
    val time by produceState(initialValue = DateFormat.getTimeFormat(context).format(Date())) {
        while (true) {
            value = DateFormat.getTimeFormat(context).format(Date())
            // Wake when the minute changes, not on a fixed tick.
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    Text(time, style = style)
}
