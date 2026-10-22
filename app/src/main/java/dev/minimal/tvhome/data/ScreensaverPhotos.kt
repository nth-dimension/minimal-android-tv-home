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
    /** The photo on screen changes every 5 minutes. */
    val SWAP_MS: Long = TimeUnit.MINUTES.toMillis(5)

    /**
     * A new photo is downloaded every 15 minutes; the swaps between cycle the saved ones. Each
     * download costs two Unsplash requests out of 50 an hour, and a write to the TV's flash.
     */
    val FETCH_EVERY_MS: Long = TimeUnit.MINUTES.toMillis(15)

    /** Photos kept on the TV, so the screensaver starts instantly and cycles when offline. */
    const val KEEP = 8

    /** Slightly early, so a swap that lands just before the 15-minute mark still fetches. */
    private val FETCH_SLACK_MS = TimeUnit.SECONDS.toMillis(30)

    /** Whether the next swap should download a new photo rather than reuse a saved one. */
    fun shouldFetch(lastFetchedAt: Long?, now: Long, saved: Int): Boolean =
        saved < 2 || lastFetchedAt == null || now < lastFetchedAt || now - lastFetchedAt >= FETCH_EVERY_MS - FETCH_SLACK_MS

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
    private val prefs = HomePrefs(context)
    private val wallpaper = WallpaperRepository(context, BuildConfig.UNSPLASH_ACCESS_KEY)
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
        pool().firstOrNull() ?: wallpaper.current()

    /** Fetches a new photo in one of the topics picked in Options. Null if none could be fetched. */
    suspend fun fetch(): Wallpaper? = mutex.withLock {
        if (!isConfigured) return@withLock null
        val topics = prefs.wallpaperSettings.first().topics
        withContext(Dispatchers.IO) {
            try {
                val photo = client.fetchRandom(topics, dir, System.currentTimeMillis())
                val (kept, dropped) = ScreensaverRules.add(pool(), photo)
                index.writeText(JSONArray(kept.map(WallpaperRules::encode)).toString())
                dropped.forEach { File(it.file).delete() }
                // Leftovers from interrupted downloads.
                val keep = kept.map { File(it.file).name }.toSet() + index.name
                dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
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
