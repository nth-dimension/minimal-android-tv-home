package dev.minimal.tvhome.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.minimal.tvhome.BuildConfig
import dev.minimal.tvhome.HomeState
import dev.minimal.tvhome.HomeViewModel
import dev.minimal.tvhome.data.HomeRedirectRules
import dev.minimal.tvhome.data.RedirectStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

private const val TAG = "MinimalHome"

/**
 * Shows what the launcher can see on this device: which apps publish Continue watching rows,
 * and whether permissions and the Home takeover are in place. Also written to logcat.
 */
@Composable
fun DiagnosticsScreen(viewModel: HomeViewModel, state: HomeState) {
    val context = LocalContext.current
    val lines by produceState(initialValue = listOf("Loading…"), state.apps) {
        value = withContext(Dispatchers.IO) {
            val rows = viewModel.rawWatchNext().sortedByDescending { it.lastEngagementMillis }
            @Suppress("DEPRECATION")
            val home = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
            val fireTv = viewModel.isFireTv()
            val redirect = fireTv && viewModel.hasHomeRedirect()
            buildList {
                add("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, commit ${BuildConfig.GIT_COMMIT})")
                add(
                    "Default home app: ${home ?: "none"}" + when {
                        home == "android" -> " (chooser, no default set)"
                        // Fire OS won't let its launcher be replaced; the redirect opens Minimal Home over it.
                        fireTv && home == HomeRedirectRules.AMAZON_LAUNCHER -> if (redirect) " (Amazon's, redirected)" else " (Amazon's)"
                        else -> ""
                    },
                )
                if (fireTv) {
                    add("Fire TV: Home button redirect ${if (redirect) "on" else "off (run scripts/install.sh)"}")
                    val timeFormat = DateFormat.getTimeFormat(context)
                    add("    redirects: ${RedirectStats.describe(viewModel.redirectStats()) { timeFormat.format(Date(it)) }}")
                }
                add(
                    "TV listings permission: " + when {
                        !viewModel.hasTvProvider() -> "not applicable (this TV has no Watch Next table)"
                        viewModel.hasTvListingsPermission() -> "granted"
                        else -> "missing"
                    },
                )
                add("Usage access: ${if (state.hasUsageAccess) "granted" else "missing"}")
                add("Playback access: ${if (viewModel.hasPlaybackAccess()) "granted" else "missing (run scripts/install.sh)"}")
                add("Installed TV apps: ${state.apps.size}")
                add("Watch Next rows visible: ${rows.size}")
                rows.groupingBy { it.packageName }.eachCount().forEach { (pkg, count) ->
                    val label = state.appsByPackage[pkg]?.label ?: pkg
                    add("    $label ($pkg): $count")
                }
                val played = viewModel.playHistory()
                add("Recorded playbacks: ${played.size}")
                played.forEach { p ->
                    val label = state.appsByPackage[p.packageName]?.label ?: p.packageName
                    val publishes = rows.any { it.packageName == p.packageName }
                    add(
                        "    $label | ${p.title} | ${p.subtitle ?: "-"} | ${p.positionMillis / 1000}s of ${p.durationMillis / 1000}s" +
                            if (publishes) " | hidden: app has its own rows" else "",
                    )
                    add("        image: ${imageReport(context, p.imageUris)}")
                }
                add("")
                rows.forEach { r ->
                    val pct = r.progress?.let { "${(it * 100).toInt()}%" } ?: "-"
                    add(
                        "${r.packageName} | ${r.title} | ${r.subtitle ?: "-"} | type=${r.type} | " +
                            "progress=$pct | browsable=${r.browsable} | deeplink=${r.intentUri != null}",
                    )
                    add("    image: ${imageReport(context, r.imageUris)}")
                }
            }
        }
    }
    LaunchedEffect(lines) { lines.forEach { Log.i(TAG, it) } }

    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = HorizontalPadding, vertical = 32.dp),
    ) {
        item { Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp)) }
        items(lines.withIndex().toList(), key = { it.index }) { (i, line) ->
            FocusableLine(line, if (i == 0) Modifier.focusRequester(first) else Modifier)
        }
    }
}

/** Loads each of the row's images now and says which worked, or why each failed. */
private suspend fun imageReport(context: Context, uris: List<String>): String {
    if (uris.isEmpty()) return "none published"
    return uris.map { uri ->
        val ok = ImageLoader.load(context, listOf(uri), 200) != null
        "${ImageLoader.describe(uri)} ${if (ok) "ok" else "failed: ${ImageLoader.failureFor(uri) ?: "unknown"}"}"
    }.joinToString()
}

@Composable
private fun FocusableLine(text: String, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(if (focused) Palette.SurfaceHigh else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
