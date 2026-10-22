package dev.minimal.tvhome.data

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap

/** An installed TV app that can be launched from the home screen. */
data class TvApp(
    val packageName: String,
    val label: String,
    /** Scaled copies of the app's banner and icon, made off the main thread when the app list loads. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val launchIntent: Intent,
    /** When the package was last installed or updated; new art comes with a new value. */
    val updatedAt: Long,
) {
    // Intents compare by identity and bitmaps are recreated on a reload; compare by what matters,
    // so an unchanged app list is seen as unchanged and nothing redraws.
    override fun equals(other: Any?): Boolean =
        other is TvApp && other.packageName == packageName && other.label == label &&
            other.updatedAt == updatedAt && other.launchIntent.component == launchIntent.component

    override fun hashCode(): Int = packageName.hashCode()
}

/** One row from the system "Watch Next" table. Plain types only, so ranking logic is unit-testable. */
data class WatchNextItem(
    val id: Long,
    val packageName: String,
    val title: String,
    val subtitle: String?,
    /** Thumbnail first, then poster art. Either may fail to load, so both are kept. */
    val imageUris: List<String>,
    val progress: Float?,
    val lastEngagementMillis: Long,
    val intentUri: String?,
    val type: Int,
    val browsable: Boolean,
    /** The app's short blurb for the show, when it publishes one. */
    val description: String? = null,
)

sealed interface WatchNextResult {
    data class Items(val items: List<WatchNextItem>) : WatchNextResult
    data object NoPermission : WatchNextResult
    data class Failed(val message: String) : WatchNextResult
}

/** Which home screen layout to show. Stored by name, so renaming an entry resets users to Minimal. */
enum class HomeStyle {
    /** Three rows: Continue watching, Recent, Favorites. */
    Minimal,

    /** Apple TV-like: a large top shelf of what you're watching above a grid of apps. */
    Shelf;

    companion object {
        fun parse(raw: String?): HomeStyle = entries.firstOrNull { it.name == raw } ?: Minimal
    }
}

/** What the Top Shelf shows: one app's items when it has any, otherwise everything ("Up Next"). */
data class ShelfContent(val items: List<WatchNextItem>, val fromPackage: String?)

/**
 * Something an app played through a media session, recorded for apps that don't publish
 * Watch Next rows themselves (JioHotstar, Apple TV, YouTube). Plain types only, for unit tests.
 */
data class PlayedItem(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    /** Artwork URL the app gave, then our saved copy of its artwork bitmap. Either may be missing. */
    val imageUris: List<String>,
    val positionMillis: Long,
    val durationMillis: Long,
    val lastPlayedMillis: Long,
)
