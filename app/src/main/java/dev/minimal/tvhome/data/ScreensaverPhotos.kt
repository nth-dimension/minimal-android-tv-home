package dev.minimal.tvhome.data

import android.content.Context
import android.util.Log
import dev.minimal.tvhome.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

/** Pure rules for the screensaver's photo pool, free of Android types so they are unit-testable. */
object ScreensaverRules {
    /** A new photo every 5 minutes. */
    val SWAP_MS: Long = TimeUnit.MINUTES.toMillis(5)

    /** Photos kept on the TV, so the screensaver starts instantly and cycles when offline. */
    const val KEEP = 8

    // A little under SWAP_MS, so a screensaver that restarts often can't fetch faster than every
    // few minutes and use up Unsplash's 50 requests an hour.
    private val MIN_FETCH_GAP_MS = SWAP_MS - TimeUnit.SECONDS.toMillis(30)

    fun shouldFetch(lastFetchedAt: Long?, now: Long): Boolean =
        lastFetchedAt == null || now < lastFetchedAt || now - lastFetchedAt >= MIN_FETCH_GAP_MS

    /** How long to wait before fetching, given when the newest saved photo was fetched. */
    fun waitBeforeFetch(lastFetchedAt: Long?, now: Long): Long =
        if (shouldFetch(lastFetchedAt, now)) 0 else (SWAP_MS - (now - lastFetchedAt!!)).coerceIn(0, SWAP_MS)

    /** Newest first, no duplicate photos, at most [keep]. Returns the pool and what fell out of it. */
    fun add(pool: List<Wallpaper>, photo: Wallpaper, keep: Int = KEEP): Pair<List<Wallpaper>, List<Wallpaper>> {
        val all = listOf(photo) + pool.filter { it.id != photo.id }
        return all.take(keep) to all.drop(keep)
    }

    /** The saved photo after [currentId], wrapping round; used when no new photo can be fetched. */
    fun next(pool: List<Wallpaper>, currentId: String?): Wallpaper? {
        if (pool.isEmpty()) return null
        val i = pool.indexOfFirst { it.id == currentId }
        return pool[(i + 1) % pool.size]
    }
}

/** The screensaver's photos on disk: a small pool topped up from Unsplash. */
class ScreensaverPhotos(private val context: Context) {
    private val client = UnsplashClient(context, BuildConfig.UNSPLASH_ACCESS_KEY)
    private val dir: File get() = File(context.filesDir, "screensaver").apply { mkdirs() }
    private val index: File get() = File(dir, "index.json")

    val isConfigured: Boolean get() = client.isConfigured

    fun pool(): List<Wallpaper> = runCatching {
        if (!index.exists()) return emptyList()
        val array = JSONArray(index.readText())
        (0 until array.length()).map { WallpaperRules.decode(array.getString(it)) }.filter { File(it.file).exists() }
    }.getOrDefault(emptyList())

    /** What to show the moment the screensaver starts: the newest saved photo, else the home background. */
    fun firstPhoto(): Wallpaper? =
        pool().firstOrNull() ?: WallpaperRepository(context, BuildConfig.UNSPLASH_ACCESS_KEY).current()

    /** Fetches a new photo in the topic picked in Options. Null if none could be fetched. */
    suspend fun fetch(): Wallpaper? = mutex.withLock {
        if (!isConfigured) return@withLock null
        val topic = HomePrefs(context).wallpaperSettings.first().topic
        withContext(Dispatchers.IO) {
            try {
                val photo = client.fetchRandom(topic, dir, System.currentTimeMillis())
                val (kept, dropped) = ScreensaverRules.add(pool(), photo)
                index.writeText(JSONArray(kept.map(WallpaperRules::encode)).toString())
                dropped.forEach { File(it.file).delete() }
                // Leftovers from interrupted downloads.
                val keep = kept.map { File(it.file).name }.toSet() + index.name
                dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
                Log.i(TAG, "Screensaver photo saved; ${kept.size} on the TV")
                photo
            } catch (e: Exception) {
                Log.w(TAG, "Screensaver photo failed: ${e.message}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "MinimalHome"
        // One fetch at a time across the screensaver and its preview.
        val mutex = Mutex()
    }
}
