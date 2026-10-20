package dev.minimal.tvhome.ui

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MinimalHome"

/** [FocusRequester.requestFocus] without the exception it throws before its target is laid out. */
fun FocusRequester.tryRequestFocus(): Boolean = runCatching { requestFocus() }.getOrDefault(false)

/**
 * Scrolls a row back to its start, but only if it has moved. [LazyListState.scrollToItem] waits
 * for the list's first layout, and a row below the fold of a freshly shown screen has had none:
 * the call would hang until the user scrolls, then finish late and yank focus around.
 */
suspend fun LazyListState.scrollToStart() {
    if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0) scrollToItem(0)
}

/**
 * Makes a focus request now, and if its target isn't laid out yet, again on the next few frames.
 * Bounded in real time, so a request made on a screen that then sits idle (no frames) can't
 * wake up on the user's next key press and steal the focus that key just moved.
 */
suspend fun focusWhenReady(request: () -> Boolean, timeoutMillis: Long = 400): Boolean {
    if (request()) return true
    return withTimeoutOrNull(timeoutMillis) {
        var ok = false
        while (!ok) {
            withFrameNanos { }
            ok = request()
        }
        ok
    } ?: false
}

/**
 * Keeps something focused on a remote-driven screen. Compose can be left with nothing focused
 * after the window loses and regains focus (every Home press does that) or after switching
 * screens; the next key press is then spent on choosing a focus target instead of moving.
 * Whenever the window is focused but nothing on this screen is, [request] runs after a short
 * grace period, so a pending focus restore gets to go first.
 */
@Composable
fun FocusGuard(hasFocus: Boolean, enabled: Boolean = true, request: () -> Boolean) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val latest by rememberUpdatedState(request)
    LaunchedEffect(windowFocused, hasFocus, enabled) {
        if (!enabled || !windowFocused || hasFocus) return@LaunchedEffect
        delay(80)
        if (!focusWhenReady({ latest() })) Log.w(TAG, "Nothing on screen could take focus")
    }
}
