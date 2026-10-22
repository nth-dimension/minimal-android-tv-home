package dev.minimal.tvhome.ui

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.minimal.tvhome.data.SleepTimer
import kotlinx.coroutines.delay
import java.util.Date

/** Minutes left on the sleep timer, updated while shown, or null with no timer running. */
@Composable
fun rememberSleepMinutesLeft(): Int? {
    val endsAt by SleepTimer.endsAt.collectAsStateWithLifecycle()
    val left by produceState<Int?>(initialValue = null, endsAt) {
        val end = endsAt ?: run { value = null; return@produceState }
        while (true) {
            val now = SystemClock.elapsedRealtime()
            value = if (now < end) SleepTimer.minutesLeft(end, now) else null
            if (now >= end) break
            // Wake when the shown minute changes, not on a fixed tick.
            delay(((end - now - 1) % 60_000) + 1)
        }
    }
    return left
}

/** Top bar label for the timer button. */
fun sleepTimerLabel(minutesLeft: Int?): String = if (minutesLeft == null) "Timer" else "Off in $minutesLeft min"

/** Pick how long until the TV turns off, or cancel a running timer. */
@Composable
fun SleepTimerDialog(minutesLeft: Int?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val canTurnOff = remember { SleepTimer.canTurnOff(context) }
    val endsAt by SleepTimer.endsAt.collectAsStateWithLifecycle()
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
                modifier = Modifier.width(420.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Turn off the TV in", style = MaterialTheme.typography.titleLarge)
                if (minutesLeft != null) {
                    val clock = endsAt?.let {
                        DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + (it - SystemClock.elapsedRealtime())))
                    }
                    Text(
                        "Turns off ${clock?.let { "at $it, " } ?: ""}in $minutesLeft min. Pick a new time to restart it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextDim,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SleepTimer.CHOICES.forEachIndexed { i, minutes ->
                        QuietButton(
                            text = "$minutes min",
                            onClick = {
                                SleepTimer.start(context, minutes * 60_000L)
                                onDismiss()
                            },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
                if (minutesLeft != null) {
                    QuietButton("Cancel timer", { SleepTimer.cancel(context); onDismiss() }, Modifier.fillMaxWidth())
                }
                if (!canTurnOff) {
                    Text(
                        "Minimal Home can't turn the TV off yet. Run scripts/install.sh to allow it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim,
                    )
                }
            }
        }
    }
}
