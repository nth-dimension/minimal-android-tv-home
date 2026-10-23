package dev.minimal.tvhome.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import androidx.core.content.ContextCompat

/**
 * Reads the system "Watch Next" table that TV apps publish their in-progress content to.
 * Rows from other apps are visible when they are searchable (the default) and we hold READ_TV_LISTINGS.
 */
class WatchNextRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, WatchNextRepository.READ_TV_LISTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** False on Fire OS, which has no TV provider: nothing to read, no permission to ask for. */
    fun isSupported(): Boolean = Platform.hasTvProvider(context)

    fun load(): WatchNextResult {
        // No provider: an empty table rather than a permission hint nobody can act on. Recorded
        // playbacks are merged into the empty list, so Continue watching still fills up.
        if (!isSupported()) return WatchNextResult.Items(emptyList())
        if (!hasPermission()) return WatchNextResult.NoPermission
        return try {
            // Every row: Search looks through all of them; the home row is capped when merged.
            WatchNextResult.Items(Ranking.rankWatchNext(queryRaw(), limit = Int.MAX_VALUE, perApp = Int.MAX_VALUE))
        } catch (e: SecurityException) {
            WatchNextResult.NoPermission
        } catch (e: Exception) {
            WatchNextResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Every row we can see, unfiltered. Used by the diagnostics screen. */
    fun queryRaw(): List<WatchNextItem> {
        if (!isSupported()) return emptyList()
        val cursor = context.contentResolver.query(
            TvContract.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null,
        ) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        WatchNextItem(
                            id = c.long("_id") ?: -1L,
                            packageName = c.string("package_name").orEmpty(),
                            title = c.string("title").orEmpty(),
                            subtitle = Ranking.subtitle(
                                episodeTitle = c.string("episode_title"),
                                season = c.string("season_display_number"),
                                episode = c.string("episode_display_number"),
                            ),
                            imageUris = listOfNotNull(c.string("thumbnail_uri"), c.string("poster_art_uri")).distinct(),
                            progress = Ranking.progress(
                                c.long("last_playback_position_millis"),
                                c.long("duration_millis"),
                            ),
                            lastEngagementMillis = c.long("last_engagement_time_utc_millis") ?: 0L,
                            intentUri = c.string("intent_uri"),
                            type = c.long("watch_next_type")?.toInt() ?: -1,
                            browsable = (c.long("browsable") ?: 1L) != 0L,
                            description = c.string("short_description"),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        /** Defined by the system TV provider; not exposed as a public SDK constant. */
        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

        /** Only the columns read below; the table has forty-odd, some of them blobs. */
        private val PROJECTION = arrayOf(
            "_id", "package_name", "title", "episode_title", "season_display_number", "episode_display_number",
            "thumbnail_uri", "poster_art_uri", "last_playback_position_millis", "duration_millis",
            "last_engagement_time_utc_millis", "intent_uri", "watch_next_type", "browsable", "short_description",
        )
    }

    private fun Cursor.string(column: String): String? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getString(i)?.takeIf { it.isNotBlank() }
    }

    private fun Cursor.long(column: String): Long? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }
}
