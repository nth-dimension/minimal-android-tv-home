package dev.minimal.tvhome.data

import android.content.Intent
import android.graphics.drawable.Drawable

/** An installed TV app that can be launched from the home screen. */
data class TvApp(
    val packageName: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable?,
    val launchIntent: Intent,
) {
    // Drawables and Intents compare by identity and are recreated on every load. Without this,
    // every refresh looked like a new app list and redrew every banner on the main thread.
    override fun equals(other: Any?): Boolean =
        other is TvApp && other.packageName == packageName && other.label == label &&
            other.launchIntent.component == launchIntent.component

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
