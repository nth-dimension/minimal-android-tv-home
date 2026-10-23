package dev.minimal.tvhome.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.minimal.tvhome.HomeState
import dev.minimal.tvhome.data.Launch
import dev.minimal.tvhome.data.Platform
import dev.minimal.tvhome.data.SearchRules
import dev.minimal.tvhome.data.TvApp
import dev.minimal.tvhome.data.WatchNextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FieldShape = RoundedCornerShape(28.dp)
private val PosterShape = RoundedCornerShape(12.dp)

/**
 * Type (or say) a title. Matches from Continue watching and your apps show right away; below them,
 * one tile per app that can search, which opens that app's own search with the words filled in.
 */
@Composable
fun SearchScreen(state: HomeState, actions: HomeActions) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Asks the package manager about every app, so off the main thread.
    val targets by produceState(emptyList<TvApp>(), state.apps, state.recent) {
        value = withContext(Dispatchers.IO) { Launch.searchTargets(context, state.apps, state.recent) }
    }
    val everywhere by produceState(false) { value = withContext(Dispatchers.IO) { Launch.globalSearchIntent(context, "") != null } }
    val platform = remember { Platform.name(context) }
    val voiceIntent by produceState<Intent?>(null) {
        value = withContext(Dispatchers.IO) {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a title")
                .takeIf { context.packageManager.resolveActivity(it, 0) != null }
        }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) query = spoken
    }

    val shows = remember(q, state.allWatchNext) { SearchRules.filter(q, state.allWatchNext) { listOf(it.title, it.subtitle) } }
    val apps = remember(q, state.apps) { SearchRules.filter(q, state.apps) { listOf(it.label) } }

    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    // Straight to the first app to search in, not whichever tile is nearest the field.
    fun toResults(): Boolean =
        runCatching { firstResult.requestFocus() }.getOrDefault(false) || focusManager.moveFocus(FocusDirection.Down)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "field") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = HorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSearch = {
                        keyboard?.hide()
                        toResults()
                    },
                    // With the keyboard closed, the text field keeps Down and Right as cursor keys,
                    // so the remote could never leave it (seen on a BRAVIA, Android 12).
                    modifier = Modifier.weight(1f).focusRequester(field).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> toResults()
                            Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                            else -> false
                        }
                    },
                )
                voiceIntent?.let { intent ->
                    Spacer(Modifier.width(16.dp))
                    QuietButton("Voice", onClick = { runCatching { voice.launch(intent) } })
                }
            }
        }

        if (q.isEmpty()) {
            item(key = "hint") {
                HintRow("Type a title or a video. Then pick an app to search in" + (if (everywhere) ", or search everywhere on $platform." else "."))
            }
        }

        if (q.isNotEmpty()) {
            item(key = "search-in") {
                ShelfRow("Search for “$q” in") {
                    itemsIndexed(targets, key = { _, it -> "s:${it.packageName}" }) { i, app ->
                        AppCard(
                            app = app,
                            onClick = { Launch.search(context, app, q) },
                            onLongClick = { actions.showMenu(app) },
                            modifier = if (i == 0) Modifier.focusRequester(firstResult) else Modifier,
                        )
                    }
                    if (everywhere) {
                        item(key = "everywhere") {
                            TextCard(
                                "All of $platform",
                                onClick = { Launch.globalSearch(context, q) },
                                modifier = if (targets.isEmpty()) Modifier.focusRequester(firstResult) else Modifier,
                            )
                        }
                    }
                }
            }
        }

        if (shows.isNotEmpty()) {
            item(key = "shows") {
                ShelfRow("In Continue watching") {
                    items(shows, key = { "w:${it.id}" }) { item ->
                        PosterCard(item, state.appsByPackage[item.packageName], onClick = { actions.openWatchNext(item) })
                    }
                }
            }
        }

        if (apps.isNotEmpty()) {
            item(key = "apps") {
                ShelfRow("Apps") {
                    items(apps, key = { "a:${it.packageName}" }) { app ->
                        AppCard(app = app, onClick = { actions.openApp(app) }, onLongClick = { actions.showMenu(app) })
                    }
                }
            }
        }

        if (q.isNotEmpty() && targets.isEmpty() && !everywhere) {
            item(key = "none") { HintRow("None of your apps accept searches from another app.") }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = Palette.Text),
        cursorBrush = SolidColor(Palette.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (focused) Palette.SurfaceHigh else Palette.Surface, FieldShape)
                    .border(BorderStroke(2.dp, if (focused) Palette.Accent else Color.Transparent), FieldShape)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        "Search titles, videos and apps",
                        style = MaterialTheme.typography.titleLarge,
                        color = Palette.TextDim,
                    )
                }
                inner()
            }
        },
    )
}

/** A Continue watching match: poster with progress, title underneath. */
@Composable
private fun PosterCard(item: WatchNextItem, app: TvApp?, onClick: () -> Unit) {
    Column(Modifier.width(PosterWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(PosterWidth, PosterHeight),
            shape = CardDefaults.shape(PosterShape),
            colors = CardDefaults.colors(containerColor = Palette.Surface, contentColor = Palette.Text),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, Palette.Accent), shape = PosterShape),
            ),
        ) {
            PosterArt(item, app, PosterWidth, showBadge = true)
        }
        Text(
            item.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
